@file:OptIn(UnstableApi::class)

package com.xtreamtv.ui.player

import android.os.SystemClock
import android.view.KeyEvent as AKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.xtreamtv.core.Fmt
import com.xtreamtv.design.*
import com.xtreamtv.domain.ContinueItem
import com.xtreamtv.domain.PlayRequest
import com.xtreamtv.player.PlayerFactory
import com.xtreamtv.player.PlayerSurface
import com.xtreamtv.player.TrackOption
import com.xtreamtv.player.TrackOptions
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.nav.BackHook
import com.xtreamtv.ui.nav.Nav
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: AppViewModel, nav: Nav, req: PlayRequest) {
    val ctx = LocalContext.current
    val player = remember { PlayerFactory.create(ctx, live = false) }
    var index by remember { mutableIntStateOf(req.index) }
    val item = req.items[index]

    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var trackDialog by remember { mutableStateOf<Int?>(null) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var miniTick by remember { mutableIntStateOf(0) }
    var miniVisible by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun saveProgress() {
        val d = player.duration
        val p = player.currentPosition
        if (d <= 0 || p < 5_000) return
        val it = req.items[index]
        val key = "${req.kind}:${req.contentId}"
        if (p > d * 0.95) {
            it.episodeId?.let(vm::markWatched)
            if (req.kind == "movie") vm.removeContinue(key)
        } else {
            vm.saveProgress(ContinueItem(key, req.kind, req.contentId, req.contentTitle, req.poster, p, d, it.episodeId,
                it.url.substringAfterLast('.', "mp4"), it.subtitle, System.currentTimeMillis()))
        }
    }
    val saveNow by rememberUpdatedState(::saveProgress)

    LaunchedEffect(index, attempt) {
        error = null; buffering = true
        player.setMediaItem(MediaItem.fromUri(item.url), item.startMs.coerceAtLeast(0))
        player.prepare(); player.playWhenReady = true
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    req.items[index].episodeId?.let(vm::markWatched)
                    if (index < req.items.lastIndex) index++ else { vm.removeContinue("${req.kind}:${req.contentId}"); nav.back() }
                }
            }
            override fun onPlayerError(e: PlaybackException) { error = "تعذر تشغيل هذا المحتوى" }
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
        }
        player.addListener(l)
        onDispose { saveNow(); player.removeListener(l); player.release() }
    }

    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }
    LaunchedEffect(index) { while (true) { delay(10_000); saveNow() } }

    // Controls: auto-hide while playing; focus handling
    LaunchedEffect(controls, interaction, playing, trackDialog) {
        if (controls && playing && trackDialog == null) { delay(4_500); controls = false }
    }
    LaunchedEffect(controls) {
        repeat(10) {
            withFrameNanos { }
            if ((if (controls) playFocus else rootFocus).tryFocus()) return@LaunchedEffect
            delay(40)
        }
    }
    LaunchedEffect(miniTick) { if (miniTick > 0) { miniVisible = true; delay(1_600); miniVisible = false } }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceAtLeast(0)
        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
        pos = player.currentPosition
    }
    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    BackHook { if (controls && playing) { controls = false; true } else false }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction = SystemClock.uptimeMillis()
                when (e.nativeKeyEvent.keyCode) {
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); controls = true; true }
                    AKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                    AKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); controls = true; true }
                    AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); miniTick++; true }
                    AKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000); miniTick++; true }
                    AKeyEvent.KEYCODE_MEDIA_NEXT -> { if (index < req.items.lastIndex) index++; true }
                    AKeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (index > 0) index--; true }
                    else -> if (controls) false else when (e.nativeKeyEvent.keyCode) {
                        AKeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000 * (1 + minOf(e.nativeKeyEvent.repeatCount / 3, 6))); miniTick++; true }
                        AKeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000 * (1 + minOf(e.nativeKeyEvent.repeatCount / 3, 6))); miniTick++; true }
                        AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER -> { togglePlay(); controls = true; true }
                        AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> { controls = true; true }
                        else -> false
                    }
                }
            },
        ) {
            PlayerSurface(player, Modifier.fillMaxSize())
            Box(Modifier.size(1.dp).focusRequester(rootFocus).focusable())

            if (buffering && error == null) Box(Modifier.align(Alignment.Center)) { Spinner(48) }
            error?.let { msg ->
                Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.u)).background(C.scrim).padding(24.u), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.Info, 36, C.danger); Spacer(Modifier.height(8.u))
                    UText(msg, 18, FontWeight.Bold); Spacer(Modifier.height(14.u))
                    TvButton("إعادة المحاولة", { attempt++ }, icon = Ic.Refresh, primary = true)
                }
            }

            if (miniVisible && !controls) MiniBar(pos, dur)

            if (controls) {
                Controls(
                    title = item.title, subtitle = item.subtitle, pos = pos, dur = dur, playing = playing,
                    hasPrev = index > 0, hasNext = index < req.items.lastIndex, playFocus = playFocus,
                    hasAudio = remember(tracksVersion) { TrackOptions.hasChoices(player, C.TRACK_TYPE_AUDIO) },
                    hasSubs = remember(tracksVersion) { TrackOptions.hasChoices(player, C.TRACK_TYPE_TEXT) },
                    hasQuality = remember(tracksVersion) { TrackOptions.hasChoices(player, C.TRACK_TYPE_VIDEO) },
                    onPlayPause = ::togglePlay,
                    onSeek = { seekBy(it) },
                    onPrev = { if (index > 0) index-- },
                    onNext = { if (index < req.items.lastIndex) index++ },
                    onTracks = { trackDialog = it },
                )
            }
        }

        trackDialog?.let { type ->
            val options = remember(type, tracksVersion) { TrackOptions.list(player, type) }
            TrackDialog(
                title = when (type) { C.TRACK_TYPE_AUDIO -> "الصوت"; C.TRACK_TYPE_TEXT -> "الترجمة"; else -> "الجودة" },
                options = options,
                onPick = { TrackOptions.apply(player, type, it); trackDialog = null },
                onDismiss = { trackDialog = null },
            )
        }
    }
}

@Composable
private fun MiniBar(pos: Long, dur: Long) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Column(Modifier.padding(horizontal = 56.u, vertical = 36.u)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UText(Fmt.duration(pos), 18, FontWeight.Bold, C.accent)
                UText(if (dur > 0) Fmt.duration(dur) else "--:--", 14, color = C.text2)
            }
            Spacer(Modifier.height(6.u))
            ProgressBar(if (dur > 0) pos.toFloat() / dur else 0f, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Controls(
    title: String, subtitle: String?, pos: Long, dur: Long, playing: Boolean, hasPrev: Boolean, hasNext: Boolean,
    playFocus: FocusRequester, hasAudio: Boolean, hasSubs: Boolean, hasQuality: Boolean,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onTracks: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(110.u).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
        Column(Modifier.align(Alignment.TopStart).padding(horizontal = 48.u, vertical = 28.u)) {
            UText(title, 22, FontWeight.Bold, maxLines = 1)
            if (subtitle != null) UText(subtitle, 14, color = C.text2)
        }
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 48.u, vertical = 26.u),
        ) {
            SeekBar(pos, dur, onSeek)
            Spacer(Modifier.height(10.u))
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(12.u), verticalAlignment = Alignment.CenterVertically) {
                    if (hasPrev) IconButton(Ic.Prev, onPrev)
                    IconButton(Ic.Rew, { onSeek(-10_000) })
                    IconButton(if (playing) Ic.Pause else Ic.Play, onPlayPause, size = 56, focusRequester = playFocus, primary = true)
                    IconButton(Ic.Fwd, { onSeek(10_000) })
                    if (hasNext) IconButton(Ic.Next, onNext)
                }
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.u)) {
                    if (hasAudio) IconButton(Ic.Audio, { onTracks(C.TRACK_TYPE_AUDIO) }, size = 40)
                    if (hasSubs) IconButton(Ic.Subs, { onTracks(C.TRACK_TYPE_TEXT) }, size = 40)
                    if (hasQuality) IconButton(Ic.Quality, { onTracks(C.TRACK_TYPE_VIDEO) }, size = 40)
                }
            }
        }
    }
}

/** Focusable seek bar: Left/Right seek; holding the key accelerates. */
@Composable
private fun SeekBar(pos: Long, dur: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UText(Fmt.duration(pos), 13, FontWeight.SemiBold, modifier = Modifier.width(64.u))
        Box(
            Modifier.weight(1f).height(26.u)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = 10_000L * (1 + minOf(e.nativeKeyEvent.repeatCount / 3, 6))
                    when (e.key) {
                        Key.DirectionLeft -> { onSeek(-step); true }
                        Key.DirectionRight -> { onSeek(step); true }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val h = if (focused) 8.u else 5.u
            Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(C.accent))
            }
        }
        UText(if (dur > 0) Fmt.duration(dur) else "--:--", 13, color = C.text2, modifier = Modifier.width(64.u), align = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun TrackDialog(title: String, options: List<TrackOption>, onPick: (TrackOption) -> Unit, onDismiss: () -> Unit) {
    TvDialog(onDismiss, width = 420) {
        UText(title, 20, FontWeight.Bold)
        Spacer(Modifier.height(12.u))
        if (options.isEmpty()) { UText("لا توجد خيارات متاحة", 14, color = C.text2); return@TvDialog }
        val fr = remember { FocusRequester() }
        RequestFocusOnce(fr)
        val selectedIdx = options.indexOfFirst { it.selected }.coerceAtLeast(0)
        LazyColumn(Modifier.heightIn(max = 300.u), verticalArrangement = Arrangement.spacedBy(6.u)) {
            items(options.size) { i ->
                val o = options[i]
                FocusBox({ onPick(o) }, Modifier.fillMaxWidth().height(42.u), shape = RoundedCornerShape(10.u), focusScale = 1.02f,
                    background = C.glass, focusRequester = if (i == selectedIdx) fr else null) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.u), verticalAlignment = Alignment.CenterVertically) {
                        UText(o.label, 15, if (o.selected) FontWeight.Bold else FontWeight.Medium, if (o.selected) C.accent else C.text, Modifier.weight(1f))
                        if (o.selected) Icon(Ic.Check, 18, C.accent)
                    }
                }
            }
        }
    }
}
