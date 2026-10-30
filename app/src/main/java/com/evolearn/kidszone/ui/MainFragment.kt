package com.evolearn.kidszone.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.model.Item
import com.evolearn.kidszone.core.model.KidsConfig
import kotlinx.coroutines.launch

/**
 * 主界面：左侧=全部影片 / 每周主题（52周+家长专区），右侧=片单大卡片。
 * 全部内容来自配置文件——搜索也只在白名单内查找，孩子看不到任何配置之外的内容。
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

        // 搜索球：进入白名单内搜索页
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
                Toast.makeText(act, R.string.reload_fail, Toast.LENGTH_LONG).show()
            }
            buildRows(cfg)
            title = getString(R.string.browse_title)
        }
    }

    private fun buildRows(cfg: KidsConfig) {
        builtTick = ConfigRepo.reloadTick
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val palette = Ui.cardPalette()

        // 全部影片：365部平铺，不必按周找片
        val all = ArrayObjectAdapter(CardPresenter(palette[2]))
        val entries = ArrayList<CatalogEntry>(ConfigRepo.totalItems(cfg))
        cfg.categories.forEachIndexed { ci, cat ->
            cat.items.forEachIndexed { ii, item -> entries.add(CatalogEntry(ci, ii, item, cat.title)) }
        }
        if (entries.isNotEmpty()) {
            all.addAll(0, entries)
            rowsAdapter.add(ListRow(HeaderItem(998L, getString(R.string.all_row_title)), all))
        }

        cfg.categories.forEachIndexed { idx, cat ->
            val listAdapter = ArrayObjectAdapter(CardPresenter(palette[idx % palette.size]))
            listAdapter.addAll(0, cat.items)
            rowsAdapter.add(ListRow(HeaderItem(idx.toLong(), cat.title), listAdapter))
        }

        // 家长专区（入口不显眼，PIN门禁）
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
                is Item -> {
                    val listRow = row as? ListRow ?: return
                    val adapter = listRow.adapter as? ArrayObjectAdapter ?: return
                    val itemIdx = adapter.indexOf(item)
                    if (itemIdx >= 0) {
                        DetailActivity.start(act, listRow.headerItem.id.toInt(), itemIdx)
                    }
                }
                is CatalogEntry -> DetailActivity.start(act, item.catIdx, item.itemIdx)
                is MenuCard -> when (item.action) {
                    MenuCard.ACTION_SETTINGS ->
                        PinDialog.show(act.supportFragmentManager, getString(R.string.menu_settings)) {
                            startActivity(Intent(act, SettingsActivity::class.java))
                        }
                    MenuCard.ACTION_RELOAD -> {
                        Toast.makeText(act, R.string.reload_loading, Toast.LENGTH_SHORT).show()
                        loadAndBuild(force = true)
                    }
                }
            }
        }
    }
}
