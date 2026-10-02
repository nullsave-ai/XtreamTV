@file:OptIn(UnstableApi::class)

package com.xtreamtv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xtreamtv.data.XtreamRepository
import com.xtreamtv.domain.EpgEntry
import com.xtreamtv.domain.LiveChannel
import com.xtreamtv.domain.nowNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Zapping engine: one ExoPlayer, instant channel switching, format fallback and auto-retry. */
class LiveController(
    ctx: Context,
    private val repo: XtreamRepository,
    private val scope: CoroutineScope,
    private val preferredFormat: () -> String,
) {
    enum class Status { Idle, Loading, Buffering, Playing, Error }

    val player: ExoPlayer = PlayerFactory.create(ctx, live = true)

    var current by mutableStateOf<LiveChannel?>(null); private set
    var status by mutableStateOf(Status.Idle); private set
    var epg by mutableStateOf<List<EpgEntry>>(emptyList()); private set

    /** Title of the programme airing now, per channel id (only when the server provided EPG data). */
    val epgNow = mutableStateMapOf<Int, String>()

    private var format = "m3u8"
    private var triedAlt = false
    private var retries = 0
    private var epgJob: Job? = null
    private var errJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> if (current != null && status != Status.Error) status = Status.Buffering
                Player.STATE_READY -> { status = Status.Playing; retries = 0 }
                else -> {}
            }
        }
        override fun onPlayerError(error: PlaybackException) = handleError()
    }

    init { player.addListener(listener) }

    fun play(ch: LiveChannel) {
        errJob?.cancel(); epgJob?.cancel()
        current = ch; triedAlt = false; retries = 0
        format = preferredFormat(); status = Status.Loading
        load(ch, format)
        epg = repo.peekEpg(ch) ?: emptyList()
        epgJob = scope.launch {
            val list = runCatching { repo.epg(ch) }.getOrDefault(emptyList())
            epg = list
            list.nowNext(System.currentTimeMillis() / 1000).first?.let { epgNow[ch.id] = it.title }
        }
    }

    private fun load(ch: LiveChannel, fmt: String) {
        player.setMediaItem(MediaItem.fromUri(repo.liveUrl(ch, fmt)))
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError() {
        val ch = current ?: return
        errJob?.cancel()
        errJob = scope.launch {
            if (!triedAlt) {
                triedAlt = true
                format = if (format == "ts") "m3u8" else "ts"
                load(ch, format)
            } else if (retries < 2) {
                retries++; status = Status.Loading
                delay(1_200)
                load(ch, format)
            } else status = Status.Error
        }
    }

    fun retry() { current?.let { play(it) } }

    fun prefetchEpg(ch: LiveChannel) {
        if (epgNow.containsKey(ch.id)) return
        scope.launch {
            val list = runCatching { repo.epg(ch) }.getOrDefault(emptyList())
            list.nowNext(System.currentTimeMillis() / 1000).first?.let { epgNow[ch.id] = it.title }
        }
    }

    suspend fun epgFor(ch: LiveChannel): List<EpgEntry> = runCatching { repo.epg(ch) }.getOrDefault(emptyList())

    fun pause() { player.pause() }
    fun resume() { if (current != null && status != Status.Error) player.play() }

    fun release() {
        errJob?.cancel(); epgJob?.cancel()
        player.removeListener(listener)
        player.release()
    }
}
