package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "username") val username: String? = null,
    @Json(name = "password") val password: String? = null,
    @Json(name = "auth") val auth: Any? = null, // puede venir como 1 o "1"
    @Json(name = "status") val status: String? = null,
    @Json(name = "exp_date") val expDate: String? = null,
    @Json(name = "active_cons") val activeCons: String? = null,
    @Json(name = "max_connections") val maxConnections: String? = null
) {
    val isAuthed: Boolean
        get() = auth?.toString() == "1"
}

@JsonClass(generateAdapter = true)
data class ServerInfo(
    @Json(name = "url") val url: String? = null,
    @Json(name = "port") val port: String? = null,
    @Json(name = "server_protocol") val serverProtocol: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: UserInfo? = null,
    @Json(name = "server_info") val serverInfo: ServerInfo? = null
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "category_name") val categoryName: String? = null,
    @Json(name = "parent_id") val parentId: Any? = null
) {
    val idString: String
        get() = categoryId?.toString() ?: ""
    val nameString: String
        get() = categoryName ?: ""
}

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    @Json(name = "num") val num: Any? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "stream_type") val streamType: String? = null,
    @Json(name = "stream_id") val streamId: Any? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "category_id") val categoryId: Any? = null
) {
    val idString: String
        get() = streamId?.toString() ?: ""
    val catString: String
        get() = categoryId?.toString() ?: ""
}

@JsonClass(generateAdapter = true)
data class XtreamVodStream(
    @Json(name = "num") val num: Any? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "stream_type") val streamType: String? = null,
    @Json(name = "stream_id") val streamId: Any? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "rating") val rating: Any? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "container_extension") val containerExtension: String? = null
) {
    val idString: String
        get() = streamId?.toString() ?: ""
    val catString: String
        get() = categoryId?.toString() ?: ""
    val ratingFloat: Float
        get() = rating?.toString()?.toFloatOrNull() ?: 0f
}

@JsonClass(generateAdapter = true)
data class XtreamSeriesItem(
    @Json(name = "num") val num: Any? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "series_id") val seriesId: Any? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "cast") val cast: String? = null,
    @Json(name = "director") val director: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "last_modified") val lastModified: String? = null,
    @Json(name = "rating") val rating: Any? = null,
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "backdrop_path") val backdropPath: Any? = null
) {
    val idString: String
        get() = seriesId?.toString() ?: ""
    val catString: String
        get() = categoryId?.toString() ?: ""
    val ratingFloat: Float
        get() = rating?.toString()?.toFloatOrNull() ?: 0f
    val backdropUrl: String
        get() {
            if (backdropPath is List<*>) {
                return (backdropPath as List<*>).firstOrNull()?.toString() ?: ""
            }
            return backdropPath?.toString() ?: ""
        }
}

// Representación unificada en memoria para buscador y listas
data class CatalogItem(
    val mode: String, // "live", "vod", "series"
    val sid: String,
    val name: String,
    val img: String,
    val rating: Float,
    val added: Long,
    val ext: String = "mp4",
    val cid: String = "",
    val plot: String = "",
    val bg: String = "",
    val nl: String = "" // Clave normalizada de búsqueda
)
