package com.xtreamtv.data.local

import android.content.Context
import com.xtreamtv.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class AccountStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    private fun load(): List<Account> = runCatching {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Account(o.getString("id"), o.getString("name"), o.getString("server"), o.getString("u"), o.getString("p"))
        }
    }.getOrDefault(emptyList())

    val accounts = MutableStateFlow(load())
    val activeId = MutableStateFlow(sp.getString("active", null)?.takeIf { id -> accounts.value.any { it.id == id } }
        ?: accounts.value.firstOrNull()?.id)

    private fun persist() {
        val arr = JSONArray()
        accounts.value.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("server", it.server).put("u", it.username).put("p", it.password))
        }
        sp.edit().putString("list", arr.toString()).putString("active", activeId.value).apply()
    }

    fun upsert(a: Account) {
        val list = accounts.value.toMutableList()
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list += a
        accounts.value = list
        if (activeId.value == null) activeId.value = a.id
        persist()
    }

    fun delete(id: String) {
        accounts.value = accounts.value.filterNot { it.id == id }
        if (activeId.value == id) activeId.value = accounts.value.firstOrNull()?.id
        persist()
    }

    fun setActive(id: String) { activeId.value = id; persist() }
}

class UserStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("user", Context.MODE_PRIVATE)
    private var acc: String = ""

    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val watched = MutableStateFlow<Set<String>>(emptySet())
    val continueWatching = MutableStateFlow<List<ContinueItem>>(emptyList())

    private fun readSet(name: String): Set<String> = runCatching {
        val a = JSONArray(sp.getString("${name}_$acc", "[]")); (0 until a.length()).map { a.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    private fun writeSet(name: String, s: Set<String>) { sp.edit().putString("${name}_$acc", JSONArray(s.toList()).toString()).apply() }

    private fun readContinue(): List<ContinueItem> = runCatching {
        val a = JSONArray(sp.getString("cont_$acc", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            ContinueItem(o.getString("key"), o.getString("kind"), o.getInt("id"), o.getString("title"),
                o.optString("poster").ifBlank { null }, o.getLong("pos"), o.getLong("dur"),
                o.optString("ep").ifBlank { null }, o.optString("ext").ifBlank { null },
                o.optString("sub").ifBlank { null }, o.getLong("t"))
        }
    }.getOrDefault(emptyList())

    private fun writeContinue(list: List<ContinueItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("key", it.key).put("kind", it.kind).put("id", it.id).put("title", it.title)
                .put("poster", it.poster ?: "").put("pos", it.positionMs).put("dur", it.durationMs)
                .put("ep", it.episodeId ?: "").put("ext", it.ext ?: "").put("sub", it.subtitle ?: "").put("t", it.updatedAt))
        }
        sp.edit().putString("cont_$acc", arr.toString()).apply()
    }

    fun load(accountId: String?) {
        acc = accountId ?: ""
        favorites.value = readSet("fav"); watched.value = readSet("watched"); continueWatching.value = readContinue()
    }

    fun lastLive(): Int? = sp.getInt("last_$acc", -1).takeIf { it >= 0 }
    fun setLastLive(id: Int) { sp.edit().putInt("last_$acc", id).apply() }

    fun toggleFavorite(key: String) {
        val s = favorites.value.toMutableSet()
        if (!s.add(key)) s.remove(key)
        favorites.value = s; writeSet("fav", s)
    }

    fun markWatched(epId: String) {
        val s = watched.value + epId
        watched.value = s; writeSet("watched", s)
    }

    fun saveProgress(item: ContinueItem) {
        val list = (listOf(item) + continueWatching.value.filterNot { it.key == item.key }).take(30)
        continueWatching.value = list; writeContinue(list)
    }

    fun removeContinue(key: String) {
        val list = continueWatching.value.filterNot { it.key == key }
        continueWatching.value = list; writeContinue(list)
    }
}

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(
        AppSettings(sp.getString("fmt", "m3u8") ?: "m3u8", sp.getInt("panel", 6), sp.getBoolean("resume", true))
    )
    val flow: StateFlow<AppSettings> = _flow
    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_flow.value); _flow.value = n
        sp.edit().putString("fmt", n.liveFormat).putInt("panel", n.panelTimeoutSec).putBoolean("resume", n.resumeLast).apply()
    }
}
