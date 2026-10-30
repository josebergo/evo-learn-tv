package com.evolearn.kidszone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 描边文字：先画深色粗描边、再画填充色，两遍叠加。
 * 用于片源卡片的标题/副标题——封面图做背景后颜色不可控，
 * 白字 + 蜡笔深藏蓝描边保证在任何画面上都清晰可读（3米外看电视）。
 */
class StrokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeColor: Int = Color.TRANSPARENT
    private var strokeWidthPx: Float = 0f
    private var fillColor: Int = currentTextColor

    /** 开启描边（dp），描边色随填充色之外的对比色传入 */
    fun setStroke(color: Int, widthDp: Float) {
        strokeColor = color
        strokeWidthPx = widthDp * resources.displayMetrics.density
        invalidate()
    }

    /** 关闭描边（无封面回退纯色底时用回纯文字色） */
    fun clearStroke() {
        strokeWidthPx = 0f
        invalidate()
    }

    override fun setTextColor(color: Int) {
        fillColor = color
        super.setTextColor(color)
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeWidthPx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = strokeWidthPx
            super.setTextColor(strokeColor)
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            super.setTextColor(fillColor)
        }
        super.onDraw(canvas)
    }
}
