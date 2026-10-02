package com.xtreamtv.domain

import java.util.UUID

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val server: String,
    val username: String,
    val password: String,
)

data class AccountInfo(val status: String?, val expiry: Long?, val maxConnections: Int?, val activeConnections: Int?)

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: Int,
    val num: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val search: String,
) { val key: String get() = "live:$id" }

data class Movie(
    val id: Int,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val rating: Double?,
    val added: Long,
    val ext: String,
    val year: String?,
    val search: String,
) { val key: String get() = "movie:$id" }

data class Series(
    val id: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val rating: Double?,
    val lastModified: Long,
    val plot: String?,
    val genre: String?,
    val year: String?,
    val search: String,
) { val key: String get() = "series:$id" }

data class MovieInfo(
    val plot: String?, val genre: String?, val year: String?, val duration: String?, val rating: Double?,
    val cast: String?, val director: String?, val backdrop: String?, val poster: String?, val country: String?,
)

data class Episode(
    val id: String, val season: Int, val number: Int, val title: String, val ext: String,
    val plot: String?, val image: String?, val durationSecs: Int?,
)

data class SeriesInfo(
    val id: Int, val name: String?, val cover: String?, val plot: String?, val genre: String?, val year: String?,
    val rating: Double?, val cast: String?, val director: String?, val backdrop: String?,
    val seasons: List<Int>, val episodes: List<Episode>,
)

data class EpgEntry(val title: String, val description: String?, val start: Long, val end: Long) {
    fun progress(nowSec: Long): Float =
        if (end <= start) 0f else ((nowSec - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

fun List<EpgEntry>.nowNext(nowSec: Long): Pair<EpgEntry?, EpgEntry?> {
    val now = firstOrNull { nowSec >= it.start && nowSec < it.end }
    val next = if (now != null) firstOrNull { it.start >= now.end } else firstOrNull { it.start > nowSec }
    return now to next
}

class Catalog<T>(
    val categories: List<Category>,
    val items: List<T>,
    categoryOf: (T) -> String,
    idOf: (T) -> Int,
) {
    val byCategory: Map<String, List<T>> = items.groupBy(categoryOf)
    val byId: Map<Int, T> = items.associateBy(idOf)
    fun count(cat: String): Int = byCategory[cat]?.size ?: 0
    val nonEmptyCategories: List<Category> = categories.filter { count(it.id) > 0 }
}

data class ContinueItem(
    val key: String, val kind: String, val id: Int, val title: String, val poster: String?,
    val positionMs: Long, val durationMs: Long, val episodeId: String?, val ext: String?,
    val subtitle: String?, val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

data class PlayItem(val title: String, val subtitle: String?, val url: String, val episodeId: String?, val startMs: Long = 0L)

data class PlayRequest(
    val kind: String, // "movie" | "series"
    val contentId: Int,
    val contentTitle: String,
    val poster: String?,
    val items: List<PlayItem>,
    val index: Int = 0,
)

data class AppSettings(val liveFormat: String = "m3u8", val panelTimeoutSec: Int = 6, val resumeLast: Boolean = true)

class ScreenMemory { var cat: String = "all"; var index: Int = 0 }

fun normalizeServer(raw: String): String {
    var s = raw.trim()
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
    val cut = listOf("/player_api.php", "/get.php", "/panel_api.php", "/c/").map { s.indexOf(it) }.filter { it > 8 }.minOrNull()
    if (cut != null) s = s.substring(0, cut)
    return s.trimEnd('/')
}
