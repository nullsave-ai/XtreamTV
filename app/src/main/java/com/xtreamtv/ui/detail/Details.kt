package com.xtreamtv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.UiState
import com.xtreamtv.core.Fmt
import com.xtreamtv.core.loadState
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route

@Composable
private fun Backdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        Img(image, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xE606080C)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x9906080C), 1f to Color(0xF206080C))))
    }
}

@Composable
private fun MetaChips(items: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.u), verticalAlignment = Alignment.CenterVertically) {
        items.filterNotNull().forEach {
            UText(it, 12, FontWeight.SemiBold, C.text, Modifier.clip(RoundedCornerShape(50)).background(C.glass).padding(horizontal = 12.u, vertical = 5.u))
        }
    }
}

@Composable
fun MovieDetailScreen(vm: AppViewModel, nav: Nav, id: Int) {
    val moviesState by vm.movies.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val cont by vm.continueWatching.collectAsStateWithLifecycle()
    val movie = (moviesState as? UiState.Success)?.data?.byId?.get(id)
    val (infoState, _) = loadState(id) { vm.repo.movieInfo(id) }
    val info = (infoState as? UiState.Success)?.data
    val fr = remember { FocusRequester() }
    RequestFocusOnce(fr, movie != null)

    if (movie == null) {
        StateHost(moviesState, { vm.loadMovies(true) }, emptyText = "المحتوى غير متاح") { EmptyMessage("المحتوى غير متاح") }
        return
    }
    val saved = cont.firstOrNull { it.key == movie.key }
    val isFav = movie.key in favorites
    val poster = info?.poster ?: movie.poster

    fun play(start: Long) = nav.go(Route.Player(PlayRequest("movie", movie.id, movie.name, poster,
        listOf(PlayItem(movie.name, null, vm.repo.movieUrl(movie.id, movie.ext), null, start)))))

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.backdrop ?: poster)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.u, vertical = 36.u), horizontalArrangement = Arrangement.spacedBy(30.u)) {
            Img(poster, Modifier.width(172.u).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.u)))
            Column(Modifier.weight(1f)) {
                UText(movie.name, 30, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(10.u))
                MetaChips(listOf(
                    info?.year ?: movie.year,
                    (info?.rating ?: movie.rating)?.let { "★ " + String.format("%.1f", it) },
                    info?.duration, info?.genre, info?.country,
                ))
                Spacer(Modifier.height(14.u))
                when {
                    !info?.plot.isNullOrBlank() -> UText(info?.plot ?: "", 14, color = C.text2, maxLines = 6)
                    infoState is UiState.Loading -> UText("جارٍ تحميل التفاصيل…", 13, color = C.text3)
                    else -> UText("لا يوجد وصف متاح لهذا الفيلم.", 13, color = C.text3)
                }
                Spacer(Modifier.height(10.u))
                if (!info?.director.isNullOrBlank()) UText("الإخراج: ${info?.director}", 13, color = C.text2)
                if (!info?.cast.isNullOrBlank()) UText("البطولة: ${info?.cast}", 13, color = C.text2, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                    if (saved != null && saved.positionMs > 5_000) {
                        TvButton("متابعة (${Fmt.duration(saved.positionMs)})", { play(saved.positionMs) }, icon = Ic.Play, primary = true, focusRequester = fr)
                        TvButton("من البداية", { play(0) })
                    } else TvButton("تشغيل", { play(0) }, icon = Ic.Play, primary = true, focusRequester = fr)
                    TvButton(if (isFav) "إزالة من المفضلة" else "أضف إلى المفضلة", { vm.toggleFavorite(movie.key) }, icon = if (isFav) Ic.HeartFill else Ic.Heart)
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(vm: AppViewModel, nav: Nav, id: Int) {
    val seriesState by vm.series.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val cont by vm.continueWatching.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val s = (seriesState as? UiState.Success)?.data?.byId?.get(id)
    val (infoState, retry) = loadState(id) { vm.repo.seriesInfo(id) }
    val fr = remember { FocusRequester() }
    RequestFocusOnce(fr, infoState is UiState.Success)

    val name = s?.name ?: (infoState as? UiState.Success)?.data?.name ?: ""
    val key = "series:$id"

    StateHost(infoState, retry, emptyText = "لا توجد حلقات") { info ->
        if (info.episodes.isEmpty()) { EmptyMessage("لا توجد حلقات لهذا المسلسل"); return@StateHost }
        val saved = cont.firstOrNull { it.key == key }
        val savedEp = saved?.episodeId?.let { eid -> info.episodes.firstOrNull { it.id == eid } }
        var season by remember { mutableIntStateOf(savedEp?.season ?: info.seasons.first()) }
        val eps = remember(info, season) { info.episodes.filter { it.season == season } }
        val isFav = key in favorites
        val poster = info.cover ?: s?.cover

        fun playlist() = info.episodes.map { ep ->
            PlayItem(name, "م${ep.season} · ح${ep.number} — ${ep.title}", vm.repo.episodeUrl(ep.id, ep.ext), ep.id)
        }
        fun play(ep: Episode, start: Long) {
            val list = playlist().toMutableList()
            val idx = info.episodes.indexOfFirst { it.id == ep.id }
            list[idx] = list[idx].copy(startMs = start)
            nav.go(Route.Player(PlayRequest("series", id, name, poster, list, idx)))
        }

        Box(Modifier.fillMaxSize()) {
            Backdrop(info.backdrop ?: poster)
            Column(Modifier.fillMaxSize().padding(horizontal = 44.u, vertical = 28.u)) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.u)) {
                    Img(poster, Modifier.width(104.u).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.u)))
                    Column(Modifier.weight(1f)) {
                        UText(name, 26, FontWeight.Bold, maxLines = 2)
                        Spacer(Modifier.height(6.u))
                        MetaChips(listOf(info.year ?: s?.year, (info.rating ?: s?.rating)?.let { "★ " + String.format("%.1f", it) }, info.genre, "${info.seasons.size} مواسم"))
                        Spacer(Modifier.height(8.u))
                        val plot = info.plot ?: s?.plot
                        if (!plot.isNullOrBlank()) UText(plot, 13, color = C.text2, maxLines = 3)
                        Spacer(Modifier.height(10.u))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                            if (savedEp != null) TvButton("متابعة: م${savedEp.season} ح${savedEp.number}", { play(savedEp, saved?.positionMs ?: 0L) }, icon = Ic.Play, primary = true, focusRequester = fr)
                            else TvButton("تشغيل الحلقة الأولى", { play(info.episodes.first(), 0) }, icon = Ic.Play, primary = true, focusRequester = fr)
                            TvButton(if (isFav) "إزالة من المفضلة" else "أضف إلى المفضلة", { vm.toggleFavorite(key) }, icon = if (isFav) Ic.HeartFill else Ic.Heart)
                        }
                    }
                }
                Spacer(Modifier.height(14.u))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                    items(info.seasons, key = { it }) { n -> Chip("الموسم $n", n == season, { season = n }, onFocus = { season = n }) }
                }
                Spacer(Modifier.height(10.u))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.u), contentPadding = PaddingValues(vertical = 6.u)) {
                    items(eps, key = { it.id }) { ep ->
                        val done = ep.id in watched
                        val frac = if (saved != null && saved.episodeId == ep.id) saved.fraction else 0f
                        FocusBox({ play(ep, if (saved != null && saved.episodeId == ep.id) saved.positionMs else 0L) },
                            Modifier.fillMaxWidth().height(72.u), shape = RoundedCornerShape(12.u), focusScale = 1.02f, background = C.glass) {
                            Row(Modifier.fillMaxSize().padding(8.u), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(112.u).fillMaxHeight().clip(RoundedCornerShape(8.u))) {
                                    Img(ep.image ?: poster, Modifier.fillMaxSize())
                                    if (frac > 0f) ProgressBar(frac, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.u))
                                }
                                Spacer(Modifier.width(14.u))
                                Column(Modifier.weight(1f)) {
                                    UText("${ep.number}. ${ep.title}", 14, FontWeight.SemiBold)
                                    if (!ep.plot.isNullOrBlank()) UText(ep.plot, 12, color = C.text3, maxLines = 2)
                                }
                                Spacer(Modifier.width(10.u))
                                ep.durationSecs?.takeIf { it > 0 }?.let { UText("${it / 60} د", 12, color = C.text2) }
                                if (done) { Spacer(Modifier.width(10.u)); Icon(Ic.Check, 18, C.ok) }
                            }
                        }
                    }
                }
            }
        }
    }
}
