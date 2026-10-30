package com.evolearn.kidszone.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B站公开web接口封装：二维码登录 / 登录态 / 搜索 / 分P / 播放地址。
 * 全部使用与网页登录同一套官方协议，详见 docs/DESIGN.md。
 */
object BiliApi {

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val b = chain.request().newBuilder().header("User-Agent", UA)
            val host = chain.request().url.host
            if (host.endsWith("bilibili.com")) {
                b.header("Referer", "https://www.bilibili.com/")
                val cookie = Prefs.cookieString
                if (cookie.isNotBlank()) b.header("Cookie", cookie)
            }
            chain.proceed(b.build())
        }
        .build()

    data class QrInit(val url: String, val qrcodeKey: String)
    data class QrPollResult(val code: Int, val cookies: Map<String, String>)

    // ---------------- 基础请求 ----------------

    suspend fun httpGetString(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private suspend fun getJson(url: String): JsonObject? =
        httpGetString(url)?.let { raw ->
            runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        }

    private fun JsonObject.dataObj(): JsonObject? =
        get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.int(field: String, def: Int = -1): Int =
        get(field)?.takeIf { it.isJsonPrimitive }?.asInt ?: def

    private fun JsonObject.str(field: String): String? =
        get(field)?.takeIf { it.isJsonPrimitive }?.takeUnless { it.isJsonNull }?.asString

    // ---------------- 二维码登录 ----------------

    suspend fun qrGenerate(): QrInit? {
        val obj = getJson("https://passport.bilibili.com/x/passport-login/web/qrcode/generate") ?: return null
        val data = obj.dataObj() ?: return null
        val url = data.str("url") ?: return null
        val key = data.str("qrcode_key") ?: return null
        return QrInit(url, key)
    }

    /** 轮询扫码状态；code: 861未扫 860已过期 86090已扫待确认 0成功 */
    suspend fun qrPoll(qrcodeKey: String): QrPollResult? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey")
                    .build()
            ).execute().use { resp ->
                val raw = resp.body?.string() ?: return@use null
                val obj = JsonParser.parseString(raw).asJsonObject
                val code = obj.dataObj()?.int("code", -1) ?: -1
                val cookies = if (code == 0) {
                    resp.headers("set-cookie").mapNotNull { header ->
                        val kv = header.substringBefore(";")
                        val idx = kv.indexOf('=')
                        if (idx > 0) kv.substring(0, idx).trim() to kv.substring(idx + 1).trim() else null
                    }.toMap()
                } else emptyMap()
                QrPollResult(code, cookies)
            }
        }.getOrNull()
    }

    /** 登录态：成功返回 (昵称, mid) */
    suspend fun nav(): Pair<String, String>? {
        val obj = getJson("https://api.bilibili.com/x/web-interface/nav") ?: return null
        val data = obj.dataObj() ?: return null
        if (data.int("isLogin", 0) != 1) return null
        val name = data.str("uname") ?: return null
        val mid = data.str("mid") ?: ""
        return name to mid
    }

    // ---------------- 内容解析 ----------------

    /** 未登录/无buvid时搜索接口可能风控，先补一个基础Cookie */
    suspend fun ensureBuvid() {
        if (Prefs.cookieString.contains("buvid3")) return
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("https://www.bilibili.com/").build())
                    .execute().use { resp ->
                        val map = resp.headers("set-cookie").mapNotNull { header ->
                            val kv = header.substringBefore(";")
                            val idx = kv.indexOf('=')
                            if (idx > 0) kv.substring(0, idx).trim() to kv.substring(idx + 1).trim() else null
                        }.toMap()
                        if (map.isNotEmpty()) Prefs.mergeCookies(map)
                    }
            }
        }
    }

    /** 搜索关键词 → 第一个有效视频的BV号（时长过滤掉预告/短视频噪声） */
    suspend fun searchFirst(kw: String): String? {
        ensureBuvid()
        val url = "https://api.bilibili.com/x/web-interface/search/type" +
            "?search_type=video&page=1&keyword=" + java.net.URLEncoder.encode(kw, "UTF-8")
        val obj = getJson(url) ?: return null
        if (obj.int("code", -1) != 0) return null
        val result = obj.dataObj()?.get("result")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val pick = result.firstOrNull { v ->
            v.isJsonObject && parseDurationSec(v.asJsonObject.str("duration")) >= 60
        } ?: result.firstOrNull { it.isJsonObject } ?: return null
        return pick.asJsonObject.str("bvid")
    }

    private fun parseDurationSec(d: String?): Int {
        if (d.isNullOrBlank()) return 0
        return d.split(":").mapNotNull { it.toIntOrNull() }.reduce { acc, p -> acc * 60 + p }
    }

    /** 分P列表（JsonArray: [{cid,page,part}]），供取cid */
    suspend fun pagelist(bvid: String): List<JsonObject>? {
        val obj = getJson("https://api.bilibili.com/x/player/pagelist?bvid=$bvid") ?: return null
        if (obj.int("code", -1) != 0) return null
        val arr = obj.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return arr.filter { it.isJsonObject }.map { it.asJsonObject }
    }

    /** HTML5 平台直链MP4（默认qn=64即720p上限）；需带Referer/Cookie拉流 */
    suspend fun playUrl(bvid: String, cid: Long, qn: Int): String? {
        val url = "https://api.bilibili.com/x/player/playurl" +
            "?bvid=$bvid&cid=$cid&qn=$qn&fnval=1&platform=html5&high_quality=1&fourk=0"
        val obj = getJson(url) ?: return null
        if (obj.int("code", -1) != 0) return null
        val durl = obj.dataObj()?.get("durl")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val first = durl.firstOrNull()?.asJsonObject ?: return null
        return first.str("url")
    }

    /** DASH 音视频分离流（登录后可达1080P及以上；游客通常只有480P以下） */
    data class DashStream(
        val videoUrl: String,
        val videoBackupUrl: String?,
        val audioUrl: String,
        val audioBackupUrl: String?,
        val qn: Int
    )

    /**
     * 拉取DASH流：在账号允许范围内选最高清晰度，视频优先H.264(codecid=7)保证盒子解码兼容，
     * 音频选码率最高的一条；URL优先upos正式节点（mcdn节点是http+端口直连，部分网络不稳）。
     */
    suspend fun playDash(bvid: String, cid: Long): DashStream? {
        val url = "https://api.bilibili.com/x/player/playurl" +
            "?bvid=$bvid&cid=$cid&qn=116&fnval=16&fourk=1"
        val obj = getJson(url) ?: return null
        if (obj.int("code", -1) != 0) return null
        val dash = obj.dataObj()?.get("dash")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

        val videos = dash.get("video")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }?.map { it.asJsonObject } ?: return null
        if (videos.isEmpty()) return null

        fun JsonObject.qnId(): Int = int("id", 0)
        fun JsonObject.urls(): List<String> =
            listOfNotNull(str("baseUrl")).plus(
                (get("backupUrl")?.takeIf { it.isJsonArray }?.asJsonArray ?: emptyList())
                    .mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }
            )

        val avc = videos.filter { it.int("codecid", 0) == 7 }
        val pool = avc.ifEmpty { videos }
        val bestVideo = pool.maxByOrNull { it.qnId() } ?: return null

        val audios = dash.get("audio")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }?.map { it.asJsonObject } ?: return null
        val bestAudio = audios.maxByOrNull { it.int("id", 0) } ?: return null

        val vUrls = bestVideo.urls().let { list ->
            list.filterNot { it.contains("mcdn") }.ifEmpty { list }
        }
        val aUrls = bestAudio.urls().let { list ->
            list.filterNot { it.contains("mcdn") }.ifEmpty { list }
        }
        if (vUrls.isEmpty() || aUrls.isEmpty()) return null

        return DashStream(
            videoUrl = vUrls.first(),
            videoBackupUrl = vUrls.getOrNull(1),
            audioUrl = aUrls.first(),
            audioBackupUrl = aUrls.getOrNull(1),
            qn = bestVideo.qnId()
        )
    }

    /** 画质qn → 展示名 */
    fun qnLabel(qn: Int): String = when (qn) {
        127, 126, 125, 120 -> "4K 超清"
        116 -> "1080P60 高帧率"
        112 -> "1080P+ 高码率"
        80 -> "1080P 高清"
        74, 64 -> "720P 高清"
        48, 32 -> "480P 标清"
        else -> "360P 流畅"
    }

    // ---------------- CC字幕（英中双语） ----------------

    data class SubLine(val fromMs: Long, val toMs: Long, val text: String)

    /**
     * 拉取视频CC字幕的英文/中文轨（AI字幕需登录态；游客通常无轨）。
     * 返回 (enLines, zhLines)，两轨都空返回null。
     */
    suspend fun subtitles(bvid: String, cid: Long): Pair<List<SubLine>, List<SubLine>>? {
        val obj = getJson("https://api.bilibili.com/x/player/v2?bvid=$bvid&cid=$cid") ?: return null
        if (obj.int("code", -1) != 0) return null
        val subs = obj.dataObj()
            ?.get("subtitle")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("subtitles")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return null
        var enUrl: String? = null
        var zhUrl: String? = null
        for (e in subs) {
            if (!e.isJsonObject) continue
            val o = e.asJsonObject
            val lan = o.str("lan") ?: continue
            val url = o.str("subtitle_url") ?: continue
            val full = if (url.startsWith("//")) "https:$url" else url
            when {
                lan == "en" || lan.startsWith("en-") -> if (enUrl == null) enUrl = full
                lan == "zh-CN" || lan == "zh-Hans" -> if (zhUrl == null) zhUrl = full
                lan.startsWith("ai-en") -> if (enUrl == null) enUrl = full
                lan.startsWith("ai-zh") -> if (zhUrl == null) zhUrl = full
            }
        }
        val en = enUrl?.let { fetchSubLines(it) } ?: emptyList()
        val zh = zhUrl?.let { fetchSubLines(it) } ?: emptyList()
        if (en.isEmpty() && zh.isEmpty()) return null
        return en to zh
    }

    private suspend fun fetchSubLines(url: String): List<SubLine> {
        val raw = httpGetString(url) ?: return emptyList()
        return runCatching {
            val body = JsonParser.parseString(raw).asJsonObject
                .get("body")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            body.filter { it.isJsonObject }.mapNotNull { e ->
                val o = e.asJsonObject
                val from = o.get("from")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@mapNotNull null
                val to = o.get("to")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@mapNotNull null
                val text = o.str("content") ?: return@mapNotNull null
                SubLine((from * 1000).toLong(), (to * 1000).toLong(), text.trim())
            }.sortedBy { it.fromMs }
        }.getOrDefault(emptyList())
    }
}
