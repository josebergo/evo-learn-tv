package com.evolearn.kidszone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.evolearn.kidszone.core.BiliApi

/**
 * 双语字幕视图：上行英文（白色大字）、下行中文（浅灰小字）。
 * 由播放页按播放进度驱动（update），时间轴匹配在视图内完成。
 */
class BiSubtitleView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var en: String? = null
    private var zh: String? = null

    private val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 2f, 0xAA000000.toInt())
    }
    private val zhPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6E6E6.toInt()
        textSize = 15f * resources.displayMetrics.scaledDensity
        setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
    }
    private val bgPaint = Paint().apply { color = 0x88000000.toInt() }

    private var enLayout: StaticLayout? = null
    private var zhLayout: StaticLayout? = null

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
        // 二分找覆盖当前时间的行
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
            val d = kotlin.math.abs(l.fromMs - curEn.fromMs)
            if (d < bestDiff) { bestDiff = d; best = l }
        }
        return best?.text
    }

    private fun rebuild() {
        val w = (width * 0.86f).toInt().coerceAtLeast(200)
        enLayout = en?.let {
            StaticLayout.Builder.obtain(it, 0, it.length, enPaint, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(2f, 1f)
                .build()
        }
        zhLayout = zh?.let {
            StaticLayout.Builder.obtain(it, 0, it.length, zhPaint, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        val e = enLayout ?: return
        val z = zhLayout
        val gap = 6f * resources.displayMetrics.density
        val total = e.height + (z?.height?.plus(gap.toInt()) ?: 0)
        val bottom = height - paddingBottom - 8f * resources.displayMetrics.density
        val top = bottom - total
        // 半透明底衬
        canvas.drawRoundRect(
            RectF(paddingLeft + width * 0.04f, top - gap, width * 0.96f - paddingRight, bottom + gap),
            12f, 12f, bgPaint
        )
        canvas.save()
        canvas.translate((width - e.width) / 2f, top.toFloat())
        e.draw(canvas)
        canvas.restore()
        if (z != null) {
            canvas.save()
            canvas.translate((width - z.width) / 2f, top + e.height + gap)
            z.draw(canvas)
            canvas.restore()
        }
    }
}
