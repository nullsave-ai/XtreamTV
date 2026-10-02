package com.xtreamtv.data

import com.xtreamtv.core.ErrorKind
import com.xtreamtv.core.XtreamError
import com.xtreamtv.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JsonDiskCache(private val dir: File) {
    init { dir.mkdirs() }
    private fun name(key: String) = key.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private fun file(key: String) = File(dir, name(key) + ".json")
    fun read(key: String): Pair<String, Long>? {
        val f = file(key)
        if (!f.exists()) return null
        return runCatching { f.readText() to (System.currentTimeMillis() - f.lastModified()) }.getOrNull()
    }
    fun write(key: String, text: String) { runCatching { file(key).writeText(text) } }
    fun clear() { dir.listFiles()?.forEach { it.delete() } }
    fun clearPrefix(prefix: String) { dir.listFiles()?.filter { it.name.startsWith(name(prefix)) }?.forEach { it.delete() } }
}

/** Single source of truth for Xtream data: memory cache -> disk cache -> network (stale disk when offline). */
class XtreamRepository(private val api: XtreamApi, private val cache: JsonDiskCache) {

    val offline = MutableStateFlow(false)

    private var account: Account? = null
    private val mem = ConcurrentHashMap<String, Any>()
    private val catalogs = ConcurrentHashMap<String, Catalog<*>>()
    private val epgMem = ConcurrentHashMap<String, Pair<Long, List<EpgEntry>>>()
    private val seriesInfos = ConcurrentHashMap<Int, SeriesInfo>()
    private val epgGate = Semaphore(3)

    private companion object {
        const val TTL = 12L * 3600_000
        const val INFO_TTL = 6L * 3600_000
        const val EPG_TTL = 120_000L
    }

    fun setAccount(a: Account?) {
        val old = account
        if (old == a) return
        if (old != null && a != null && old.id == a.id) cache.clearPrefix(a.id)
        account = a
        mem.clear(); catalogs.clear(); epgMem.clear(); seriesInfos.clear()
        offline.value = false
    }

    fun clearCache() { mem.clear(); catalogs.clear(); epgMem.clear(); seriesInfos.clear(); cache.clear() }

    fun liveUrl(ch: LiveChannel, ext: String) = api.liveUrl(requireAccount(), ch.id, ext)
    fun movieUrl(id: Int, ext: String) = api.movieUrl(requireAccount(), id, ext)
    fun episodeUrl(epId: String, ext: String) = api.episodeUrl(requireAccount(), epId, ext)
    private fun requireAccount() = account ?: throw XtreamError(ErrorKind.Auth, "لا يوجد حساب نشط")

    private fun guard(text: String) {
        val t = text.trimStart()
        if (t.isEmpty()) throw XtreamError(ErrorKind.Server, "استجابة فارغة من السيرفر")
        if (t.startsWith("{")) {
            val ui = runCatching { JSONObject(t).optJSONObject("user_info") }.getOrNull()
            if (ui != null && ui.optInt("auth", 1) == 0) throw XtreamError(ErrorKind.Auth, "بيانات الحساب غير صحيحة")
        }
    }

    private suspend fun <T : Any> parseSafe(text: String, parse: (String) -> T): T = withContext(Dispatchers.Default) {
        try { parse(text) } catch (e: JSONException) { throw XtreamError(ErrorKind.Parse, "تعذر قراءة بيانات السيرفر", e) }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String, force: Boolean, ttl: Long, action: String?,
        params: Map<String, String> = emptyMap(), parse: (String) -> T,
    ): T {
        val acc = requireAccount()
        val full = "${acc.id}_$key"
        if (!force) (mem[full] as? T)?.let { return it }
        val disk = withContext(Dispatchers.IO) { cache.read(full) }
        if (!force && disk != null && disk.second < ttl) {
            return parseSafe(disk.first, parse).also { mem[full] = it }
        }
        return try {
            val text = api.fetch(acc, action, params)
            guard(text)
            withContext(Dispatchers.IO) { cache.write(full, text) }
            offline.value = false
            parseSafe(text, parse).also { mem[full] = it }
        } catch (e: XtreamError) {
            if (disk != null && e.kind != ErrorKind.Auth) {
                offline.value = true
                parseSafe(disk.first, parse).also { mem[full] = it }
            } else throw e
        }
    }

    suspend fun authenticate(a: Account): AccountInfo {
        val text = api.fetch(a, null)
        if (text.isBlank()) throw XtreamError(ErrorKind.Server, "استجابة فارغة من السيرفر")
        val info = try { Parsers.accountInfo(text) } catch (e: JSONException) { null }
            ?: throw XtreamError(ErrorKind.Parse, "الرابط لا يشير إلى سيرفر Xtream صالح")
        val auth = JSONObject(text).optJSONObject("user_info")?.optInt("auth", 1) ?: 1
        if (auth == 0) throw XtreamError(ErrorKind.Auth, "اسم المستخدم أو كلمة المرور غير صحيحة")
        return info
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun liveCatalog(force: Boolean = false): Catalog<LiveChannel> {
        val acc = requireAccount(); val k = "${acc.id}_live"
        if (!force) (catalogs[k] as? Catalog<LiveChannel>)?.let { return it }
        return coroutineScope {
            val cats = async { cached("live_cats", force, TTL, "get_live_categories") { Parsers.categories(it) } }
            val items = async { cached("live_streams", force, TTL, "get_live_streams") { Parsers.live(it) } }
            val c = cats.await(); val i = items.await()
            withContext(Dispatchers.Default) { Catalog(c, i, { it.categoryId }, { it.id }) }
        }.also { catalogs[k] = it }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun movieCatalog(force: Boolean = false): Catalog<Movie> {
        val acc = requireAccount(); val k = "${acc.id}_movies"
        if (!force) (catalogs[k] as? Catalog<Movie>)?.let { return it }
        return coroutineScope {
            val cats = async { cached("vod_cats", force, TTL, "get_vod_categories") { Parsers.categories(it) } }
            val items = async { cached("vod_streams", force, TTL, "get_vod_streams") { Parsers.movies(it) } }
            val c = cats.await(); val i = items.await()
            withContext(Dispatchers.Default) { Catalog(c, i, { it.categoryId }, { it.id }) }
        }.also { catalogs[k] = it }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun seriesCatalog(force: Boolean = false): Catalog<Series> {
        val acc = requireAccount(); val k = "${acc.id}_series"
        if (!force) (catalogs[k] as? Catalog<Series>)?.let { return it }
        return coroutineScope {
            val cats = async { cached("series_cats", force, TTL, "get_series_categories") { Parsers.categories(it) } }
            val items = async { cached("series_list", force, TTL, "get_series") { Parsers.series(it) } }
            val c = cats.await(); val i = items.await()
            withContext(Dispatchers.Default) { Catalog(c, i, { it.categoryId }, { it.id }) }
        }.also { catalogs[k] = it }
    }

    suspend fun movieInfo(id: Int): MovieInfo =
        cached("vodinfo_$id", false, INFO_TTL, "get_vod_info", mapOf("vod_id" to id.toString())) { Parsers.movieInfo(it) }

    suspend fun seriesInfo(id: Int): SeriesInfo =
        cached("seriesinfo_$id", false, INFO_TTL, "get_series_info", mapOf("series_id" to id.toString())) {
            Parsers.seriesInfo(it, id)
        }.also { seriesInfos[id] = it }

    /** Episodes of series whose details were already opened (the API has no episode search). */
    fun searchEpisodes(normalizedQuery: String, limit: Int = 30): List<Pair<SeriesInfo, Episode>> {
        val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<SeriesInfo, Episode>>()
        for (s in seriesInfos.values) for (e in s.episodes) {
            val hay = normalizeSearch(e.title)
            if (tokens.all { hay.contains(it) }) { out += s to e; if (out.size >= limit) return out }
        }
        return out
    }

    fun peekEpg(ch: LiveChannel): List<EpgEntry>? {
        val acc = account ?: return null
        return epgMem["${acc.id}_${ch.id}"]?.takeIf { System.currentTimeMillis() - it.first < EPG_TTL }?.second
    }

    /** Short EPG for a live channel. Never throws: returns an empty list when no data. */
    suspend fun epg(ch: LiveChannel, limit: Int = 8): List<EpgEntry> {
        val acc = account ?: return emptyList()
        val k = "${acc.id}_${ch.id}"
        peekEpg(ch)?.let { return it }
        return epgGate.withPermit {
            peekEpg(ch)?.let { return@withPermit it }
            val text = try {
                api.fetch(acc, "get_short_epg", mapOf("stream_id" to ch.id.toString(), "limit" to limit.toString()))
            } catch (e: XtreamError) { return@withPermit emptyList() }
            val list = withContext(Dispatchers.Default) { runCatching { Parsers.epg(text) }.getOrDefault(emptyList()) }
            epgMem[k] = System.currentTimeMillis() to list
            list
        }
    }
}
