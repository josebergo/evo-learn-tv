package com.evolearn.kidszone.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 应用全部持久化状态（登录Cookie、片单缓存、解析缓存） */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("kidszone", Context.MODE_PRIVATE)
    }

    // ---- 配置来源 ----
    /** 应用内配置的地址（第1优先级）。空=未设置 */
    var customConfigUrl: String
        get() = sp.getString("custom_config_url", "") ?: ""
        set(v) = sp.edit().putString("custom_config_url", v.trim()).apply()

    var cachedConfigJson: String
        get() = sp.getString("cached_config_json", "") ?: ""
        set(v) = sp.edit().putString("cached_config_json", v).apply()

    var configSourceLabel: String
        get() = sp.getString("config_source", "") ?: ""
        set(v) = sp.edit().putString("config_source", v).apply()

    var configVersion: Int
        get() = sp.getInt("config_version", 0)
        set(v) = sp.edit().putInt("config_version", v).apply()

    // ---- 播放 ----
    /** 自动字幕（Vosk离线语音识别）总开关，片源自带字幕时家长可关闭。默认开 */
    var liveSubtitleEnabled: Boolean
        get() = sp.getBoolean("live_subtitle_enabled", true)
        set(v) = sp.edit().putBoolean("live_subtitle_enabled", v).apply()

    // ---- B站登录 ----
    /** 形如 "SESSDATA=xxx; buvid3=yyy" */
    var cookieString: String
        get() = sp.getString("cookies", "") ?: ""
        set(v) = sp.edit().putString("cookies", v).apply()

    var loginName: String
        get() = sp.getString("login_name", "") ?: ""
        set(v) = sp.edit().putString("login_name", v).apply()

    var loginMid: String
        get() = sp.getString("login_mid", "") ?: ""
        set(v) = sp.edit().putString("login_mid", v).apply()

    /** 合并扫码登录下发的新Cookie，保留原有 buvid3 等基础Cookie */
    fun mergeCookies(newCookies: Map<String, String>) {
        val map = decodeCookies(cookieString)
        map.putAll(newCookies)
        cookieString = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun isLoggedIn(): Boolean = cookieString.contains("SESSDATA")

    fun clearLogin() {
        cookieString = ""
        loginName = ""
        loginMid = ""
    }

    private fun decodeCookies(raw: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in raw.split(";")) {
            val idx = pair.indexOf('=')
            if (idx > 0) map[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
        }
        return map
    }

    // ---- 关键词 → BV号 解析缓存 ----
    private val resolveMap: MutableMap<String, String> by lazy {
        val raw = sp.getString("resolve_cache", "{}") ?: "{}"
        Gson().fromJson(raw, object : TypeToken<MutableMap<String, String>>() {}.type) ?: mutableMapOf()
    }

    @Synchronized
    fun cachedBvid(itemId: String): String? = resolveMap[itemId]

    @Synchronized
    fun saveBvid(itemId: String, bvid: String) {
        resolveMap[itemId] = bvid
        sp.edit().putString("resolve_cache", Gson().toJson(resolveMap)).apply()
    }
}
