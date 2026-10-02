package com.xtreamtv.ui.search

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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.UiState
import com.xtreamtv.data.normalizeSearch
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.common.ChannelCard
import com.xtreamtv.ui.common.PosterCard
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class CatHit(val kind: String, val cat: Category, val count: Int)
private class Results(
    val channels: List<LiveChannel> = emptyList(), val movies: List<Movie> = emptyList(), val series: List<Series> = emptyList(),
    val episodes: List<Pair<SeriesInfo, Episode>> = emptyList(), val cats: List<CatHit> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && episodes.isEmpty() && cats.isEmpty()
}

private val AR = listOf("ضصثقفغعهخح", "جدشسيبلاتن", "مكطئءؤرىةو", "زظذ")
private val EN = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private const val NUM = "1234567890"

private fun runSearch(vm: AppViewModel, q: String): Results {
    val n = normalizeSearch(q)
    if (n.isBlank()) return Results()
    val tokens = n.split(' ').filter { it.isNotBlank() }
    fun hit(s: String) = tokens.all { s.contains(it) }
    val live = (vm.live.value as? UiState.Success)?.data
    val movies = (vm.movies.value as? UiState.Success)?.data
    val series = (vm.series.value as? UiState.Success)?.data
    val cats = buildList {
        live?.nonEmptyCategories?.filter { hit(normalizeSearch(it.name)) }?.forEach { add(CatHit("live", it, live.count(it.id))) }
        movies?.nonEmptyCategories?.filter { hit(normalizeSearch(it.name)) }?.forEach { add(CatHit("movie", it, movies.count(it.id))) }
        series?.nonEmptyCategories?.filter { hit(normalizeSearch(it.name)) }?.forEach { add(CatHit("series", it, series.count(it.id))) }
    }.take(20)
    return Results(
        channels = live?.items?.filter { hit(it.search) }?.sortedBy { it.search.indexOf(tokens[0]) }?.take(40).orEmpty(),
        movies = movies?.items?.filter { hit(it.search) }?.sortedBy { it.search.indexOf(tokens[0]) }?.take(40).orEmpty(),
        series = series?.items?.filter { hit(it.search) }?.sortedBy { it.search.indexOf(tokens[0]) }?.take(40).orEmpty(),
        episodes = vm.repo.searchEpisodes(n),
        cats = cats,
    )
}

@Composable
fun SearchScreen(vm: AppViewModel, nav: Nav) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(Results()) }
    var arabic by remember { mutableStateOf(true) }
    val firstKey = remember { FocusRequester() }
    RequestFocusOnce(firstKey)

    LaunchedEffect(query) {
        if (query.isBlank()) { results = Results(); return@LaunchedEffect }
        delay(250)
        results = withContext(Dispatchers.Default) { runSearch(vm, query) }
    }

    Row(
        Modifier.fillMaxSize().onKeyEvent { e -> // physical keyboards
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            val cp = e.utf16CodePoint
            when {
                e.key == Key.Backspace -> { query = query.dropLast(1); true }
                cp > 31 && cp != 127 && !e.isCtrlPressed -> { query += String(Character.toChars(cp)); true }
                else -> false
            }
        }.padding(top = 20.u),
    ) {
        // Keyboard column (start side)
        Column(Modifier.width(352.u).fillMaxHeight().padding(horizontal = 14.u)) {
            Row(
                Modifier.fillMaxWidth().height(46.u).clip(RoundedCornerShape(12.u)).background(C.glass).padding(horizontal = 14.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Search, 20, C.accent)
                Spacer(Modifier.width(10.u))
                if (query.isEmpty()) UText("اكتب للبحث…", 16, color = C.text3, modifier = Modifier.weight(1f))
                else UText(query, 18, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.u))
            CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
                    (if (arabic) AR else EN).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.u)) {
                            row.forEachIndexed { c, ch ->
                                KbKey(ch.toString(), Modifier.size(width = 29.u, height = 38.u), if (r == 0 && c == 0) firstKey else null) { query += ch }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.u)) {
                        NUM.forEach { ch -> KbKey(ch.toString(), Modifier.size(width = 29.u, height = 38.u), null) { query += ch } }
                    }
                }
            }
            Spacer(Modifier.height(8.u))
            Row(horizontalArrangement = Arrangement.spacedBy(6.u)) {
                KbKey("مسافة", Modifier.weight(1.4f).height(38.u), null) { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
                KbKey("حذف", Modifier.weight(1f).height(38.u), null) { query = query.dropLast(1) }
                KbKey("مسح", Modifier.weight(1f).height(38.u), null) { query = "" }
                KbKey(if (arabic) "EN" else "ع", Modifier.weight(0.8f).height(38.u), null) { arabic = !arabic }
            }
        }

        // Results
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val r = results
            when {
                query.isBlank() -> EmptyMessage("ابحث في القنوات والأفلام والمسلسلات والحلقات والتصنيفات", icon = Ic.Search)
                r.isEmpty -> EmptyMessage("لا توجد نتائج لـ \"$query\"", icon = Ic.Search)
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.u), verticalArrangement = Arrangement.spacedBy(12.u)) {
                    if (r.channels.isNotEmpty()) item { Block("القنوات (${r.channels.size})") {
                        items(r.channels, key = { it.id }) { ch ->
                            ChannelCard(ch, Modifier.width(128.u), favorite = ch.key in favorites, onLongClick = { vm.toggleFavorite(ch.key) }) { nav.go(Route.Live(ch.id)) }
                        }
                    } }
                    if (r.movies.isNotEmpty()) item { Block("الأفلام (${r.movies.size})") {
                        items(r.movies, key = { it.id }) { m ->
                            PosterCard(m.name, m.poster, Modifier.width(100.u), meta = m.year, favorite = m.key in favorites, onLongClick = { vm.toggleFavorite(m.key) }) { nav.go(Route.MovieDetail(m.id)) }
                        }
                    } }
                    if (r.series.isNotEmpty()) item { Block("المسلسلات (${r.series.size})") {
                        items(r.series, key = { it.id }) { s ->
                            PosterCard(s.name, s.cover, Modifier.width(100.u), meta = s.year, favorite = s.key in favorites, onLongClick = { vm.toggleFavorite(s.key) }) { nav.go(Route.SeriesDetail(s.id)) }
                        }
                    } }
                    if (r.episodes.isNotEmpty()) item { Block("الحلقات (${r.episodes.size})") {
                        items(r.episodes, key = { it.second.id }) { (s, e) ->
                            FocusBox({ nav.go(Route.SeriesDetail(s.id)) }, Modifier.width(230.u).height(62.u), shape = RoundedCornerShape(10.u), focusScale = 1.04f, background = C.glass) {
                                Column(Modifier.fillMaxSize().padding(horizontal = 12.u, vertical = 8.u), verticalArrangement = Arrangement.Center) {
                                    UText(e.title, 13, FontWeight.SemiBold)
                                    UText("${s.name ?: ""} • م${e.season} ح${e.number}", 11, color = C.text3)
                                }
                            }
                        }
                    } }
                    if (r.cats.isNotEmpty()) item { Block("التصنيفات (${r.cats.size})") {
                        items(r.cats, key = { it.kind + it.cat.id }) { h ->
                            val kind = when (h.kind) { "live" -> "قنوات"; "movie" -> "أفلام"; else -> "مسلسلات" }
                            Chip("${h.cat.name} • $kind ${h.count}", false, {
                                when (h.kind) {
                                    "live" -> nav.tab(Route.Live(catId = h.cat.id))
                                    "movie" -> nav.tab(Route.Movies(h.cat.id))
                                    else -> nav.tab(Route.Shows(h.cat.id))
                                }
                            })
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, row: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        UText(title, 16, FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.u))
        Spacer(Modifier.height(6.u))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.u, vertical = 6.u), horizontalArrangement = Arrangement.spacedBy(12.u), content = row)
    }
}

@Composable
private fun KbKey(label: String, modifier: Modifier, fr: FocusRequester?, onClick: () -> Unit) {
    FocusBox(onClick, modifier, shape = RoundedCornerShape(8.u), focusScale = 1.12f, background = C.glass, focusRequester = fr) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UText(label, if (label.length > 1) 12 else 16, FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
