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
 * 主界面：左侧=每周主题（52周+家长专区），右侧=该周7部片单大卡片。
 * 全部内容来自配置文件——孩子在这里看不到任何配置之外的推荐/搜索入口。
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

        // 不设置 OnSearchClickedListener：TV端不出现搜索球，杜绝任意搜索
        setOnItemViewClickedListener(ItemViewClickedListener())

        if (ConfigRepo.current != null) {
            buildRows(ConfigRepo.current!!)
        } else {
            title = getString(R.string.player_loading)
            loadAndBuild()
        }
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
