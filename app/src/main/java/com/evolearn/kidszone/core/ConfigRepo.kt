package com.evolearn.kidszone.core

import android.content.Context
import com.evolearn.kidszone.BuildConfig
import com.evolearn.kidszone.core.model.Category
import com.evolearn.kidszone.core.model.Item
import com.evolearn.kidszone.core.model.KidsConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 片单配置仓库。来源优先级：
 * 1. 应用内配置的地址（家长在设置里输入） customConfigUrl
 * 2. 内置默认远程地址 BuildConfig.DEFAULT_CONFIG_URL
 * 3. assets/config.default.json（离线兜底）
 * 4. 上一次成功缓存的 JSON
 * 任一远程来源 version >= 已缓存version 时才覆盖缓存。
 */
object ConfigRepo {

    private val gson = Gson()

    @Volatile
    var current: KidsConfig? = null
        private set

    /** 每次成功加载+1，供界面判断是否需要重建列表 */
    @Volatile
    var reloadTick: Int = 0
        private set

    data class LoadResult(val config: KidsConfig, val source: String, val fromNetwork: Boolean)

    suspend fun load(context: Context, forceRefresh: Boolean = false): KidsConfig {
        if (!forceRefresh && current != null) return current!!
        val ctx = context.applicationContext
        val cached = parse(Prefs.cachedConfigJson)

        // 1) 应用内配置的地址
        val custom = Prefs.customConfigUrl
        if (custom.isNotBlank()) {
            fetchAndAccept(custom, "应用内配置地址", cached)?.let {
                onLoaded(it); return it.config
            }
        }

        // 2) 内置默认远程地址（占位符视为未部署，跳过）
        val defaultUrl = BuildConfig.DEFAULT_CONFIG_URL
        if (defaultUrl.isNotBlank() && !defaultUrl.contains("yourname")) {
            fetchAndAccept(defaultUrl, "内置默认地址", cached)?.let {
                onLoaded(it); return it.config
            }
        }

        // 3) 离线内置 / 4) 本地缓存
        val assets = fromAssets(ctx)
        val pick = when {
            assets != null && (cached == null || assets.version >= cached.version) ->
                LoadResult(assets, "离线内置片单", false)
            cached != null -> LoadResult(cached, "本地缓存片单", false)
            assets != null -> LoadResult(assets, "离线内置片单", false)
            else -> LoadResult(KidsConfig(), "无片单", false)
        }
        onLoaded(pick)
        return pick.config
    }

    private fun onLoaded(r: LoadResult) {
        current = r.config
        Prefs.configSourceLabel = r.source
        Prefs.configVersion = r.config.version
        reloadTick++
    }

    private suspend fun fetchAndAccept(url: String, label: String, cached: KidsConfig?): LoadResult? {
        val raw = withContext(Dispatchers.IO) {
            runCatching { BiliApi.httpGetString(url) }.getOrNull()
        } ?: return null
        val cfg = parse(raw) ?: return null
        if (cfg.categories.sumOf { it.items.size } <= 0) return null
        if (cached != null && cfg.version < cached.version) {
            // 远程版本回退：保留更高版本的本地缓存
            return LoadResult(cached, Prefs.configSourceLabel.ifBlank { label }, false)
        }
        Prefs.cachedConfigJson = raw
        return LoadResult(cfg, label, true)
    }

    private fun parse(json: String): KidsConfig? {
        if (json.isBlank()) return null
        return runCatching {
            JsonParser.parseString(json).isJsonObject // 先校验，防半截缓存
            gson.fromJson(json, KidsConfig::class.java)
        }.getOrNull()?.takeIf { it.categories.isNotEmpty() }
    }

    private fun fromAssets(ctx: Context): KidsConfig? = runCatching {
        val raw = ctx.assets.open("config.default.json").bufferedReader().use { it.readText() }
        parse(raw)
    }.getOrNull()

    // ---------------- 导航辅助 ----------------

    fun itemAt(cfg: KidsConfig, catIdx: Int, itemIdx: Int): Item? =
        cfg.categories.getOrNull(catIdx)?.items?.getOrNull(itemIdx)

    fun categoryAt(cfg: KidsConfig, catIdx: Int): Category? = cfg.categories.getOrNull(catIdx)

    /** 同主题内下一部；跨到下一主题第一部；最后一部返回null */
    fun nextOf(cfg: KidsConfig, catIdx: Int, itemIdx: Int): Pair<Int, Int>? {
        val cat = categoryAt(cfg, catIdx) ?: return null
        if (itemIdx + 1 < cat.items.size) return catIdx to itemIdx + 1
        val nextCat = categoryAt(cfg, catIdx + 1) ?: return null
        if (nextCat.items.isEmpty()) return nextOf(cfg, catIdx + 1, -1)
        return catIdx + 1 to 0
    }

    fun totalItems(cfg: KidsConfig): Int = cfg.categories.sumOf { it.items.size }
}
