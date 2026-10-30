package com.evolearn.kidszone.core

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.google.gson.JsonParser
import org.vosk.Recognizer
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * 离线英文实时识别（Vosk）：给没有英文字幕轨的视频实时生成英文字幕行。
 * 模型（assets/model-en-us.zip，small-en-us 41MB）首次使用时解压到 filesDir。
 * 识别在单独线程；输入为播放器音频 PCM（任意采样率/声道，内部重采样16k mono）。
 */
object VoskEngine {

    private val io = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val resampler = PcmResampler16k()

    /** 主线程回调：(文本, 是否为未定稿的partial)；文本为空表示当前静默 */
    @Volatile
    var onText: ((String, Boolean) -> Unit)? = null

    @Volatile
    var running = false
        private set

    /** 首次解压+加载模型（耗时1-3s，后台线程），完成后主线程回调 */
    fun ensureModel(ctx: Context, onReady: () -> Unit) {
        if (model != null) {
            main.post(onReady)
            return
        }
        io.execute {
            runCatching {
                val dir = File(ctx.filesDir, "vosk-model")
                val mark = File(dir, ".ready")
                if (!mark.exists()) {
                    ctx.assets.open("model-en-us.zip").use { input ->
                        ZipInputStream(input.buffered()).use { zip ->
                            var entry = zip.nextEntry
                            val buf = ByteArray(1 shl 16)
                            while (entry != null) {
                                // zip顶层目录(vosk-model-small-en-us-0.15)剥掉，直接解到dir下
                                val out = File(dir, entry.name.substringAfter('/'))
                                if (entry.isDirectory) out.mkdirs()
                                else {
                                    out.parentFile?.mkdirs()
                                    FileOutputStream(out).use { fos ->
                                        while (true) {
                                            val n = zip.read(buf)
                                            if (n < 0) break
                                            fos.write(buf, 0, n)
                                        }
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    mark.createNewFile()
                }
                Model(dir.absolutePath).also { model = it }
            }
            main.post(onReady)
        }
    }

    /** 开始识别（重复调用会先停掉旧的） */
    fun start() {
        io.execute {
            val m = model ?: return@execute
            stopLocked()
            recognizer = Recognizer(m, 16000.0f)
            running = true
        }
    }

    /** 喂入播放器PCM（任意采样率/声道，16bit小端） */
    fun feed(pcm: ByteArray, sampleRate: Int, channels: Int) {
        if (!running) return
        io.execute {
            val rec = recognizer ?: return@execute
            val mono16k = resampler.process(pcm, sampleRate, channels)
            if (mono16k.isEmpty()) return@execute
            if (rec.acceptWaveForm(mono16k, mono16k.size)) {
                val text = textOf(rec.result, "text")
                if (text.isNotBlank()) main.post { onText?.invoke(text, false) }
            } else {
                val text = textOf(rec.partialResult, "partial")
                main.post { onText?.invoke(text, true) }
            }
        }
    }

    /** seek/切集后重置识别上下文 */
    fun reset() {
        io.execute { recognizer?.reset() }
    }

    fun stop() {
        io.execute { stopLocked(); running = false }
    }

    private fun stopLocked() {
        recognizer?.close()
        recognizer = null
    }

    private fun textOf(json: String, field: String): String = runCatching {
        JsonParser.parseString(json).asJsonObject
            .get(field)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }.getOrDefault("")
}

/** 任意采样率/声道 PCM16 → 16kHz mono PCM16（线性插值，跨块保相位） */
class PcmResampler16k {

    private var lastMono = 0.0
    private var frac = 0.0 // 下一个输出点相对块首的采样位置

    fun process(bytes: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        if (bytes.size < 2 * channels || channels <= 0) return ByteArray(0)
        val n = bytes.size / 2 / channels
        if (n == 0) return ByteArray(0)
        val mono = DoubleArray(n)
        val sh = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until n) {
            var acc = 0.0
            for (c in 0 until channels) acc += sh.get(i * channels + c).toDouble()
            mono[i] = acc / channels
        }
        val step = sampleRate / 16000.0
        if (step <= 0) return ByteArray(0)
        val out = ArrayList<Short>((n / step).toInt() + 4)
        var t = frac
        while (t < n.toDouble()) {
            val i = t.toInt()
            val v = if (i == 0) {
                lastMono + (mono[0] - lastMono) * t
            } else {
                mono[i - 1] + (mono[i] - mono[i - 1]) * (t - i)
            }
            out.add(v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            t += step
        }
        frac = t - n
        lastMono = mono[n - 1]
        val result = ByteArray(out.size * 2)
        for (i in out.indices) {
            result[i * 2] = (out[i].toInt() and 0xFF).toByte()
            result[i * 2 + 1] = (out[i].toInt() shr 8 and 0xFF).toByte()
        }
        return result
    }
}

/** 播放器音频旁路：插入AudioSink处理链复制一份PCM用于识别，不影响播放 */
@OptIn(UnstableApi::class)
class PcmTapAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var onPcm: ((ByteArray, Int, Int) -> Unit)? = null // (bytes, sampleRate, channels)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        inputAudioFormat // passthrough

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val listener = onPcm
        if (listener != null) {
            val pos = inputBuffer.position()
            val copy = ByteArray(remaining)
            inputBuffer.get(copy)
            inputBuffer.position(pos)
            listener.invoke(copy, inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        }
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
