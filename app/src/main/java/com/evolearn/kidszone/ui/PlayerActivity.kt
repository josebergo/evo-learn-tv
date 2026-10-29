package com.evolearn.kidszone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.BiliApi
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.Prefs
import com.evolearn.kidszone.core.model.Item
import kotlinx.coroutines.launch

/**
 * 播放页：BV号(配置/缓存/搜索) → cid → html5 MP4直链 → ExoPlayer。
 * 播放失败提供重试与"去B站TV版"降级（家长PIN确认）。
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_player)

        catIdx = intent.getIntExtra(EXTRA_CAT, 0)
        itemIdx = intent.getIntExtra(EXTRA_ITEM, 0)
        forceSearch = intent.getBooleanExtra(EXTRA_FORCE_SEARCH, false)

        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingText = findViewById(R.id.loading_text)
        errorOverlay = findViewById(R.id.error_overlay)
        errorText = findViewById(R.id.error_text)

        player = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.player_view).player = player
        player.addListener(playerListener)

        findViewById<Button>(R.id.btn_retry).setOnClickListener {
            errorOverlay.visibility = View.GONE
            resolveAndPlay()
        }
        findViewById<Button>(R.id.btn_open_bili).setOnClickListener {
            PinDialog.show(supportFragmentManager, getString(R.string.player_open_bili)) {
                val bvid = lastBvid
                if (bvid.isNullOrBlank()) {
                    Toast.makeText(this, R.string.need_login_play_fail, Toast.LENGTH_LONG).show()
                } else {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/$bvid"))
                        )
                    }
                }
            }
        }

        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        val cfg = ConfigRepo.current
        val item = cfg?.let { ConfigRepo.itemAt(it, catIdx, itemIdx) }
        if (cfg == null || item == null) {
            finish()
            return
        }
        endedHandled = false
        loadingOverlay.visibility = View.VISIBLE
        loadingText.text = getString(R.string.player_loading)

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

                val qn = cfg.play?.defaultQn ?: 64
                val url = BiliApi.playUrl(bvid, cid, qn)
                    ?: throw IllegalStateException("获取播放地址失败，请先在家长设置中扫码登录B站")

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

                val source = DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))

                player.setMediaSource(source)
                player.prepare()
                player.playWhenReady = true
                loadingOverlay.visibility = View.GONE
            } catch (t: Throwable) {
                loadingOverlay.visibility = View.GONE
                errorText.text = "${getString(R.string.player_error_title)}\n${t.message ?: ""}"
                errorOverlay.visibility = View.VISIBLE
            }
        }
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
    }

    private fun maybePlayNext() {
        val cfg = ConfigRepo.current ?: return
        val autoplay = cfg.play?.autoplayNext ?: true
        if (!autoplay) return
        val next = ConfigRepo.nextOf(cfg, catIdx, itemIdx)
        if (next == null) {
            Toast.makeText(this, R.string.player_week_done, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val (c, i) = next
        val nextItem = ConfigRepo.itemAt(cfg, c, i)
        nextItem?.let { Toast.makeText(this, getString(R.string.player_next, it.t), Toast.LENGTH_SHORT).show() }
        catIdx = c
        itemIdx = i
        resolveAndPlay()
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.removeListener(playerListener)
        player.release()
    }
}
