package com.xtreamtv.ui.nav

import androidx.compose.runtime.*
import com.xtreamtv.domain.PlayRequest

sealed interface Route {
    data object Home : Route
    data class Live(val channelId: Int? = null, val catId: String? = null) : Route
    data class Movies(val catId: String? = null) : Route
    data class Shows(val catId: String? = null) : Route
    data object Favorites : Route
    data object Search : Route
    data object Accounts : Route
    data object Settings : Route
    data object Epg : Route
    data class MovieDetail(val id: Int) : Route
    data class SeriesDetail(val id: Int) : Route
    data class Player(val request: PlayRequest) : Route
}

/** Index in the side rail, or -1 for routes that are not rail tabs. */
fun Route.tabIndex(): Int = when (this) {
    is Route.Home -> 0; is Route.Live -> 1; is Route.Movies -> 2; is Route.Shows -> 3
    is Route.Favorites -> 4; is Route.Search -> 5; is Route.Accounts -> 6; is Route.Settings -> 7
    else -> -1
}

/** The rail is visible on tab screens, except Live which is fullscreen. */
fun Route.showsRail(): Boolean = tabIndex() >= 0 && this !is Route.Live

/** Tiny hand-rolled navigation: a route stack plus back hooks (screens can intercept Back). */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    val hooks = mutableListOf<() -> Boolean>()

    fun go(r: Route) { stack.add(r) }
    fun tab(r: Route) { stack.clear(); stack.add(Route.Home); if (r != Route.Home) stack.add(r) }
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

/** Lets a screen consume Back. Return true from [onBack] when handled. */
@Composable
fun BackHook(enabled: Boolean = true, onBack: () -> Boolean) {
    val nav = LocalNav.current
    val cb by rememberUpdatedState(onBack)
    val en by rememberUpdatedState(enabled)
    DisposableEffect(nav) {
        val h: () -> Boolean = { en && cb() }
        nav.hooks.add(h)
        onDispose { nav.hooks.remove(h) }
    }
}
