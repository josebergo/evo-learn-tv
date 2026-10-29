package com.evolearn.kidszone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.Prefs

class DetailActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CAT = "cat"
        const val EXTRA_ITEM = "item"

        fun start(ctx: Context, catIdx: Int, itemIdx: Int) {
            ctx.startActivity(
                Intent(ctx, DetailActivity::class.java)
                    .putExtra(EXTRA_CAT, catIdx)
                    .putExtra(EXTRA_ITEM, itemIdx)
            )
        }
    }

    private var catIdx = 0
    private var itemIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_detail)

        catIdx = intent.getIntExtra(EXTRA_CAT, 0)
        itemIdx = intent.getIntExtra(EXTRA_ITEM, 0)

        val cfg = ConfigRepo.current
        val cat = cfg?.let { ConfigRepo.categoryAt(it, catIdx) }
        val item = cfg?.let { ConfigRepo.itemAt(it, catIdx, itemIdx) }
        if (cfg == null || cat == null || item == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.detail_badge).text = "${cat.title} · ${item.id.uppercase()}"
        findViewById<TextView>(R.id.detail_title).text = item.t

        val meta = listOfNotNull(
            cat.en,
            cat.theme,
            item.tag,
            item.min?.let { "约${it}分钟" },
            "选集 P${item.ep ?: 1}"
        ).joinToString("  ·  ")
        findViewById<TextView>(R.id.detail_meta).text = meta
        findViewById<TextView>(R.id.detail_notice).text = cfg.server?.notice ?: ""

        findViewById<Button>(R.id.btn_play).setOnClickListener {
            PlayerActivity.start(this, catIdx, itemIdx, forceSearch = false)
        }
        findViewById<Button>(R.id.btn_re_resolve).setOnClickListener {
            PlayerActivity.start(this, catIdx, itemIdx, forceSearch = true)
        }
        findViewById<Button>(R.id.btn_open_bili).setOnClickListener {
            PinDialog.show(supportFragmentManager, getString(R.string.detail_open_bili)) {
                openInBiliApp(item.bvid ?: Prefs.cachedBvid(item.id))
            }
        }
    }

    private fun openInBiliApp(bvid: String?) {
        if (bvid.isNullOrBlank()) {
            Toast.makeText(this, R.string.need_login_play_fail, Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/$bvid")))
        }
    }
}
