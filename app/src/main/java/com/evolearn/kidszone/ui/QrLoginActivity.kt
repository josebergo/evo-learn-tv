package com.evolearn.kidszone.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.BiliApi
import com.evolearn.kidszone.core.Prefs
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 大屏展示B站登录二维码，手机App扫码确认后自动完成登录 */
class QrLoginActivity : FragmentActivity() {

    companion object {
        fun intent(ctx: Context) = Intent(ctx, QrLoginActivity::class.java)
    }

    private lateinit var qrImage: ImageView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_qrlogin)
        Ui.applyFont(findViewById(android.R.id.content))
        qrImage = findViewById(R.id.qr_image)
        statusText = findViewById(R.id.qr_status)

        startLoginLoop()
    }

    /** 自动循环：生成二维码 → 轮询(2.5s/次, 180s过期) → 过期自动重新生成 */
    private fun startLoginLoop() {
        lifecycleScope.launch {
            while (!isFinishing) {
                val init = BiliApi.qrGenerate()
                if (init == null) {
                    statusText.text = "网络错误，正在重试…"
                    delay(3_000)
                    continue
                }
                qrImage.setImageBitmap(qrBitmap(init.url, 512))
                statusText.text = getString(R.string.qr_waiting)

                val deadline = System.currentTimeMillis() + 180_000
                var done = false
                while (!done && System.currentTimeMillis() < deadline && !isFinishing) {
                    delay(2_500)
                    val r = BiliApi.qrPoll(init.qrcodeKey) ?: continue
                    when (r.code) {
                        0 -> {
                            Prefs.mergeCookies(r.cookies)
                            val nav = BiliApi.nav()
                            if (nav != null) {
                                Prefs.loginName = nav.first
                                Prefs.loginMid = nav.second
                            }
                            statusText.text = getString(R.string.qr_success, Prefs.loginName.ifBlank { "已登录" })
                            done = true
                            delay(2_000)
                            finish()
                        }
                        86090 -> statusText.text = getString(R.string.qr_scanned)
                        860 -> { // 过期：外层循环重新生成
                            statusText.text = getString(R.string.qr_expired)
                            done = true
                            delay(2_000)
                        }
                        else -> Unit // 861 继续等待
                    }
                }
            }
        }
    }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
