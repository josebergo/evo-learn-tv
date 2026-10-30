package com.evolearn.kidszone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.evolearn.kidszone.core.BiliApi

/**
 * 双语字幕视图（经典美剧风格）：白色粗体大字+深色描边，无底衬无卡片；
 * 上行英文、下行中文小字，由播放页按播放进度驱动（update）。
 * 双层绘制（黑边层+白字层）保证任何画面上都清晰易读。
 */
class BiSubtitleView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var en: String? = null
    private var zh: String? = null

    private val d = resources.displayMetrics.density

    private fun outlinePaint(sizeSp: Float, strokeDp: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC1A2233.toInt()
        textSize = sizeSp * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = strokeDp * d
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private fun fillPaint(sizeSp: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = sizeSp * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val enOutline = outlinePaint(27f, 5f)
    private val enFill = fillPaint(27f)
    private val zhOutline = outlinePaint(18f, 4f)
    private val zhFill = fillPaint(18f)

    private var enOutlineLayout: StaticLayout? = null
    private var enFillLayout: StaticLayout? = null
    private var zhOutlineLayout: StaticLayout? = null
    private var zhFillLayout: StaticLayout? = null

    /** 播放进度驱动：找到当前时间点的英/中字幕行，变化时重绘；
     *  liveEn 为Vosk实时识别的英文行，非空时优先于时间轴英文轨 */
    fun update(
        posMs: Long,
        enLines: List<BiliApi.SubLine>,
        zhLines: List<BiliApi.SubLine>,
        liveEn: String? = null
    ) {
        val e = liveEn?.takeIf { it.isNotBlank() } ?: lineAt(enLines, posMs)
        val z = bestZh(enLines, zhLines, posMs)
        if (e != en || z != zh) {
            en = e
            zh = z
            rebuild()
            invalidate()
        }
    }

    private fun lineAt(lines: List<BiliApi.SubLine>, posMs: Long): String? {
        var lo = 0
        var hi = lines.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val l = lines[mid]
            when {
                posMs < l.fromMs -> hi = mid - 1
                posMs > l.toMs -> lo = mid + 1
                else -> return l.text
            }
        }
        return null
    }

    /** 中文轨与英文行按起点时间对齐（±1.2s 内取最接近的一行） */
    private fun bestZh(
        enLines: List<BiliApi.SubLine>,
        zhLines: List<BiliApi.SubLine>,
        posMs: Long
    ): String? {
        val direct = lineAt(zhLines, posMs)
        if (direct != null) return direct
        val curEn = enLines.firstOrNull { posMs in it.fromMs..it.toMs } ?: return null
        var best: BiliApi.SubLine? = null
        var bestDiff = 1200L
        for (l in zhLines) {
            val diff = kotlin.math.abs(l.fromMs - curEn.fromMs)
            if (diff < bestDiff) { bestDiff = diff; best = l }
        }
        return best?.text
    }

    private fun layoutOf(text: String?, paint: TextPaint, w: Int): StaticLayout? =
        text?.let {
            StaticLayout.Builder.obtain(it, 0, it.length, paint, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(2f, 1f)
                .build()
        }

    private fun rebuild() {
        val w = (width * 0.9f).toInt().coerceAtLeast(200)
        enOutlineLayout = layoutOf(en, enOutline, w)
        enFillLayout = layoutOf(en, enFill, w)
        zhOutlineLayout = layoutOf(zh, zhOutline, w)
        zhFillLayout = layoutOf(zh, zhFill, w)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        val eOutline = enOutlineLayout ?: return
        val eFill = enFillLayout ?: return
        val zOutline = zhOutlineLayout
        val zFill = zhFillLayout
        val gap = 5f * d
        val bottom = height - paddingBottom - 6f * d
        val total = eOutline.height + ((zOutline?.height ?: 0) + if (zOutline != null) gap.toInt() else 0)
        var top = (bottom - total).toFloat()

        canvas.save()
        canvas.translate((width - eOutline.width) / 2f, top)
        eOutline.draw(canvas) // 黑边层
        eFill.draw(canvas)    // 白字层
        canvas.restore()
        if (zOutline != null && zFill != null) {
            top += eOutline.height + gap
            canvas.save()
            canvas.translate((width - zOutline.width) / 2f, top)
            zOutline.draw(canvas)
            zFill.draw(canvas)
            canvas.restore()
        }
    }
}
