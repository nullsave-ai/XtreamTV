package com.xtreamtv.data

import android.util.Base64
import com.xtreamtv.domain.*
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.str(k: String): String? {
    if (!has(k) || isNull(k)) return null
    val s = opt(k)?.toString()?.trim()
    return if (s.isNullOrEmpty() || s == "null") null else s
}
fun JSONObject.int(k: String): Int? = str(k)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
fun JSONObject.long(k: String): Long? = str(k)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
fun JSONObject.dbl(k: String): Double? = str(k)?.toDoubleOrNull()
fun JSONObject.firstStr(k: String): String? = when (val v = opt(k)) {
    is JSONArray -> if (v.length() > 0) v.optString(0).takeIf { it.isNotBlank() } else null
    is String -> v.takeIf { it.isNotBlank() }
    else -> null
}
fun JSONArray.objs(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }

fun normalizeSearch(s: String): String {
    val sb = StringBuilder(s.length)
    for (raw in s) {
        val ch = raw.lowercaseChar()
        when {
            ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' -> {}
            ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
            ch == 'ى' || ch == 'ئ' -> sb.append('ي')
            ch == 'ة' -> sb.append('ه')
            ch == 'ؤ' -> sb.append('و')
            else -> sb.append(ch)
        }
    }
    return sb.toString().trim()
}

private val YEAR = Regex("(19|20)\\d{2}")
fun yearOf(s: String?): String? = s?.let { YEAR.find(it)?.value }

object Parsers {
    private fun arr(json: String): JSONArray {
        val t = json.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    fun categories(json: String): List<Category> = arr(json).objs().mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id, o.str("category_name") ?: id)
    }

    fun live(json: String): List<LiveChannel> = arr(json).objs().mapIndexedNotNull { i, o ->
        val id = o.int("stream_id") ?: return@mapIndexedNotNull null
        val name = o.str("name") ?: ""
        LiveChannel(id, o.int("num") ?: (i + 1), name, o.str("stream_icon"), o.str("category_id") ?: "0",
            o.str("epg_channel_id"), normalizeSearch(name))
    }.sortedBy { it.num }

    fun movies(json: String): List<Movie> = arr(json).objs().mapNotNull { o ->
        val id = o.int("stream_id") ?: return@mapNotNull null
        val name = o.str("name") ?: return@mapNotNull null
        val r5 = o.dbl("rating_5based") ?: o.dbl("rating")?.div(2)
        Movie(id, name, o.str("stream_icon"), o.str("category_id") ?: "0", r5?.takeIf { it > 0 },
            o.long("added") ?: 0L, o.str("container_extension") ?: "mp4", yearOf(name), normalizeSearch(name))
    }

    fun series(json: String): List<Series> = arr(json).objs().mapNotNull { o ->
        val id = o.int("series_id") ?: return@mapNotNull null
        val name = o.str("name") ?: return@mapNotNull null
        val r5 = o.dbl("rating_5based") ?: o.dbl("rating")?.div(2)
        Series(id, name, o.str("cover"), o.str("category_id") ?: "0", r5?.takeIf { it > 0 },
            o.long("last_modified") ?: 0L, o.str("plot"), o.str("genre"),
            yearOf(o.str("releaseDate") ?: o.str("release_date") ?: name), normalizeSearch(name))
    }

    fun movieInfo(json: String): MovieInfo {
        val o = JSONObject(json)
        val i = o.optJSONObject("info") ?: JSONObject()
        return MovieInfo(
            plot = i.str("plot") ?: i.str("description"), genre = i.str("genre"),
            year = yearOf(i.str("releasedate") ?: i.str("release_date") ?: i.str("year")),
            duration = i.str("duration"), rating = i.dbl("rating")?.takeIf { it > 0 },
            cast = i.str("cast") ?: i.str("actors"), director = i.str("director"),
            backdrop = i.firstStr("backdrop_path"), poster = i.str("movie_image") ?: i.str("cover_big"),
            country = i.str("country"),
        )
    }

    fun seriesInfo(json: String, id: Int): SeriesInfo {
        val o = JSONObject(json)
        val i = o.optJSONObject("info") ?: JSONObject()
        val eps = mutableListOf<Episode>()
        fun addSeason(seasonKey: Int?, a: JSONArray) {
            for (e in a.objs()) {
                val epId = e.str("id") ?: continue
                val inf = e.optJSONObject("info")
                eps += Episode(
                    epId, e.int("season") ?: seasonKey ?: 1, e.int("episode_num") ?: (eps.size + 1),
                    e.str("title") ?: "الحلقة", e.str("container_extension") ?: "mp4",
                    inf?.str("plot"), inf?.str("movie_image"), inf?.int("duration_secs"),
                )
            }
        }
        when (val raw = o.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { k -> raw.optJSONArray(k)?.let { addSeason(k.toIntOrNull(), it) } }
            is JSONArray -> for (n in 0 until raw.length()) raw.optJSONArray(n)?.let { addSeason(n + 1, it) }
        }
        val sorted = eps.sortedWith(compareBy({ it.season }, { it.number }))
        return SeriesInfo(
            id, i.str("name"), i.str("cover"), i.str("plot"), i.str("genre"),
            yearOf(i.str("releaseDate") ?: i.str("release_date")), i.dbl("rating_5based") ?: i.dbl("rating")?.div(2),
            i.str("cast"), i.str("director"), i.firstStr("backdrop_path"),
            sorted.map { it.season }.distinct(), sorted,
        )
    }

    fun epg(json: String): List<EpgEntry> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return o.optJSONArray("epg_listings")?.objs()?.mapNotNull { e ->
            val s = e.long("start_timestamp") ?: return@mapNotNull null
            val t = e.long("stop_timestamp") ?: e.long("end_timestamp") ?: return@mapNotNull null
            EpgEntry(decode(e.str("title") ?: ""), e.str("description")?.let(::decode), s, t)
        }?.sortedBy { it.start } ?: emptyList()
    }

    fun accountInfo(json: String): AccountInfo? {
        val u = JSONObject(json).optJSONObject("user_info") ?: return null
        return AccountInfo(u.str("status"), u.long("exp_date"), u.int("max_connections"), u.int("active_cons"))
    }

    private val B64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
    private fun decode(s: String): String {
        val t = s.trim()
        if (t.isEmpty() || t.length % 4 != 0 || !B64.matches(t)) return s
        return runCatching { String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(s)
    }
}
