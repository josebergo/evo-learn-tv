package com.evolearn.kidszone.ui

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.model.KidsConfig
import kotlinx.coroutines.launch

/**
 * 固定双栏主界面：左侧分类栏常驻（含底部家长工具），右侧顶部固定搜索条 + 当前分类的片源网格。
 * 浏览分类即过滤：聚焦/点击左侧分类，右侧网格只显示该分类的影片；"全部影片"显示整个白名单。
 * 网格用标准 RecyclerView（3列），卡片边缘与搜索条严格对齐；全部内容来自配置文件。
 */
class MainFragment : Fragment() {

    /** 左侧导航项：一个分类 + 它的片源 */
    private class NavEntry(val title: String, val entries: List<CatalogEntry>)

    private lateinit var navList: RecyclerView
    private lateinit var contentGrid: RecyclerView
    private lateinit var searchBar: View
    private lateinit var navSettings: View
    private lateinit var navReload: View
    private val cardPresenter = CardPresenter(Ui.cardPalette()[0])
    private val gridAdapter = GridAdapter()
    private var navEntries: List<NavEntry> = emptyList()
    private var selectedNav = 0
    private var builtTick = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        navList = view.findViewById(R.id.nav_list)
        contentGrid = view.findViewById(R.id.content_grid)
        searchBar = view.findViewById(R.id.search_bar)
        navSettings = view.findViewById(R.id.nav_settings)
        navReload = view.findViewById(R.id.nav_reload)

        searchBar.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        Ui.bounce(searchBar)
        navSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        navReload.setOnClickListener {
            activity?.let { Ui.toast(it, it.getString(R.string.reload_loading)) }
            loadAndBuild(force = true)
        }
        listOf(navSettings, navReload).forEach { it.background = navBackground(it, isCurrent = false) }

        val gap = (10 * resources.displayMetrics.density).toInt()
        contentGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        contentGrid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                outRect.set(gap / 2, gap / 2, gap / 2, gap / 2)
            }
        })
        contentGrid.adapter = gridAdapter

        navList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        navList.adapter = NavAdapter()

        if (ConfigRepo.current != null) {
            build(ConfigRepo.current!!)
        } else {
            loadAndBuild()
        }

        // 手绘体注入：布局上屏后遍历设置；片单加载较慢时再兜底一轮（幂等）
        Ui.applyFont(view)
        view.postDelayed({ Ui.applyFont(view) }, 800)
    }

    override fun onResume() {
        super.onResume()
        // 设置页刷新过片单（或换了配置地址）后回到首页自动重建
        val tick = ConfigRepo.reloadTick
        if (ConfigRepo.current != null && tick != builtTick) {
            build(ConfigRepo.current!!)
        }
    }

    private fun loadAndBuild(force: Boolean = false) {
        val act: FragmentActivity = activity ?: return
        lifecycleScope.launch {
            val cfg = ConfigRepo.load(act, force)
            if (cfg.categories.isEmpty()) {
                Ui.toast(act, act.getString(R.string.reload_fail), long = true)
            }
            build(cfg)
        }
    }

    private fun build(cfg: KidsConfig) {
        builtTick = ConfigRepo.reloadTick

        // 全条目展开（带难度等级徽标）；v1旧配置无level时按theme归入 英语/纪录片
        val isV2 = cfg.categories.any { it.level != null || it.items.any { i -> i.lv != null } }
        val buckets = LinkedHashMap<String, MutableList<CatalogEntry>>()
        val all = ArrayList<CatalogEntry>(ConfigRepo.totalItems(cfg))
        cfg.categories.forEachIndexed { ci, cat ->
            val bucketTitle = when {
                isV2 -> cat.title
                cat.theme.orEmpty().contains("纪录片") -> getString(R.string.category_documentary)
                else -> getString(R.string.category_english)
            }
            val bucket = buckets.getOrPut(bucketTitle) { mutableListOf() }
            cat.items.forEachIndexed { ii, item ->
                val e = CatalogEntry(ci, ii, item, cat.title, item.lv ?: cat.level)
                all.add(e)
                bucket.add(e)
            }
        }

        // 等级从易到难排序展示；v1兜底为 英语/纪录片
        val ordered = if (isV2) buckets else {
            val docu = buckets[getString(R.string.category_documentary)] ?: mutableListOf()
            val english = buckets[getString(R.string.category_english)] ?: mutableListOf()
            linkedMapOf(getString(R.string.category_english) to english, getString(R.string.category_documentary) to docu)
        }

        val list = ArrayList<NavEntry>()
        if (all.isNotEmpty()) list.add(NavEntry(getString(R.string.all_row_title), all))
        ordered.entries.forEach { (title, entries) ->
            if (entries.isNotEmpty()) list.add(NavEntry(title, entries))
        }
        navEntries = list
        if (selectedNav >= list.size) selectedNav = 0
        navList.adapter?.notifyDataSetChanged()
        showCategory(selectedNav)
    }

    /** 切换右侧网格到指定分类并高亮导航项（post出当前布局 pass，避免在焦点回调里同步notify崩溃） */
    private fun showCategory(index: Int) {
        val entry = navEntries.getOrNull(index) ?: return
        selectedNav = index
        navList.post { gridAdapter.submit(entry.entries) }
        (navList.adapter as? NavAdapter)?.setCurrent(index)
    }

    /** 分类栏项背景：常态白底蜡笔边；焦点芒果底橙边；当前分类橙边 */
    private fun navBackground(v: View, isCurrent: Boolean): StateListDrawable {
        val d = v.resources.displayMetrics.density
        val white = ContextCompat.getColor(v.context, R.color.bg_panel)
        val mango = ContextCompat.getColor(v.context, R.color.card_c1)
        val crayon = ContextCompat.getColor(v.context, R.color.crayon)
        val accent = ContextCompat.getColor(v.context, R.color.accent)
        val normal = GradientDrawable().apply {
            setColor(white)
            setStroke(((if (isCurrent) 4 else 2) * d).toInt(), if (isCurrent) accent else crayon)
        }
        val focused = GradientDrawable().apply {
            setColor(mango)
            setStroke((4 * d).toInt(), accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** 右侧片源网格：卡片由 CardPresenter 渲染（书脊色按原分类，徽标难度等级） */
    private inner class GridAdapter : RecyclerView.Adapter<GridHolder>() {

        private var entries: List<CatalogEntry> = emptyList()

        fun submit(list: List<CatalogEntry>) {
            entries = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridHolder =
            GridHolder(cardPresenter.onCreateViewHolder(parent))

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: GridHolder, position: Int) {
            val entry = entries[position]
            cardPresenter.onBindViewHolder(holder.presenterHolder, entry)
            holder.itemView.setOnClickListener {
                DetailActivity.start(requireActivity(), entry.catIdx, entry.itemIdx)
            }
        }
    }

    /** 把 leanback Presenter.ViewHolder 包成 RecyclerView.ViewHolder */
    private class GridHolder(val presenterHolder: Presenter.ViewHolder) :
        RecyclerView.ViewHolder(presenterHolder.view)

    /** 左侧分类栏：聚焦即切换右侧网格；OK/点击跳入网格 */
    private inner class NavAdapter : RecyclerView.Adapter<NavAdapter.Holder>() {

        private var current = -1

        fun setCurrent(index: Int) {
            if (index == current || navEntries.isEmpty()) return
            val old = current
            current = index
            if (old >= 0) notifyItemChanged(old)
            notifyItemChanged(index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.nav_item, parent, false)
            return Holder(v)
        }

        override fun getItemCount(): Int = navEntries.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tv = holder.itemView as TextView
            tv.text = navEntries[position].title
            tv.background = navBackground(tv, position == current)
            tv.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showCategory(position)
            }
            tv.setOnClickListener {
                // 遥控器聚焦时已切换；鼠标/触摸点击不走焦点，这里兜底再切一次
                showCategory(position)
                (contentGrid.getChildAt(0) ?: contentGrid).requestFocus()
            }
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
