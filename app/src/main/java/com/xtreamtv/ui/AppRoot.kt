package com.xtreamtv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.design.*
import com.xtreamtv.ui.accounts.AccountsScreen
import com.xtreamtv.ui.catalog.FavoritesScreen
import com.xtreamtv.ui.catalog.MoviesScreen
import com.xtreamtv.ui.catalog.ShowsScreen
import com.xtreamtv.ui.detail.MovieDetailScreen
import com.xtreamtv.ui.detail.SeriesDetailScreen
import com.xtreamtv.ui.epg.EpgScreen
import com.xtreamtv.ui.home.HomeScreen
import com.xtreamtv.ui.live.LiveScreen
import com.xtreamtv.ui.nav.*
import com.xtreamtv.ui.player.PlayerScreen
import com.xtreamtv.ui.search.SearchScreen
import com.xtreamtv.ui.settings.SettingsScreen

private class RailItem(val route: Route, val ic: Ic, val label: String)

private val RAIL = listOf(
    RailItem(Route.Home, Ic.Home, "الرئيسية"),
    RailItem(Route.Live(), Ic.Live, "القنوات"),
    RailItem(Route.Movies(), Ic.Movies, "الأفلام"),
    RailItem(Route.Shows(), Ic.Series, "المسلسلات"),
    RailItem(Route.Favorites, Ic.Heart, "المفضلة"),
    RailItem(Route.Search, Ic.Search, "البحث"),
    RailItem(Route.Accounts, Ic.User, "الحسابات"),
    RailItem(Route.Settings, Ic.Settings, "الإعدادات"),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = remember { Nav() }
    val activity = LocalContext.current as Activity
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val route = nav.current
    var railFocused by remember { mutableStateOf(false) }
    val railFocus = remember { FocusRequester() }

    LaunchedEffect(accounts.isEmpty()) { if (accounts.isEmpty()) nav.tab(Route.Accounts) }

    BackHandler {
        if (nav.hooks.asReversed().any { it() }) return@BackHandler
        when {
            route.showsRail() && !railFocused && railFocus.tryFocus() -> {}
            nav.back() -> {}
            route !is Route.Home -> nav.tab(Route.Home)
            else -> activity.finish()
        }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize()) {
            val railW = 76.u
            val showRail = route.showsRail()
            Box(Modifier.fillMaxSize().padding(start = if (showRail) railW else 0.dp)) {
                key(route) { Screen(route, vm, nav) }
            }
            if (showRail) NavRail(route, nav, railFocus, railW) { railFocused = it }
            if (offline && route !is Route.Player && route !is Route.Live) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 10.u).clip(RoundedCornerShape(50))
                        .background(C.accent.copy(alpha = 0.18f)).padding(horizontal = 14.u, vertical = 6.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Info, 14, C.accent); Spacer(Modifier.width(6.u))
                    UText("وضع عدم الاتصال — عرض بيانات محفوظة", 12, color = C.accent)
                }
            }
        }
    }
}

@Composable
private fun Screen(route: Route, vm: AppViewModel, nav: Nav) {
    when (route) {
        is Route.Home -> HomeScreen(vm, nav)
        is Route.Live -> LiveScreen(vm, nav, route)
        is Route.Movies -> MoviesScreen(vm, nav, route.catId)
        is Route.Shows -> ShowsScreen(vm, nav, route.catId)
        is Route.Favorites -> FavoritesScreen(vm, nav)
        is Route.Search -> SearchScreen(vm, nav)
        is Route.Accounts -> AccountsScreen(vm, nav)
        is Route.Settings -> SettingsScreen(vm)
        is Route.Epg -> EpgScreen(vm, nav)
        is Route.MovieDetail -> MovieDetailScreen(vm, nav, route.id)
        is Route.SeriesDetail -> SeriesDetailScreen(vm, nav, route.id)
        is Route.Player -> PlayerScreen(vm, nav, route.request)
    }
}

/** Overlay rail: collapsed icons by default, expands over the content when it has focus. */
@Composable
private fun NavRail(current: Route, nav: Nav, railFocus: FocusRequester, collapsed: androidx.compose.ui.unit.Dp, onFocus: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val w by animateDpAsState(if (expanded) 232.u else collapsed, tween(160), label = "rail")
    Column(
        Modifier.fillMaxHeight().width(w).zIndex(5f)
            .background(Brush.horizontalGradient(listOf(Color(0xF2080A0F), Color(0xF2080A0F), Color(0xB3080A0F))))
            .onFocusChanged { expanded = it.hasFocus; onFocus(it.hasFocus) }
            .focusGroup()
            .padding(horizontal = 10.u, vertical = 18.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.padding(start = 6.u, bottom = 14.u), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.u).clip(RoundedCornerShape(9.u)).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Ic.Play, 18, Color(0xFF14110A))
            }
            if (expanded) { Spacer(Modifier.width(10.u)); UText("Xtream TV", 17, FontWeight.Bold, C.text) }
        }
        RAIL.forEach { item ->
            val selected = item.route.tabIndex() == current.tabIndex()
            FocusBox(
                onClick = { nav.tab(item.route) },
                modifier = Modifier.fillMaxWidth().height(46.u),
                shape = RoundedCornerShape(12.u), focusScale = 1f,
                background = Color.Transparent, focusedBackground = C.glass,
                focusRequester = if (selected) railFocus else null,
            ) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.ic, 22, if (selected) C.accent else if (f) C.text else C.text2)
                    if (expanded) {
                        Spacer(Modifier.width(14.u))
                        UText(item.label, 15, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) C.accent else C.text)
                    }
                }
                if (selected) Box(Modifier.align(Alignment.CenterStart).width(3.u).height(22.u).clip(RoundedCornerShape(2.u)).background(C.accent))
            }
        }
    }
}
