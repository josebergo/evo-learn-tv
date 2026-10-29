package com.evolearn.kidszone.core.model

import com.google.gson.annotations.SerializedName

/** 远程配置根对象，见 server/config.default.json */
data class KidsConfig(
    val version: Int = 0,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val server: ServerInfo? = null,
    val play: PlayInfo? = null,
    val categories: List<Category> = emptyList()
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
    val items: List<Item> = emptyList()
)

data class Item(
    val id: String = "",
    val t: String = "",
    val kw: String? = null,
    val bvid: String? = null,
    val ep: Int? = null,
    val min: Int? = null,
    val tag: String? = null
)
