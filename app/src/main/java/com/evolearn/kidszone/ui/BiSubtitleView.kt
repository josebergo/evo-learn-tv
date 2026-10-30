package com.evolearn.kidszone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.evolearn.kidszone.core.BiliApi

/**
 * 双语字幕视图：半透明黑底圆角条（宽度随文本自适应）+ 白字深边；
 * 上行英文大字、下行中文小字。由播放页按播放进度驱动（update）。
 */
class BiSubtitleView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var en: String? = null
    private var zh: String? = null

    private val d = resources.displayMetrics.density

    /** 深边层 + 白字层双层绘制，任何画面上清晰 */
    private fun outlinePaint(sizeSp: Float, strokeDp: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE61A2233.toInt()
        textSize = sizeSp * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = strokeDp * d
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private fun fillPaint(sizeSp: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizeSp * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val enOutline = outlinePaint(26f, 4.5f)
    private val enFill = fillPaint(26f, 0xFFFFFFFF.toInt())
    private val zhOutline = outlinePaint(17f, 3.5f)
    private val zhFill = fillPaint(17f, 0xFFE8E8E8.toInt())

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val bgRect = RectF()

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
        val w = (width * 0.82f).toInt().coerceAtLeast(200)
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
        val gap = 4f * d
        val padH = 30f * d
        val padV = 13f * d
        val inter = if (zOutline != null) gap + 3f * d else 0f

        val textW = maxOf(eOutline.width, zOutline?.width ?: 0)
        val textH = eOutline.height + (zOutline?.height ?: 0) + if (zOutline != null) inter.toInt() else 0
        val bottom = height - paddingBottom - 8f * d
        val top = bottom - textH - 2 * padV

        // 半透明黑底圆角条：宽度随文本自适应
        bgRect.set(
            (width - textW) / 2f - padH, top,
            (width + textW) / 2f + padH, bottom
        )
        canvas.drawRoundRect(bgRect, 16f * d, 16f * d, bgPaint)

        var y = top + padV
        canvas.save()
        canvas.translate((width - eOutline.width) / 2f, y)
        eOutline.draw(canvas)
        eFill.draw(canvas)
        canvas.restore()
        if (zOutline != null && zFill != null) {
            y += eOutline.height + inter
            canvas.save()
            canvas.translate((width - zOutline.width) / 2f, y)
            zOutline.draw(canvas)
            zFill.draw(canvas)
            canvas.restore()
        }
    }
}
