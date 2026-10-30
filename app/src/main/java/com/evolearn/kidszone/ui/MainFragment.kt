package com.evolearn.kidszone.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.model.KidsConfig
import kotlinx.coroutines.launch

/**
 * 主界面：左侧=分类（全部影片/L1启蒙~L5挑战/纪录片/家长专区），右上角=唯一搜索入口，右侧=片单大卡片。
 * 分类按难度从易到难排序（v2配置的等级分类）；全部内容来自配置文件——搜索也只在白名单内查找。
 */
class MainFragment : BrowseSupportFragment() {

    private var builtTick = -1

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.browse_title)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_app)
        setHeadersState(HEADERS_ENABLED)
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.bg_panel)
        // 右上角唯一的搜索入口（Leanback 自带 search orb）；左侧菜单仅保留分类
        setSearchAffordanceColors(
            SearchOrbView.Colors(
                ContextCompat.getColor(requireContext(), R.color.accent),
                ContextCompat.getColor(requireContext(), R.color.card_c1),
                ContextCompat.getColor(requireContext(), R.color.cloud_white)
            )
        )
        setOnSearchClickedListener {
            activity?.startActivity(Intent(activity, SearchActivity::class.java))
        }
        setOnItemViewClickedListener(ItemViewClickedListener())

        if (ConfigRepo.current != null) {
            buildRows(ConfigRepo.current!!)
        } else {
            title = getString(R.string.player_loading)
            loadAndBuild()
        }

        // 手绘体注入：rows/headers 是 Leanback 内部 presenter 创建的，布局上屏后遍历设置；
        // 片单加载较慢时 800ms 后再兜底一轮（幂等，重复调用无害）
        val root = requireView()
        root.post { Ui.applyFont(root) }
        root.postDelayed({ Ui.applyFont(root) }, 800)
    }

    override fun onResume() {
        super.onResume()
        // 设置页刷新过片单（或换了配置地址）后回到首页自动重建
        val tick = ConfigRepo.reloadTick
        if (ConfigRepo.current != null && tick != builtTick) {
            buildRows(ConfigRepo.current!!)
        }
    }

    private fun loadAndBuild(force: Boolean = false) {
        val act: FragmentActivity = activity ?: return
        lifecycleScope.launch {
            val cfg = ConfigRepo.load(act, force)
            if (cfg.categories.isEmpty()) {
                Ui.toast(act, act.getString(R.string.reload_fail), long = true)
            }
            buildRows(cfg)
            title = getString(R.string.browse_title)
        }
    }

    private fun buildRows(cfg: KidsConfig) {
        builtTick = ConfigRepo.reloadTick
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val palette = Ui.cardPalette()

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

        fun addCategoryRow(headerId: Long, title: String, colorRes: Int, entries: List<CatalogEntry>) {
            if (entries.isEmpty()) return
            val listAdapter = ArrayObjectAdapter(CardPresenter(colorRes))
            listAdapter.addAll(0, entries)
            rowsAdapter.add(ListRow(HeaderItem(headerId, title), listAdapter))
        }

        // 等级从易到难排序展示；v1兜底为 英语/纪录片
        val ordered = if (isV2) buckets else {
            val docu = buckets[getString(R.string.category_documentary)] ?: mutableListOf()
            val english = buckets[getString(R.string.category_english)] ?: mutableListOf()
            linkedMapOf(getString(R.string.category_english) to english, getString(R.string.category_documentary) to docu)
        }
        addCategoryRow(0, getString(R.string.all_row_title), palette[2], all)
        ordered.entries.forEachIndexed { idx, (title, entries) ->
            addCategoryRow((idx + 1).toLong(), title, palette[(idx + 1) % palette.size], entries)
        }

        // 家长专区
        val menu = ArrayObjectAdapter(CardPresenter(palette[3]))
        menu.add(MenuCard(
            getString(R.string.menu_settings), getString(R.string.menu_settings_sub),
            MenuCard.ACTION_SETTINGS, getString(R.string.menu_badge_settings)
        ))
        menu.add(MenuCard(
            getString(R.string.menu_reload), getString(R.string.menu_reload_sub),
            MenuCard.ACTION_RELOAD, getString(R.string.menu_badge_tool)
        ))
        rowsAdapter.add(ListRow(HeaderItem(999L, getString(R.string.settings_row_title)), menu))

        adapter = rowsAdapter
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            val act: FragmentActivity = activity ?: return
            when (item) {
                is CatalogEntry -> DetailActivity.start(act, item.catIdx, item.itemIdx)
                is MenuCard -> when (item.action) {
                    MenuCard.ACTION_SETTINGS ->
                        startActivity(Intent(act, SettingsActivity::class.java))
                    MenuCard.ACTION_RELOAD -> {
                        Ui.toast(act, act.getString(R.string.reload_loading))
                        loadAndBuild(force = true)
                    }
                }
            }
        }
    }
}
