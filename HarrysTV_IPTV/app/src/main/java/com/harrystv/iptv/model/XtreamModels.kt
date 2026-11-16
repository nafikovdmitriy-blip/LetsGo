package com.harrystv.iptv.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String?,
    val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class ServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("server_protocol") val protocol: String?
)

data class CategoryItem(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

data class StreamItem(
    @SerializedName("stream_id") val streamId: Int,
    val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null
)

data class SeriesItem(
    @SerializedName("series_id") val seriesId: Int,
    val name: String,
    val cover: String?,
    val plot: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class SeriesInfoResponse(
    val info: SeriesMetadata?,
    val episodes: Map<String, List<EpisodeItem>>?
)

data class SeriesMetadata(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val rating: String?
)

data class EpisodeItem(
    val id: String,
    @SerializedName("episode_num") val episodeNum: Int,
    val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val season: Int?
)

data class ShortEpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgEntry>?
)

data class EpgEntry(
    val title: String?,
    val start: String?,
    val end: String?,
    @SerializedName("start_timestamp") val startTimestamp: String?,
    @SerializedName("stop_timestamp") val stopTimestamp: String?,
    val description: String?
)

data class M3uChannel(
    val name: String,
    val groupTitle: String,
    val logoUrl: String?,
    val tvgId: String?,
    val streamUrl: String
)

data class EpgProgram(
    val channelId: String,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String
)
