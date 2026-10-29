package com.evolearn.kidszone.ui

import android.app.Activity
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.evolearn.kidszone.R

object Ui {
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
