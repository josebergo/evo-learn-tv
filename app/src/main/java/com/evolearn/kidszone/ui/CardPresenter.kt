package com.evolearn.kidszone.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.leanback.widget.Presenter
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.model.Item

/** 家长专区行的伪条目 */
data class MenuCard(
    val title: String,
    val sub: String,
    val action: Int,
    val badge: String
) {
    companion object {
        const val ACTION_SETTINGS = 1
        const val ACTION_RELOAD = 2
    }
}

/** 片单卡片：纯色大字卡片，65寸3米可视；焦点放大+橙色描边 */
class CardPresenter(private val colorRes: Int) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_item, parent, false)
        val base = ContextCompat.getColor(parent.context, colorRes)
        val accent = ContextCompat.getColor(parent.context, R.color.accent)
        view.background = stateBackground(base, accent)
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val view = viewHolder.view
        val badge: TextView = view.findViewById(R.id.card_id)
        val title: TextView = view.findViewById(R.id.card_title)
        val sub: TextView = view.findViewById(R.id.card_sub)
        when (item) {
            is Item -> {
                badge.text = item.id.uppercase()
                title.text = item.t
                sub.text = listOfNotNull(item.tag, item.min?.let { "约${it}分钟" })
                    .joinToString(" · ")
            }
            is MenuCard -> {
                badge.text = item.badge
                title.text = item.title
                sub.text = item.sub
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.animate().cancel()
        viewHolder.view.scaleX = 1f
        viewHolder.view.scaleY = 1f
    }

    private fun stateBackground(baseColor: Int, accent: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(baseColor)
            cornerRadius = 14f
        }
        val focused = GradientDrawable().apply {
            setColor(ColorUtils.blendARGB(baseColor, Color.WHITE, 0.14f))
            cornerRadius = 14f
            setStroke(4, accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }
}
