package com.xtreamtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.UiState
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.common.ChannelCard
import com.xtreamtv.ui.common.PosterCard
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route

private class Hero(val title: String, val meta: String, val body: String?, val image: String?)

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val home by vm.home.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val cont by vm.continueWatching.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hero = remember { mutableStateOf<Hero?>(null) }

    val allLoading = live is UiState.Loading && movies is UiState.Loading && series is UiState.Loading
    val allError = live is UiState.Error && movies is UiState.Error && series is UiState.Error
    if (allLoading || allError) {
        StateHost(if (allError) live else UiState.Loading, onRetry = { vm.refresh() }) { }
        return
    }

    val liveCatalog = (live as? UiState.Success)?.data
    val favChannels = remember(liveCatalog, favorites) {
        liveCatalog?.items?.filter { it.key in favorites }?.take(40).orEmpty()
    }
    val firstFocus = remember { FocusRequester() }
    RequestFocusOnce(firstFocus)

    Column(Modifier.fillMaxSize()) {
        HeroBanner(hero)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.u), verticalArrangement = Arrangement.spacedBy(14.u)) {
            item(key = "shortcuts") { Shortcuts(nav, firstFocus) }

            if (cont.isNotEmpty()) item(key = "continue") {
                Section("متابعة المشاهدة") {
                    items(cont, key = { it.key }) { c ->
                        ContinueCard(c,
                            onFocus = { hero.value = Hero(c.title, c.subtitle ?: if (c.kind == "movie") "فيلم" else "مسلسل", "أكملت ${(c.fraction * 100).toInt()}%", c.poster) },
                            onLongClick = { vm.removeContinue(c.key) },
                        ) {
                            if (c.kind == "series") nav.go(Route.SeriesDetail(c.id))
                            else nav.go(Route.Player(PlayRequest("movie", c.id, c.title, c.poster,
                                listOf(PlayItem(c.title, null, vm.repo.movieUrl(c.id, c.ext ?: "mp4"), null, c.positionMs)))))
                        }
                    }
                }
            }

            val chRow = if (favChannels.isNotEmpty()) favChannels else home.channels
            if (chRow.isNotEmpty()) item(key = "channels") {
                Section(if (favChannels.isNotEmpty()) "قنواتك المفضلة" else "القنوات") {
                    items(chRow, key = { it.id }) { ch ->
                        ChannelCard(ch, Modifier.width(132.u), favorite = ch.key in favorites,
                            onFocus = { hero.value = Hero(ch.name, "قناة مباشرة • ${ch.num}", null, ch.logo) },
                            onLongClick = { vm.toggleFavorite(ch.key) },
                        ) { nav.go(Route.Live(ch.id)) }
                    }
                }
            }

            if (home.latestMovies.isNotEmpty()) item(key = "latestMovies") { MovieRow("أحدث الأفلام", home.latestMovies, favorites, vm, nav, hero) }
            if (home.topMovies.isNotEmpty()) item(key = "topMovies") { MovieRow("الأعلى تقييمًا", home.topMovies, favorites, vm, nav, hero) }

            if (home.latestSeries.isNotEmpty()) item(key = "latestSeries") {
                Section("أحدث المسلسلات") {
                    items(home.latestSeries, key = { it.id }) { s ->
                        PosterCard(s.name, s.cover, Modifier.width(104.u), meta = s.year, favorite = s.key in favorites,
                            onFocus = { hero.value = Hero(s.name, listOfNotNull("مسلسل", s.year, s.genre).joinToString(" • "), s.plot, s.cover) },
                            onLongClick = { vm.toggleFavorite(s.key) },
                        ) { nav.go(Route.SeriesDetail(s.id)) }
                    }
                }
            }

            if (home.movieCats.isNotEmpty()) item(key = "movieCats") {
                Section("تصفح الأفلام حسب التصنيف") {
                    items(home.movieCats, key = { it.first.id }) { (cat, n) ->
                        Chip("${cat.name}  $n", false, { nav.tab(Route.Movies(cat.id)) })
                    }
                }
            }
            if (home.seriesCats.isNotEmpty()) item(key = "seriesCats") {
                Section("تصفح المسلسلات حسب التصنيف") {
                    items(home.seriesCats, key = { it.first.id }) { (cat, n) ->
                        Chip("${cat.name}  $n", false, { nav.tab(Route.Shows(cat.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieRow(title: String, list: List<Movie>, favorites: Set<String>, vm: AppViewModel, nav: Nav, hero: MutableState<Hero?>) {
    Section(title) {
        items(list, key = { it.id }) { m ->
            PosterCard(m.name, m.poster, Modifier.width(104.u), meta = m.year, favorite = m.key in favorites,
                onFocus = { hero.value = Hero(m.name, listOfNotNull("فيلم", m.year, m.rating?.let { "★ " + String.format("%.1f", it) }).joinToString(" • "), null, m.poster) },
                onLongClick = { vm.toggleFavorite(m.key) },
            ) { nav.go(Route.MovieDetail(m.id)) }
        }
    }
}

@Composable
private fun Section(title: String, row: LazyListScope.() -> Unit) {
    Column {
        UText(title, 17, FontWeight.Bold, modifier = Modifier.padding(horizontal = 28.u))
        Spacer(Modifier.height(8.u))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.u, vertical = 8.u),
            horizontalArrangement = Arrangement.spacedBy(14.u),
            content = row,
        )
    }
}

@Composable
private fun HeroBanner(hero: State<Hero?>) {
    val h = hero.value
    Box(Modifier.fillMaxWidth().height(168.u)) {
        Img(h?.image, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.bg.copy(alpha = 0.55f), 0.5f to C.bg, 1f to C.bg)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to C.bg)))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 32.u).fillMaxWidth(0.55f)) {
            if (h == null) {
                UText("مرحبًا بك", 28, FontWeight.Bold)
                Spacer(Modifier.height(6.u))
                UText("تنقّل بالأسهم واضغط OK للتشغيل. اضغط مطوّلًا على OK لإضافة عنصر إلى المفضلة.", 13, color = C.text2, maxLines = 2)
            } else {
                UText(h.title, 28, FontWeight.Bold)
                Spacer(Modifier.height(4.u))
                UText(h.meta, 13, FontWeight.Medium, C.accent)
                if (!h.body.isNullOrBlank()) { Spacer(Modifier.height(6.u)); UText(h.body, 13, color = C.text2, maxLines = 3) }
            }
        }
    }
}

@Composable
private fun Shortcuts(nav: Nav, first: FocusRequester) {
    val entries: List<Triple<String, Ic, () -> Unit>> = listOf(
        Triple("القنوات", Ic.Live, { nav.tab(Route.Live()) }),
        Triple("الأفلام", Ic.Movies, { nav.tab(Route.Movies()) }),
        Triple("المسلسلات", Ic.Series, { nav.tab(Route.Shows()) }),
        Triple("دليل البرامج", Ic.Guide, { nav.go(Route.Epg) }),
        Triple("المفضلة", Ic.Heart, { nav.tab(Route.Favorites) }),
        Triple("البحث", Ic.Search, { nav.tab(Route.Search) }),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 28.u, vertical = 6.u), horizontalArrangement = Arrangement.spacedBy(12.u)) {
        items(entries.size) { i ->
            val (label, ic, action) = entries[i]
            FocusBox(action, Modifier.width(132.u).height(54.u), shape = RoundedCornerShape(12.u), background = C.glass,
                focusRequester = if (i == 0) first else null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.u), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, 20, C.accent); Spacer(Modifier.width(10.u)); UText(label, 14, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(c: ContinueItem, onFocus: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(196.u)) {
        FocusBox(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onLongClick = onLongClick, onFocus = onFocus,
            shape = RoundedCornerShape(10.u), focusScale = 1.06f) {
            Img(c.poster, Modifier.fillMaxSize().clip(RoundedCornerShape(10.u)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))))
            Icon(Ic.Play, 26, Color.White, Modifier.align(Alignment.Center))
            ProgressBar(c.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.u))
        }
        Spacer(Modifier.height(6.u))
        UText(c.title, 12, FontWeight.Medium)
        if (c.subtitle != null) UText(c.subtitle, 11, color = C.text3)
    }
}
