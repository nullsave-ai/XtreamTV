package com.xtreamtv.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.Fmt
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route
import kotlinx.coroutines.delay

private class EpgSel(val channel: LiveChannel, val entry: EpgEntry?)

/** EPG guide: rows of channels with their real programmes. Nothing is invented: rows without data say so. */
@Composable
fun EpgScreen(vm: AppViewModel, nav: Nav) {
    val state by vm.live.collectAsStateWithLifecycle()
    StateHost(state, onRetry = { vm.loadLive(true) }, empty = { it.items.isEmpty() }, emptyText = "لا توجد قنوات") { catalog ->
        EpgContent(vm, nav, catalog)
    }
}

@Composable
private fun EpgContent(vm: AppViewModel, nav: Nav, catalog: Catalog<LiveChannel>) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val cats = remember(catalog) { listOf(Category("all", "الكل"), Category("fav", "المفضلة")) + catalog.nonEmptyCategories }
    var catId by remember { mutableStateOf("all") }
    val channels = remember(catalog, catId, favorites) {
        when (catId) { "all" -> catalog.items; "fav" -> catalog.items.filter { it.key in favorites }; else -> catalog.byCategory[catId].orEmpty() }
    }
    val sel = remember { mutableStateOf<EpgSel?>(null) }
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    if (channels.isNotEmpty()) RequestFocusOnce(first, catId)

    Column(Modifier.fillMaxSize().padding(top = 18.u)) {
        Detail(sel)
        Spacer(Modifier.height(8.u))
        LazyRow(contentPadding = PaddingValues(horizontal = 28.u), horizontalArrangement = Arrangement.spacedBy(8.u)) {
            items(cats, key = { it.id }) { c -> Chip(c.name, c.id == catId, { catId = c.id }, onFocus = { catId = c.id }) }
        }
        Spacer(Modifier.height(8.u))
        if (channels.isEmpty()) {
            EmptyMessage("لا توجد قنوات في هذا التصنيف")
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.u, vertical = 8.u), verticalArrangement = Arrangement.spacedBy(8.u)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    EpgRow(vm, nav, ch, sel, if (i == 0) first else null)
                }
            }
        }
    }
}

@Composable
private fun Detail(sel: State<EpgSel?>) {
    val s = sel.value
    Column(Modifier.fillMaxWidth().height(86.u).padding(horizontal = 32.u)) {
        if (s == null) {
            UText("دليل البرامج", 24, FontWeight.Bold)
            UText("اختر برنامجًا لعرض تفاصيله، واضغط OK لمشاهدة القناة.", 13, color = C.text2)
        } else {
            val e = s.entry
            UText(e?.title ?: s.channel.name, 22, FontWeight.Bold)
            if (e != null) {
                UText("${s.channel.name}  •  ${Fmt.clock(e.start)} – ${Fmt.clock(e.end)}", 13, FontWeight.Medium, C.accent)
                if (!e.description.isNullOrBlank()) UText(e.description, 12, color = C.text2, maxLines = 2)
            } else UText("${s.channel.name} — لا تتوفر بيانات البرامج", 13, color = C.text2)
        }
    }
}

@Composable
private fun EpgRow(vm: AppViewModel, nav: Nav, ch: LiveChannel, sel: MutableState<EpgSel?>, firstFocus: FocusRequester?) {
    var entries by remember(ch.id) { mutableStateOf<List<EpgEntry>?>(null) }
    LaunchedEffect(ch.id) { entries = vm.repo.peekEpg(ch) ?: vm.repo.epg(ch, 8) }
    val nowSec by produceState(System.currentTimeMillis() / 1000) { while (true) { delay(30_000); value = System.currentTimeMillis() / 1000 } }
    val open = { nav.go(Route.Live(ch.id)) }

    Row(Modifier.fillMaxWidth().height(62.u), verticalAlignment = Alignment.CenterVertically) {
        // Channel cell
        FocusBox(open, Modifier.width(190.u).fillMaxHeight(), onFocus = { sel.value = EpgSel(ch, null) },
            shape = RoundedCornerShape(10.u), focusScale = 1.03f, focusRequester = firstFocus) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.u), verticalAlignment = Alignment.CenterVertically) {
                UText(ch.num.toString(), 14, FontWeight.Bold, C.text2, Modifier.width(34.u), align = TextAlign.Center)
                ChannelLogo(ch.logo, ch.name, Modifier.width(40.u).height(30.u))
                Spacer(Modifier.width(8.u))
                UText(ch.name, 13, FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(10.u))
        // Programmes: time flows left-to-right
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val list = entries
            when {
                list == null -> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.u)).background(C.glass))
                list.none { it.end > nowSec } -> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.u)).background(C.glass), contentAlignment = Alignment.CenterStart) {
                    UText("لا تتوفر بيانات EPG لهذه القناة", 12, color = C.text3, modifier = Modifier.padding(horizontal = 14.u))
                }
                else -> LazyRow(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.u), verticalAlignment = Alignment.CenterVertically) {
                    items(list.filter { it.end > nowSec }, key = { it.start }) { e ->
                        val isNow = nowSec >= e.start
                        val minutes = ((e.end - maxOf(e.start, nowSec)) / 60f).coerceAtLeast(1f)
                        FocusBox(open, Modifier.width((minutes * 3.2f).coerceIn(110f, 420f).u).fillMaxHeight(),
                            onFocus = { sel.value = EpgSel(ch, e) }, shape = RoundedCornerShape(10.u), focusScale = 1.03f,
                            background = if (isNow) C.accent.copy(alpha = 0.14f) else C.glass) {
                            Column(Modifier.fillMaxSize().padding(horizontal = 10.u, vertical = 7.u), verticalArrangement = Arrangement.SpaceBetween) {
                                UText(e.title, 13, FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UText(Fmt.clock(e.start), 11, color = if (isNow) C.accent else C.text3)
                                    if (isNow) { Spacer(Modifier.width(8.u)); ProgressBar(e.progress(nowSec), Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
