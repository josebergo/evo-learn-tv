package com.evolearn.kidszone.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.evolearn.kidszone.R

object Ui {

    private var kuaile: Typeface? = null

    /** 站酷快乐体（assets/fonts，OFL 免费商用）：手绘绘本风的标题/按钮字体 */
    fun font(context: android.content.Context): Typeface =
        kuaile ?: Typeface.createFromAsset(context.assets, "fonts/zcool_kuaile.ttf")
            .also { kuaile = it }

    /** 递归给整棵树的 TextView 换手绘体；Button 等可聚焦控件加焦点弹跳 */
    fun applyFont(root: View) {
        val f = font(root.context)
        applyFontRec(root, f)
    }

    private fun applyFontRec(v: View, f: Typeface) {
        if (v is TextView) v.typeface = f
        if (v is android.widget.Button) bounce(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFontRec(v.getChildAt(i), f)
    }

    /** 焦点弹跳：放大 1.06 带 overshoot 回弹，像贴纸被按起来 */
    fun bounce(v: View) {
        v.setOnFocusChangeListener { _, hasFocus ->
            val s = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(s).scaleY(s)
                .setInterpolator(OvershootInterpolator(2.2f))
                .setDuration(220).start()
        }
    }

    /** Holo 深色 AlertDialog 改造成贴纸白卡（须在 show() 之后调用） */
    fun styleDialog(dialog: android.app.AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_panel_card)
        val crayon = dialog.context.getColor(R.color.crayon)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(crayon)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(crayon)
        dialog.findViewById<View>(android.R.id.content)?.let { applyFont(it) }
    }

    /** 电视全屏沉浸模式，隐藏系统栏，孩子无法下拉看到通知。
     *  可在 setContentView 之前调用：先触发 DecorView 创建，再在布局完成后执行 insets 控制。 */
    fun hideBars(activity: Activity) {
        val window = activity.window
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val decor = window.decorView // 确保 DecorView 已创建
            decor.post {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    fun cardPalette(): IntArray = intArrayOf(
        R.color.card_c0, R.color.card_c1, R.color.card_c2, R.color.card_c3, R.color.card_c4
    )
}
