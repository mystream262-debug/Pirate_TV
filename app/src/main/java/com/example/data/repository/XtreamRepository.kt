package com.example.data.repository

import com.example.data.api.ApiClient
import com.example.data.model.CatalogItem
import com.example.data.model.EpisodeInfo
import com.example.data.model.FlatEpisode
import com.example.data.model.ImdbInfo
import com.example.data.model.SeriesEpisode
import com.example.data.model.UserInfo
import com.example.data.model.VodInfoDetails
import com.example.data.model.XtreamCategory
import com.example.data.storage.PlaybackProgressManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// Utilidades de normalización según HTA original
val PUNCT_REGEX = Regex("""[\-_.:,;!?'"()\[\]|/\\&+*#@~`\u00b4\u2018\u2019\u201c\u201d]+""")
const val FROM_CHARS = "\u00e1\u00e0\u00e4\u00e2\u00e3\u00e9\u00e8\u00eb\u00ea\u00ed\u00ec\u00ef\u00ee\u00f3\u00f2\u00f6\u00f4\u00f5\u00fa\u00f9\u00fc\u00fb\u00f1\u00e7"
const val TO_CHARS   = "aaaaaeeeeiiiiooooouuuunc"

fun fold(s: String?): String {
    if (s == null) return ""
    val lower = s.lowercase()
    val sb = StringBuilder(lower.length)
    for (ch in lower) {
        val idx = FROM_CHARS.indexOf(ch)
        if (idx >= 0) sb.append(TO_CHARS[idx]) else sb.append(ch)
    }
    return sb.toString()
}

fun keyOf(s: String?): String {
    return fold(s).replace(PUNCT_REGEX, " ").trim().replace(Regex("""\s+"""), " ")
}

fun tokens(q: String): List<String> {
    val k = keyOf(q)
    return if (k.isEmpty()) emptyList() else k.split(" ").filter { it.isNotEmpty() }
}

fun hashNs(base: String, user: String): String {
    val s = "$base|$user"
    var h = 5381L
    for (ch in s) {
        h = (((h shl 5) + h) + ch.code) and 0xFFFFFFFFL
    }
    return java.lang.Long.toUnsignedString(h, 36)
}

data class CleanTitle(val title: String, val year: Int?)

fun cleanTitle(name: String?): CleanTitle {
    var s = name ?: ""
    var year: Int? = null
    val yearRegex = Regex("""[\(\[]\s*((?:19|20)\d{2})\s*[\)\]]""")
    val m = yearRegex.find(s)
    if (m != null) {
        year = m.groupValues[1].toIntOrNull()
    }
    s = s.replace(Regex("""^\s*[A-Z]{2,4}\s*\|\s*"""), "")
        .replace(Regex("""^\s*[A-Z]{2,4}\s+-\s+"""), "")
        .replace(Regex("""\[[^\]]*\]"""), " ")
        .replace(Regex("""\([^\)]*\)"""), " ")
        .replace(Regex("""\b(4K|UHD|FHD|FULLHD|HD|SD|1080P|720P|2160P|HEVC|H265|X265|LATINO|CASTELLANO|ESPA.OL|DUAL|MULTI|SUBTITULADO|SUBS?|VOSE|VOS)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""[\|_]+"""), " ")

    val endYearRegex = Regex("""^(.*\S)[\s\-:]+((?:19|20)\d{2})\s*$""")
    val endM = endYearRegex.find(s)
    if (endM != null) {
        if (year == null) year = endM.groupValues[2].toIntOrNull()
        s = endM.groupValues[1]
    }
    s = s.replace(Regex("""\s{2,}"""), " ").trim(' ', '-', ':')
    return CleanTitle(s.ifEmpty { name ?: "" }, year)
}

class XtreamRepository(
    private val progressManager: PlaybackProgressManager
) {

    private val api = ApiClient.xtreamService
    private val omdbApi = ApiClient.omdbService

    // Caché en memoria de catálogos y categorías
    val catalogCache = mutableMapOf<String, List<CatalogItem>>() // "live", "vod", "series"
    val categoryCache = mutableMapOf<String, List<XtreamCategory>>()
    val categoryCounts = mutableMapOf<String, Map<String, Int>>()
    val categoryNames = mutableMapOf<String, Map<String, String>>()
    val imdbCache = mutableMapOf<String, ImdbInfo>()

    var currentBaseUrl = "http://sdmx.vip:80"
    var currentUser = ""
    var currentPass = ""
    var currentNs = ""

    fun setupSession(baseUrl: String, user: String, pass: String) {
        currentBaseUrl = baseUrl.trimEnd('/')
        currentUser = user.trim()
        currentPass = pass
        currentNs = hashNs(currentBaseUrl, currentUser)
    }

    fun clearSession() {
        catalogCache.clear()
        categoryCache.clear()
        categoryCounts.clear()
        categoryNames.clear()
    }

    suspend fun login(baseUrl: String, user: String, pass: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            setupSession(baseUrl, user, pass)
            val apiUrl = "$currentBaseUrl/player_api.php"
            val response = api.authenticate(apiUrl, currentUser, currentPass)
            val userInfo = response.userInfo

            if (userInfo == null || !userInfo.isAuthed) {
                Result.failure(Exception("Usuario o contraseña incorrectos."))
            } else {
                Result.success(userInfo)
            }
        } catch (e: Exception) {
            Result.failure(Exception("No se pudo conectar con el servidor (${e.localizedMessage ?: "error de red"})."))
        }
    }

    suspend fun loadCategories(mode: String): List<XtreamCategory> = withContext(Dispatchers.IO) {
        categoryCache[mode]?.let { return@withContext it }
        val apiUrl = "$currentBaseUrl/player_api.php"
        try {
            val list = when (mode) {
                "live" -> api.getLiveCategories(apiUrl, currentUser, currentPass)
                "vod" -> api.getVodCategories(apiUrl, currentUser, currentPass)
                else -> api.getSeriesCategories(apiUrl, currentUser, currentPass)
            }
            categoryCache[mode] = list
            categoryNames[mode] = list.associate { it.idString to it.nameString }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun loadCatalog(mode: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        catalogCache[mode]?.let { return@withContext it }
        loadCategories(mode)
        val apiUrl = "$currentBaseUrl/player_api.php"

        try {
            val items: List<CatalogItem> = when (mode) {
                "live" -> {
                    val raw = api.getLiveStreams(apiUrl, currentUser, currentPass)
                    raw.map {
                        CatalogItem(
                            mode = "live",
                            sid = it.idString,
                            name = it.name ?: "",
                            img = it.streamIcon ?: "",
                            rating = 0f,
                            added = it.added?.toLongOrNull() ?: 0L,
                            cid = it.catString,
                            nl = keyOf(it.name)
                        )
                    }
                }
                "vod" -> {
                    val raw = api.getVodStreams(apiUrl, currentUser, currentPass)
                    raw.map {
                        CatalogItem(
                            mode = "vod",
                            sid = it.idString,
                            name = it.name ?: "",
                            img = it.streamIcon ?: "",
                            rating = it.ratingFloat,
                            added = it.added?.toLongOrNull() ?: 0L,
                            ext = it.containerExtension?.ifEmpty { "mp4" } ?: "mp4",
                            cid = it.catString,
                            nl = keyOf(it.name)
                        )
                    }.sortedWith(compareByDescending<CatalogItem> { it.rating }.thenByDescending { it.added })
                }
                else -> {
                    val raw = api.getSeries(apiUrl, currentUser, currentPass)
                    raw.map {
                        CatalogItem(
                            mode = "series",
                            sid = it.idString,
                            name = it.name ?: "",
                            img = it.cover ?: "",
                            rating = it.ratingFloat,
                            added = it.lastModified?.toLongOrNull() ?: 0L,
                            cid = it.catString,
                            plot = it.plot ?: "",
                            bg = it.backdropUrl,
                            nl = keyOf(it.name)
                        )
                    }.sortedWith(compareByDescending<CatalogItem> { it.rating }.thenByDescending { it.added })
                }
            }

            // Calcular conteo de elementos por categoría
            val counts = mutableMapOf<String, Int>()
            for (it in items) {
                if (it.cid.isNotEmpty()) {
                    counts[it.cid] = (counts[it.cid] ?: 0) + 1
                }
            }
            categoryCounts[mode] = counts
            catalogCache[mode] = items
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun search(query: String, modeFilter: String = "all"): Map<String, List<CatalogItem>> {
        val qTokens = tokens(query)
        if (qTokens.isEmpty()) return emptyMap()

        val modes = if (modeFilter == "all") listOf("live", "vod", "series") else listOf(modeFilter)
        val result = mutableMapOf<String, List<CatalogItem>>()

        for (m in modes) {
            val list = catalogCache[m] ?: emptyList()
            val filtered = list.filter { item ->
                qTokens.all { token -> item.nl.contains(token) }
            }
            result[m] = filtered
        }
        return result
    }

    suspend fun getVodDetails(vodId: String): Pair<VodInfoDetails, String> = withContext(Dispatchers.IO) {
        val apiUrl = "$currentBaseUrl/player_api.php"
        try {
            val resp = api.getVodInfoRaw(apiUrl, currentUser, currentPass, vodId = vodId)
            val jsonString = resp.string()
            val jsonObj = JSONObject(jsonString)

            val infoObj = jsonObj.optJSONObject("info")
            val movieDataObj = jsonObj.optJSONObject("movie_data")

            val details = if (infoObj != null) {
                var bg = ""
                val bgOpt = infoObj.opt("backdrop_path")
                if (bgOpt is JSONArray && bgOpt.length() > 0) {
                    bg = bgOpt.optString(0)
                } else if (bgOpt != null && bgOpt !is JSONObject && bgOpt !is JSONArray) {
                    bg = bgOpt.toString()
                }

                VodInfoDetails(
                    movieImage = infoObj.optString("movie_image"),
                    backdropPath = bg,
                    plot = infoObj.optString("plot").ifEmpty { infoObj.optString("description") },
                    releaseDate = infoObj.optString("releasedate").ifEmpty { infoObj.optString("release_date") },
                    duration = infoObj.optString("duration"),
                    genre = infoObj.optString("genre"),
                    director = infoObj.optString("director"),
                    cast = infoObj.optString("cast"),
                    rating = infoObj.optString("rating"),
                    youtubeTrailer = infoObj.optString("youtube_trailer")
                )
            } else {
                VodInfoDetails()
            }

            val ext = movieDataObj?.optString("container_extension")?.ifEmpty { "mp4" } ?: "mp4"
            Pair(details, ext)
        } catch (_: Exception) {
            Pair(VodInfoDetails(), "mp4")
        }
    }

    suspend fun getSeriesDetails(seriesId: String): Pair<VodInfoDetails, List<FlatEpisode>> = withContext(Dispatchers.IO) {
        val apiUrl = "$currentBaseUrl/player_api.php"
        try {
            val resp = api.getSeriesInfoRaw(apiUrl, currentUser, currentPass, seriesId = seriesId)
            val jsonString = resp.string()
            val jsonObj = JSONObject(jsonString)

            val infoObj = jsonObj.optJSONObject("info")
            var bg = ""
            val bgOpt = infoObj?.opt("backdrop_path")
            if (bgOpt is JSONArray && bgOpt.length() > 0) {
                bg = bgOpt.optString(0)
            } else if (bgOpt != null && bgOpt !is JSONObject && bgOpt !is JSONArray) {
                bg = bgOpt.toString()
            }

            val details = VodInfoDetails(
                movieImage = infoObj?.optString("cover"),
                backdropPath = bg,
                plot = infoObj?.optString("plot"),
                releaseDate = infoObj?.optString("releaseDate"),
                duration = infoObj?.optString("episode_run_time")?.let { if (it.isNotEmpty()) "$it min" else "" },
                genre = infoObj?.optString("genre"),
                director = infoObj?.optString("director"),
                cast = infoObj?.optString("cast"),
                rating = infoObj?.optString("rating"),
                youtubeTrailer = infoObj?.optString("youtube_trailer")
            )

            val flatEpisodes = mutableListOf<FlatEpisode>()
            val episodesObj = jsonObj.optJSONObject("episodes")

            if (episodesObj != null) {
                val seasonKeys = episodesObj.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
                for (sn in seasonKeys) {
                    val arr = episodesObj.optJSONArray(sn) ?: continue
                    for (i in 0 until arr.length()) {
                        val epObj = arr.optJSONObject(i) ?: continue
                        val epId = epObj.optString("id")
                        val epNum = epObj.optInt("episode_num", i + 1)
                        val epTitle = epObj.optString("title")
                        val ext = epObj.optString("container_extension").ifEmpty { "mp4" }
                        val epInfo = epObj.optJSONObject("info")

                        val epImg = epInfo?.optString("movie_image") ?: ""
                        val epPlot = epInfo?.optString("plot") ?: ""
                        val epDur = epInfo?.optString("duration") ?: ""
                        val epDurSecs = epInfo?.optLong("duration_secs", 0L) ?: 0L

                        val streamUrl = "$currentBaseUrl/series/$currentUser/$currentPass/$epId.$ext"

                        flatEpisodes.add(
                            FlatEpisode(
                                season = sn,
                                num = epNum,
                                id = epId,
                                title = epTitle,
                                ext = ext,
                                durationText = epDur,
                                durationSecs = epDurSecs,
                                image = epImg,
                                plot = epPlot,
                                key = PlaybackProgressManager.ekKey(currentNs, epId),
                                streamUrl = streamUrl
                            )
                        )
                    }
                }
            }

            Pair(details, flatEpisodes)
        } catch (_: Exception) {
            Pair(VodInfoDetails(), emptyList())
        }
    }

    suspend fun lookupImdb(
        rawName: String,
        mode: String,
        customQuery: String? = null,
        omdbKey: String = ""
    ): ImdbInfo? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(rawName)
        val title = (customQuery ?: clean.title).trim()
        val cacheKey = "$mode|${title.lowercase()}|${if (customQuery != null) "" else (clean.year?.toString() ?: "")}"

        imdbCache[cacheKey]?.let { return@withContext it }

        if (title.isEmpty()) return@withContext null

        try {
            // Si hay clave OMDb, consultamos directamente OMDb por título y año
            if (omdbKey.isNotEmpty()) {
                val omdbRes = omdbApi.getOmdbByTitle(
                    title = title,
                    year = if (customQuery != null) null else clean.year,
                    apiKey = omdbKey
                )
                if (omdbRes.response.equals("True", ignoreCase = true) && omdbRes.imdbId != null) {
                    val info = ImdbInfo(
                        id = omdbRes.imdbId,
                        title = omdbRes.title ?: title,
                        year = omdbRes.year,
                        plot = omdbRes.plot,
                        rating = omdbRes.imdbRating,
                        votes = omdbRes.imdbVotes,
                        rt = omdbRes.rottenTomatoesScore,
                        genre = omdbRes.genre,
                        runtime = omdbRes.runtime,
                        director = omdbRes.director,
                        actors = omdbRes.actors,
                        rated = omdbRes.rated,
                        awards = omdbRes.awards,
                        img = omdbRes.poster
                    )
                    imdbCache[cacheKey] = info
                    return@withContext info
                }
            }

            // Fallback a sugerencias de IMDb
            val firstChar = title.firstOrNull()?.lowercaseChar() ?: 'x'
            val ch = if (firstChar in 'a'..'z' || firstChar in '0'..'9') firstChar else 'x'
            val encodedTitle = URLEncoder.encode(title.lowercase(), "UTF-8")
            val suggUrl = "https://v2.sg.media-imdb.com/suggestion/$ch/$encodedTitle.json"

            val body = omdbApi.getImdbSuggestions(suggUrl).string()
            val root = JSONObject(body)
            val dArr = root.optJSONArray("d") ?: return@withContext null

            // Elegir el mejor match
            var bestId: String? = null
            var bestTitle = title
            var bestYear: String? = null
            var bestImg: String? = null

            for (i in 0 until dArr.length()) {
                val item = dArr.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (!id.startsWith("tt")) continue
                val q = item.optString("q").lowercase()
                if (q.contains("video game")) continue

                bestId = id
                bestTitle = item.optString("l", title)
                bestYear = item.optInt("y", 0).let { if (it > 0) it.toString() else null }
                bestImg = item.optJSONObject("i")?.optString("imageUrl")
                break
            }

            if (bestId != null) {
                // Si tenemos la ID y OMDb key, traemos detalles completos con la ID
                if (omdbKey.isNotEmpty()) {
                    val omdbDetail = omdbApi.getOmdbById(imdbId = bestId, apiKey = omdbKey)
                    if (omdbDetail.response.equals("True", ignoreCase = true)) {
                        val info = ImdbInfo(
                            id = bestId,
                            title = omdbDetail.title ?: bestTitle,
                            year = omdbDetail.year ?: bestYear,
                            plot = omdbDetail.plot,
                            rating = omdbDetail.imdbRating,
                            votes = omdbDetail.imdbVotes,
                            rt = omdbDetail.rottenTomatoesScore,
                            genre = omdbDetail.genre,
                            runtime = omdbDetail.runtime,
                            director = omdbDetail.director,
                            actors = omdbDetail.actors,
                            rated = omdbDetail.rated,
                            awards = omdbDetail.awards,
                            img = omdbDetail.poster?.ifEmpty { bestImg } ?: bestImg
                        )
                        imdbCache[cacheKey] = info
                        return@withContext info
                    }
                }

                val basic = ImdbInfo(
                    id = bestId,
                    title = bestTitle,
                    year = bestYear,
                    img = bestImg
                )
                imdbCache[cacheKey] = basic
                return@withContext basic
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun translateToSpanish(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=es&dt=t&q=$encoded"
            val resp = omdbApi.translateText(url).string()
            val jsonArr = JSONArray(resp)
            val segments = jsonArr.optJSONArray(0) ?: return@withContext null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                sb.append(seg.optString(0))
            }
            sb.toString().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun buildLiveStreamUrl(streamId: String, ext: String): String {
        val extension = if (ext.lowercase() == "m3u8") "m3u8" else "ts"
        return "$currentBaseUrl/live/$currentUser/$currentPass/$streamId.$extension"
    }

    fun buildVodStreamUrl(streamId: String, ext: String): String {
        return "$currentBaseUrl/movie/$currentUser/$currentPass/$streamId.$ext"
    }
}
