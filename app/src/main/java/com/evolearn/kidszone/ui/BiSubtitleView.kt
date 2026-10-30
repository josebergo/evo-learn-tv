package com.evolearn.kidszone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.evolearn.kidszone.core.BiliApi

/**
 * 双语字幕视图（糖果贴纸风）：英文行=奶油贴纸卡（蜡笔描边+不对称手绘圆角），
 * 中文行=小一号的错位便签叠在右下，像手写注释；两张卡随文本宽度自适应，
 * 在任何画面上一眼可辨。由播放页按播放进度驱动（update）。
 */
class BiSubtitleView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var en: String? = null
    private var zh: String? = null

    private val d = resources.displayMetrics.density

    private val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3D4A6B.toInt() // 蜡笔深蓝（奶油底上高对比）
        textSize = 23f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private val zhPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A6A8C.toInt()
        textSize = 15f * resources.displayMetrics.scaledDensity
    }

    // 贴纸卡：奶油底 + 蜡笔描边（英文卡/中文卡同语言，中文卡略暖色区分）
    private val enCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFDF3.toInt() }
    private val enStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = 0xFF3D4A6B.toInt()
    }
    private val zhCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFE9C8.toInt() }
    private val zhStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = 0xFFB0682F.toInt()
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF8A3D.toInt() }

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
            val firstShow = en == null && zh == null && (e != null || z != null)
            en = e
            zh = z
            rebuild()
            if (firstShow) {
                // 新句进场：贴纸轻轻"啪"上来
                scaleX = 0.92f
                scaleY = 0.92f
            }
            animate().scaleX(1f).scaleY(1f).setDuration(140).start()
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

    /** 不对称圆角贴纸路径（手绘歪扭感，与首页卡片同语言） */
    private fun stickerPath(rect: RectF, style: Int): Path {
        val r1 = (if (style == 0) 22f else 16f) * d
        val r2 = (if (style == 0) 16f else 12f) * d
        val r3 = (if (style == 0) 18f else 20f) * d
        val r4 = (if (style == 0) 26f else 14f) * d
        return Path().apply {
            arcTo(RectF(rect.left, rect.top, rect.left + 2 * r1, rect.top + 2 * r1), 180f, 90f, false)
            arcTo(RectF(rect.right - 2 * r2, rect.top, rect.right, rect.top + 2 * r2), 270f, 90f, false)
            arcTo(RectF(rect.right - 2 * r3, rect.bottom - 2 * r3, rect.right, rect.bottom), 0f, 90f, false)
            arcTo(RectF(rect.left, rect.bottom - 2 * r4, rect.left + 2 * r4, rect.bottom), 90f, 90f, false)
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val e = enLayout ?: return
        val z = zhLayout
        val padH = 26f * d
        val padV = 14f * d
        val overlap = if (z != null) 10f * d else 0f // 中文便签叠在英文卡下缘

        val enCardW = e.width + 2 * padH
        val enCardH = e.height + 2 * padV
        val zhCardW = z?.let { it.width + 2 * padH * 0.8f } ?: 0f
        val zhCardH = z?.let { it.height + 2 * padV * 0.7f } ?: 0f

        val bottom = height - paddingBottom - 10f * d
        val enCardTop = bottom - zhCardH + overlap - enCardH
        // 中文便签向右错开一点，像贴上去的手写注释
        val zhShift = if (z != null) 34f * d else 0f

        // 英文贴纸卡
        val enRect = RectF(
            (width - enCardW) / 2f, enCardTop,
            (width + enCardW) / 2f, enCardTop + enCardH
        )
        canvas.save()
        canvas.rotate(-0.7f, enRect.centerX(), enRect.centerY()) // 轻微歪一点更手绘
        canvas.translate(2.5f * d, 3f * d)
        canvas.drawPath(stickerPath(enRect, 0), shadowPaint) // 投影
        canvas.translate(-2.5f * d, -3f * d)
        canvas.drawPath(stickerPath(enRect, 0), enCardPaint)
        canvas.drawPath(stickerPath(enRect, 0), enStrokePaint)
        // 左上"胶带点"装饰
        canvas.drawCircle(enRect.left + 10f * d, enRect.top + 10f * d, 4f * d, dotPaint)
        canvas.translate(enRect.left + padH, enRect.top + padV)
        e.draw(canvas)
        canvas.restore()

        // 中文小便签（错位叠放）
        if (z != null) {
            val zhRect = RectF(
                (width - zhCardW) / 2f + zhShift, enCardTop + enCardH - overlap,
                (width + zhCardW) / 2f + zhShift, enCardTop + enCardH - overlap + zhCardH
            )
            canvas.save()
            canvas.rotate(0.9f, zhRect.centerX(), zhRect.centerY())
            canvas.translate(2f * d, 2.5f * d)
            canvas.drawPath(stickerPath(zhRect, 1), shadowPaint)
            canvas.translate(-2f * d, -2.5f * d)
            canvas.drawPath(stickerPath(zhRect, 1), zhCardPaint)
            canvas.drawPath(stickerPath(zhRect, 1), zhStrokePaint)
            canvas.drawCircle(zhRect.right - 9f * d, zhRect.top + 9f * d, 3.2f * d, dotPaint)
            canvas.translate(zhRect.left + padH * 0.8f, zhRect.top + padV * 0.7f)
            z.draw(canvas)
            canvas.restore()
        }
    }
}
