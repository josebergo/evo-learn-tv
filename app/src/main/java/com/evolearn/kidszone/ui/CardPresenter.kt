package com.evolearn.kidszone.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.leanback.widget.Presenter
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.model.Item

/** 家长专区/搜索卡的伪条目 */
data class MenuCard(
    val title: String,
    val sub: String,
    val action: Int,
    val badge: String
) {
    companion object {
        const val ACTION_SETTINGS = 1
        const val ACTION_RELOAD = 2
        const val ACTION_SEARCH = 3
    }
}

/** 片单条目定位：搜索结果 / 分类行使用，携带原分类坐标与难度等级，点击直达详情 */
data class CatalogEntry(
    val catIdx: Int,
    val itemIdx: Int,
    val item: Item,
    val catTitle: String,
    val level: Int? = null
)

/** 片单卡片：糖果贴纸风——糖果色底 + 蜡笔粗描边 + 不对称圆角（手绘歪扭感）；
 *  焦点时弹跳放大 + 橙色描边加粗提亮。快乐体标题，65寸3米可视。 */
class CardPresenter(private val colorRes: Int) : Presenter() {

    private var baseColor: Int = 0
    private var crayonColor: Int = 0
    private var accentColor: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_item, parent, false)
        baseColor = ContextCompat.getColor(parent.context, colorRes)
        crayonColor = ContextCompat.getColor(parent.context, R.color.crayon)
        accentColor = ContextCompat.getColor(parent.context, R.color.accent)
        view.background = stickerBackground(view, baseColor)
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(scale).scaleY(scale)
                .setInterpolator(OvershootInterpolator(2.4f))
                .setDuration(220).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val view = viewHolder.view
        val badge: TextView = view.findViewById(R.id.card_id)
        val title: TextView = view.findViewById(R.id.card_title)
        val sub: TextView = view.findViewById(R.id.card_sub)
        title.typeface = Ui.font(view.context)
        when (item) {
            is Item -> {
                view.background = stickerBackground(view, baseColor)
                badge.text = item.id.uppercase()
                title.text = item.t
                sub.text = listOfNotNull(item.tag, item.min?.let { "约${it}分钟" })
                    .joinToString(" · ")
            }
            is CatalogEntry -> {
                // 搜索/分类平铺场景：书脊色跟随原分类，徽标展示难度等级(L1-L5)
                val palette = Ui.cardPalette()
                val color = ContextCompat.getColor(
                    view.context, palette[item.catIdx.mod(palette.size)]
                )
                view.background = stickerBackground(view, color)
                badge.text = item.level?.let { "L$it" } ?: item.item.id.uppercase()
                title.text = item.item.t
                sub.text = listOfNotNull(
                    item.catTitle,
                    item.item.tag,
                    item.item.min?.let { "约${it}分钟" }
                ).joinToString(" · ")
            }
            is MenuCard -> {
                // 搜索入口做成矩形输入框样式（直角、白底、蜡笔描边），区别于贴纸卡片
                view.background = if (item.action == MenuCard.ACTION_SEARCH)
                    searchBoxBackground(view) else stickerBackground(view, baseColor)
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

    /** 贴纸背景：四角圆角各不相同（手绘感），常态蜡笔 3dp 描边，焦点橙 5dp 描边 + 提亮 */
    private fun stickerBackground(v: View, baseColor: Int): StateListDrawable {
        val d = v.resources.displayMetrics.density
        val radii = floatArrayOf(
            30 * d, 30 * d, 24 * d, 24 * d,
            26 * d, 26 * d, 34 * d, 34 * d
        )
        val normal = GradientDrawable().apply {
            setColor(baseColor)
            cornerRadii = radii
            setStroke((3 * d).toInt(), crayonColor)
        }
        val focused = GradientDrawable().apply {
            setColor(ColorUtils.blendARGB(baseColor, Color.WHITE, 0.18f))
            cornerRadii = radii
            setStroke((5 * d).toInt(), accentColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** 搜索框背景：直角矩形（GradientDrawable 默认无圆角），白底蜡笔边，焦点芒果提亮橙边 */
    private fun searchBoxBackground(v: View): StateListDrawable {
        val d = v.resources.displayMetrics.density
        val white = ContextCompat.getColor(v.context, R.color.bg_panel)
        val focusedFill = ContextCompat.getColor(v.context, R.color.accent_dim)
        val normal = GradientDrawable().apply {
            setColor(white)
            setStroke((3 * d).toInt(), crayonColor)
        }
        val focused = GradientDrawable().apply {
            setColor(focusedFill)
            setStroke((5 * d).toInt(), accentColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }
}
