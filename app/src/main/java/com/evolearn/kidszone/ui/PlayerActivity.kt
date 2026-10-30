package com.evolearn.kidszone.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.BiliApi
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.Prefs
import com.evolearn.kidszone.core.model.Item
import kotlinx.coroutines.launch

/**
 * 播放页：BV号(配置/缓存/搜索) → cid → html5 MP4直链 → ExoPlayer。
 * 播放失败提供重试。
 */
class PlayerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CAT = "cat"
        const val EXTRA_ITEM = "item"
        const val EXTRA_FORCE_SEARCH = "force_search"

        fun start(ctx: Context, catIdx: Int, itemIdx: Int, forceSearch: Boolean) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_CAT, catIdx)
                    .putExtra(EXTRA_ITEM, itemIdx)
                    .putExtra(EXTRA_FORCE_SEARCH, forceSearch)
            )
        }
    }

    private var catIdx = 0
    private var itemIdx = 0
    private var forceSearch = false

    private lateinit var player: ExoPlayer
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorText: TextView

    private var lastBvid: String? = null
    private var endedHandled = false

    /** 当前是否用DASH流起播（用于播放出错时自动降级MP4） */
    private var playingDash = false

    /** DASH播放失败后本次会话内不再尝试DASH */
    private var dashBlocked = false

    // 双语字幕：B站CC轨（中文行，AI字幕需登录）+ Vosk实时识别（英文行，无轨时兜底）
    private lateinit var biSubtitle: BiSubtitleView
    private var enLines: List<BiliApi.SubLine> = emptyList()
    private var zhLines: List<BiliApi.SubLine> = emptyList()
    private var liveEn: String? = null
    private var liveStarted = false
    private val pcmTap = com.evolearn.kidszone.core.PcmTapAudioProcessor()
    private val subtitleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val subtitleTick = object : Runnable {
        override fun run() {
            if (::biSubtitle.isInitialized && player.isPlaying) {
                biSubtitle.update(player.currentPosition, enLines, zhLines, liveEn)
            }
            subtitleHandler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_player)
        Ui.applyFont(findViewById(android.R.id.content))

        catIdx = intent.getIntExtra(EXTRA_CAT, 0)
        itemIdx = intent.getIntExtra(EXTRA_ITEM, 0)
        forceSearch = intent.getBooleanExtra(EXTRA_FORCE_SEARCH, false)

        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingText = findViewById(R.id.loading_text)
        errorOverlay = findViewById(R.id.error_overlay)
        errorText = findViewById(R.id.error_text)
        biSubtitle = findViewById(R.id.bi_subtitle)
        subtitleHandler.post(subtitleTick)

        // 音频旁路：子类化RenderersFactory把PCM复制器插进AudioSink，供Vosk识别
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackTimestampPassthrough: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(pcmTap))
                    .build()
        }
        player = ExoPlayer.Builder(this, renderersFactory).build()
        findViewById<PlayerView>(R.id.player_view).player = player
        player.addListener(playerListener)

        findViewById<Button>(R.id.btn_retry).setOnClickListener {
            errorOverlay.visibility = View.GONE
            resolveAndPlay()
        }

        resolveAndPlay()
    }

    /** announceTitle：自动连播切换时传入下一部标题，加载页显示"即将播放：xxx" */
    private fun resolveAndPlay(announceTitle: String? = null) {
        val cfg = ConfigRepo.current
        val item = cfg?.let { ConfigRepo.itemAt(it, catIdx, itemIdx) }
        if (cfg == null || item == null) {
            finish()
            return
        }
        endedHandled = false
        loadingOverlay.visibility = View.VISIBLE
        loadingText.text =
            if (announceTitle != null) getString(R.string.player_next, announceTitle)
            else getString(R.string.player_loading)

        lifecycleScope.launch {
            try {
                val bvid = resolveBvid(item)
                    ?: throw IllegalStateException(if (Prefs.isLoggedIn()) "未找到视频" else "未登录且未找到视频")
                lastBvid = bvid

                val pages = BiliApi.pagelist(bvid)
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("获取分P失败（可能已下架）")
                val epIdx = ((item.ep ?: 1) - 1).coerceIn(0, pages.size - 1)
                val cid = pages[epIdx].get("cid")?.asLong
                    ?: throw IllegalStateException("分P信息异常")

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(BiliApi.UA)
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to "https://www.bilibili.com/",
                            "Cookie" to Prefs.cookieString
                        )
                    )
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)

                // 已登录：优先DASH音视频分离流（可达1080P高清）；游客或DASH不可用：HTML5 MP4直链
                val dash = if (Prefs.isLoggedIn() && !dashBlocked) BiliApi.playDash(bvid, cid) else null
                val source: MediaSource
                if (dash != null) {
                    playingDash = true
                    val video = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(dash.videoUrl))
                    val audio = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(dash.audioUrl))
                    source = MergingMediaSource(video, audio)
                    Ui.toast(this@PlayerActivity, getString(R.string.player_quality_info, BiliApi.qnLabel(dash.qn)))
                } else {
                    playingDash = false
                    val qn = cfg.play?.defaultQn ?: 64
                    val url = BiliApi.playUrl(bvid, cid, qn)
                        ?: throw IllegalStateException("获取播放地址失败，请先在家长设置中扫码登录B站")
                    source = DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(url))
                }

                player.setMediaSource(source)
                player.prepare()
                player.playWhenReady = true
                loadingOverlay.visibility = View.GONE

                // 异步拉CC字幕（不阻塞起播）；中文轨(AI)需登录
                enLines = emptyList()
                zhLines = emptyList()
                liveEn = null
                liveStarted = false
                pcmTap.onPcm = null
                lifecycleScope.launch {
                    val subs = BiliApi.subtitles(bvid, cid)
                    if (subs != null) {
                        enLines = subs.first
                        zhLines = subs.second
                    }
                    if (enLines.isEmpty() && zhLines.isEmpty()) {
                        // 完全没有英中字幕轨且家长未关闭自动字幕：才启用Vosk实时识别英文行（有轨一律不跑识别）
                        if (Prefs.liveSubtitleEnabled) startLiveEnglish()
                        maybeDemoSubtitle()
                    }
                }
            } catch (t: Throwable) {
                loadingOverlay.visibility = View.GONE
                errorText.text = "${getString(R.string.player_error_title)}\n${t.message ?: ""}"
                errorOverlay.visibility = View.VISIBLE
            }
        }
    }

    /** Vosk实时英文识别：模型首次解压约1-3s，就绪后自动开始；识别文本实时上屏英文行 */
    private fun startLiveEnglish() {
        if (liveStarted) return
        liveStarted = true
        com.evolearn.kidszone.core.VoskEngine.onText = { text, _ ->
            liveEn = text.ifBlank { null }
        }
        pcmTap.onPcm = { bytes, rate, ch ->
            com.evolearn.kidszone.core.VoskEngine.feed(bytes, rate, ch)
        }
        com.evolearn.kidszone.core.VoskEngine.ensureModel(this) {
            com.evolearn.kidszone.core.VoskEngine.start()
        }
    }

    /** 字幕样式演示（隐藏调试入口，adb 启动时带 demo_subtitle 才生效）：
     *  无真实字幕轨时注入英中对照演示句，验证"英文上行/中文小字下行"渲染。
     */
    private fun maybeDemoSubtitle() {
        if (!intent.getBooleanExtra("demo_subtitle", false)) return
        val lines = listOf(
            "Peppa loves jumping in muddy puddles." to "佩奇最喜欢在泥坑里跳来跳去。",
            "If you jump in muddy puddles, you must wear your boots." to "如果你要跳泥坑，一定要穿上小靴子。",
            "George, let's find another puddle!" to "乔治，我们再去找一个泥坑吧！"
        )
        val en = ArrayList<BiliApi.SubLine>()
        val zh = ArrayList<BiliApi.SubLine>()
        for (round in 0 until 200) {
            val base = round * 16_000L + 4_000
            lines.forEachIndexed { i, (e, z) ->
                val from = base + i * 5_000L
                en.add(BiliApi.SubLine(from, from + 4_600, e))
                zh.add(BiliApi.SubLine(from + 200, from + 4_600, z))
            }
        }
        enLines = en
        zhLines = zh
    }

    /** 优先级：强制搜索 > 配置BV号 > 本地解析缓存 > 关键词搜索 */
    private suspend fun resolveBvid(item: Item): String? {
        if (!forceSearch) {
            item.bvid?.let { return it }
            Prefs.cachedBvid(item.id)?.let { return it }
        }
        val kw = item.kw ?: return null
        loadingText.text = getString(R.string.player_resolving_search)
        val found = BiliApi.searchFirst(kw)
        if (found != null) Prefs.saveBvid(item.id, found)
        return found
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (playingDash && !dashBlocked) {
                // DASH流起播失败（部分 CDN/盒子兼容问题）：自动降级MP4直链重试
                dashBlocked = true
                errorOverlay.visibility = View.GONE
                loadingOverlay.visibility = View.VISIBLE
                loadingText.text = getString(R.string.player_fallback_mp4)
                resolveAndPlay()
                return
            }
            loadingOverlay.visibility = View.GONE
            errorText.text = "${getString(R.string.player_error_title)}\n${error.errorCodeName}"
            errorOverlay.visibility = View.VISIBLE
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && !endedHandled) {
                endedHandled = true
                maybePlayNext()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // seek后音频上下文断裂，重置识别器避免字幕串行
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                com.evolearn.kidszone.core.VoskEngine.reset()
            }
        }
    }

    private fun maybePlayNext() {
        val cfg = ConfigRepo.current ?: return
        val autoplay = cfg.play?.autoplayNext ?: true
        if (!autoplay) return
        val next = ConfigRepo.nextOf(cfg, catIdx, itemIdx)
        if (next == null) {
            Ui.toast(this, getString(R.string.player_week_done), long = true)
            finish()
            return
        }
        val (c, i) = next
        val nextItem = ConfigRepo.itemAt(cfg, c, i)
        nextItem?.let { Ui.toast(this, getString(R.string.player_next, it.t)) }
        catIdx = c
        itemIdx = i
        resolveAndPlay(nextItem?.t)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        subtitleHandler.removeCallbacks(subtitleTick)
        com.evolearn.kidszone.core.VoskEngine.stop()
        player.removeListener(playerListener)
        player.release()
    }
}
