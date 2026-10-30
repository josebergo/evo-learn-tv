package com.evolearn.kidszone.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.model.KidsConfig
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 白名单内搜索：只在片单配置的影片范围内查找，结果可直接进入详情播放。
 * 支持中文（泥坑）、英文（Peppa）、拼音（peiqi），多个关键词空格分隔取交集。
 */
class SearchActivity : FragmentActivity() {

    /** 一条可搜索的索引项：原文小写串 + 拼音串 */
    private class Doc(val entry: CatalogEntry, val hay: String, val pin: String)

    private lateinit var input: EditText
    private lateinit var emptyView: TextView
    private lateinit var statusView: TextView

    private var docs: List<Doc> = emptyList()
    private var filterJob: Job? = null

    /** 结果网格：单例（tag 寻回，Activity 重建后仍指向恢复的实例） */
    private val gridFragment: SearchGridFragment
        get() = supportFragmentManager.findFragmentByTag(TAG_GRID) as? SearchGridFragment
            ?: SearchGridFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.search_grid_container, it, TAG_GRID)
                    .commitNow()
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_search)
        Ui.applyFont(findViewById(android.R.id.content))

        input = findViewById(R.id.search_input)
        emptyView = findViewById(R.id.search_empty)
        statusView = findViewById(R.id.search_status)
        gridFragment // 确保网格就位

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterJob?.cancel()
                filterJob = lifecycleScope.launch {
                    delay(250) // 停顿防抖
                    applyFilter(s?.toString().orEmpty())
                }
            }
        })

        lifecycleScope.launch {
            val cfg = ConfigRepo.current ?: ConfigRepo.load(this@SearchActivity)
            buildIndex(cfg)
            applyFilter(input.text.toString())
        }
    }

    private fun buildIndex(cfg: KidsConfig) {
        val list = ArrayList<Doc>(ConfigRepo.totalItems(cfg))
        cfg.categories.forEachIndexed { ci, cat ->
            cat.items.forEachIndexed { ii, item ->
                val raw = listOfNotNull(
                    item.t, item.kw, item.tag, item.id,
                    item.lv?.let { "L$it" },
                    cat.title, cat.en, cat.theme, cat.id
                ).joinToString(" ")
                list.add(
                    Doc(
                        CatalogEntry(ci, ii, item, cat.title, item.lv ?: cat.level),
                        raw.lowercase(),
                        Pinyin.toPinyin(raw, "").lowercase()
                    )
                )
            }
        }
        docs = list
    }

    private fun applyFilter(query: String) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (docs.isEmpty() || tokens.isEmpty()) {
            gridFragment.showResults(emptyList())
            statusView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text =
                if (docs.isEmpty()) getString(R.string.player_loading)
                else getString(R.string.search_help, docs.size)
            return
        }
        val hits = docs.filter { d -> tokens.all { d.hay.contains(it) || d.pin.contains(it) } }
        gridFragment.showResults(hits.map { it.entry })
        if (hits.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.search_no_result, query.trim())
            statusView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            statusView.visibility = View.VISIBLE
            statusView.text = getString(R.string.search_result_count, hits.size)
        }
    }

    companion object {
        private const val TAG_GRID = "search_grid"
    }

    /** 结果网格：卡片样式与首页一致（书脊色按周次），OK点击进入详情 */
    class SearchGridFragment : VerticalGridSupportFragment() {

        private val results = ArrayObjectAdapter(CardPresenter(Ui.cardPalette()[0]))

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            gridPresenter = VerticalGridPresenter().apply { numberOfColumns = 4 }
            adapter = results
            setOnItemViewClickedListener { _, item, _, _ ->
                (item as? CatalogEntry)?.let {
                    DetailActivity.start(requireActivity(), it.catIdx, it.itemIdx)
                }
            }
        }

        fun showResults(entries: List<CatalogEntry>) {
            results.clear()
            results.addAll(0, entries)
        }
    }
}
