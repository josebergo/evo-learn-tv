package com.evolearn.kidszone.core.model

import com.google.gson.annotations.SerializedName

/** 远程配置根对象，见 server/config.default.json */
data class KidsConfig(
    val version: Int = 0,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val server: ServerInfo? = null,
    val play: PlayInfo? = null,
    val levels: List<LevelInfo>? = null,
    val categories: List<Category> = emptyList()
)

/** 难度等级定义（v2配置）：L1启蒙~L5挑战 */
data class LevelInfo(
    val lv: Int = 0,
    val title: String? = null,
    val en: String? = null,
    val theme: String? = null
)

data class ServerInfo(
    val name: String? = null,
    val notice: String? = null
)

data class PlayInfo(
    @SerializedName("default_qn") val defaultQn: Int? = 64,
    @SerializedName("autoplay_next") val autoplayNext: Boolean? = true
)

data class Category(
    val id: String = "",
    val title: String = "",
    val en: String? = null,
    val theme: String? = null,
    /** v2：该分类的难度等级（1-5）；纪录片分类=4 */
    val level: Int? = null,
    val items: List<Item> = emptyList()
)

data class Item(
    val id: String = "",
    val t: String = "",
    val kw: String? = null,
    val bvid: String? = null,
    val ep: Int? = null,
    val min: Int? = null,
    val tag: String? = null,
    /** v2：难度等级1-5，卡片徽标展示 */
    val lv: Int? = null
)
