package com.xtreamtv.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.UiState
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.common.ChannelCard
import com.xtreamtv.ui.common.PosterCard
import com.xtreamtv.ui.common.PosterGrid
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route
import kotlinx.coroutines.flow.collectLatest

private const val ALL = "all"
private const val FAV = "fav"

@Composable
fun MoviesScreen(vm: AppViewModel, nav: Nav, initialCat: String?) {
    val state by vm.movies.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    CatalogScreen(
        title = "الأفلام", state = state, onRetry = { vm.loadMovies(true) }, vm = vm, memoryKey = "movies", initialCat = initialCat,
        favorites = favorites, keyOf = { it.id }, favKey = { it.key }, categoryOf = { it.categoryId },
        name = { it.name }, image = { it.poster }, meta = { it.year },
        onOpen = { nav.go(Route.MovieDetail(it.id)) },
    )
}

@Composable
fun ShowsScreen(vm: AppViewModel, nav: Nav, initialCat: String?) {
    val state by vm.series.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    CatalogScreen(
        title = "المسلسلات", state = state, onRetry = { vm.loadSeries(true) }, vm = vm, memoryKey = "series", initialCat = initialCat,
        favorites = favorites, keyOf = { it.id }, favKey = { it.key }, categoryOf = { it.categoryId },
        name = { it.name }, image = { it.cover }, meta = { it.year },
        onOpen = { nav.go(Route.SeriesDetail(it.id)) },
    )
}

/** Categories column + poster grid. Category and scroll position are remembered per screen. */
@Composable
private fun <T> CatalogScreen(
    title: String, state: UiState<Catalog<T>>, onRetry: () -> Unit, vm: AppViewModel, memoryKey: String, initialCat: String?,
    favorites: Set<String>, keyOf: (T) -> Int, favKey: (T) -> String, categoryOf: (T) -> String,
    name: (T) -> String, image: (T) -> String?, meta: (T) -> String?, onOpen: (T) -> Unit,
) {
    StateHost(state, onRetry, empty = { it.items.isEmpty() }, emptyText = "لا يوجد محتوى في هذا الحساب") { catalog ->
        val mem = remember { vm.memory(memoryKey) }
        var catId by remember { mutableStateOf(initialCat ?: mem.cat) }
        if (initialCat != null) { mem.cat = initialCat }
        val cats = remember(catalog) { listOf(Category(ALL, "الكل"), Category(FAV, "المفضلة")) + catalog.nonEmptyCategories }
        val items = remember(catalog, catId, favorites) {
            when (catId) {
                ALL -> catalog.items
                FAV -> catalog.items.filter { favKey(it) in favorites }
                else -> catalog.byCategory[catId].orEmpty()
            }
        }
        val startIndex = remember { if (initialCat != null) 0 else mem.index }
        val gridState = rememberLazyGridState(startIndex.coerceAtLeast(0))
        LaunchedEffect(gridState) { snapshotFlow { gridState.firstVisibleItemIndex }.collectLatest { mem.index = it } }
        LaunchedEffect(catId) { mem.cat = catId }

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(210.u).fillMaxHeight().background(Color0).padding(top = 22.u)) {
                UText(title, 22, FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.u))
                Spacer(Modifier.height(10.u))
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.u, vertical = 4.u), verticalArrangement = Arrangement.spacedBy(4.u)) {
                    items(cats, key = { it.id }) { c ->
                        val selected = c.id == catId
                        FocusBox(
                            onClick = { catId = c.id; mem.index = 0 }, Modifier.fillMaxWidth().height(40.u),
                            onFocus = { if (catId != c.id) { catId = c.id; mem.index = 0 } },
                            shape = RoundedCornerShape(10.u), focusScale = 1.02f,
                            background = if (selected) C.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                                UText(c.name, 14, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) C.accent else C.text, Modifier.weight(1f))
                                val n = when (c.id) { ALL -> catalog.items.size; FAV -> items.size.takeIf { catId == FAV } ?: 0; else -> catalog.count(c.id) }
                                if (n > 0) UText(n.toString(), 11, color = C.text3)
                            }
                        }
                    }
                }
            }
            PosterGrid(
                items = items, keyOf = keyOf, state = gridState, modifier = Modifier.weight(1f).fillMaxHeight(),
                startIndex = if (catId == mem.cat && initialCat == null) startIndex else 0,
                emptyText = if (catId == FAV) "لا توجد عناصر مفضلة بعد.\nاضغط مطوّلًا على OK فوق أي عنصر لإضافته." else "لا يوجد محتوى",
                focusOnEntry = true,
            ) { item, fr ->
                PosterCard(name(item), image(item), meta = meta(item), favorite = favKey(item) in favorites, focusRequester = fr,
                    onLongClick = { vm.toggleFavorite(favKey(item)) }) { onOpen(item) }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0x66000000)

@Composable
fun FavoritesScreen(vm: AppViewModel, nav: Nav) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val firstChip = remember { androidx.compose.ui.focus.FocusRequester() }
    RequestFocusOnce(firstChip)

    val ch = remember(live, favorites) { (live as? UiState.Success)?.data?.items?.filter { it.key in favorites }.orEmpty() }
    val mv = remember(movies, favorites) { (movies as? UiState.Success)?.data?.items?.filter { it.key in favorites }.orEmpty() }
    val sr = remember(series, favorites) { (series as? UiState.Success)?.data?.items?.filter { it.key in favorites }.orEmpty() }

    Column(Modifier.fillMaxSize().padding(top = 22.u)) {
        UText("المفضلة", 24, FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.u))
        Spacer(Modifier.height(10.u))
        LazyRow(contentPadding = PaddingValues(horizontal = 32.u), horizontalArrangement = Arrangement.spacedBy(8.u)) {
            val tabs = listOf("القنوات (${ch.size})", "الأفلام (${mv.size})", "المسلسلات (${sr.size})")
            items(tabs.size) { i -> Chip(tabs[i], tab == i, { tab = i }, onFocus = { tab = i }, focusRequester = if (i == 0) firstChip else null) }
        }
        val empty = "لا توجد عناصر مفضلة هنا.\nاضغط مطوّلًا على OK فوق أي عنصر لإضافته."
        when (tab) {
            0 -> PosterGrid(ch, { it.id }, rememberLazyGridState(), Modifier.weight(1f), minCell = 150, emptyText = empty, focusOnEntry = false) { c, fr ->
                ChannelCard(c, favorite = true, focusRequester = fr, onLongClick = { vm.toggleFavorite(c.key) }) { nav.go(Route.Live(c.id)) }
            }
            1 -> PosterGrid(mv, { it.id }, rememberLazyGridState(), Modifier.weight(1f), emptyText = empty, focusOnEntry = false) { m, fr ->
                PosterCard(m.name, m.poster, meta = m.year, favorite = true, focusRequester = fr, onLongClick = { vm.toggleFavorite(m.key) }) { nav.go(Route.MovieDetail(m.id)) }
            }
            else -> PosterGrid(sr, { it.id }, rememberLazyGridState(), Modifier.weight(1f), emptyText = empty, focusOnEntry = false) { s, fr ->
                PosterCard(s.name, s.cover, meta = s.year, favorite = true, focusRequester = fr, onLongClick = { vm.toggleFavorite(s.key) }) { nav.go(Route.SeriesDetail(s.id)) }
            }
        }
    }
}
