package com.xtreamtv.ui.live

import android.os.SystemClock
import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.design.*
import com.xtreamtv.domain.*
import com.xtreamtv.player.LiveController
import com.xtreamtv.player.PlayerSurface
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.nav.BackHook
import com.xtreamtv.ui.nav.Nav
import com.xtreamtv.ui.nav.Route
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val ALL = "all"
private const val FAV = "fav"

@Composable
fun LiveScreen(vm: AppViewModel, nav: Nav, route: Route.Live) {
    val state by vm.live.collectAsStateWithLifecycle()
    StateHost(state, onRetry = { vm.loadLive(true) }, empty = { it.items.isEmpty() }, emptyText = "لا توجد قنوات في هذا الحساب") {
        LiveContent(vm, nav, route, it)
    }
}

@Composable
private fun rememberNowSec(): State<Long> = produceState(System.currentTimeMillis() / 1000) {
    while (true) { delay(15_000); value = System.currentTimeMillis() / 1000 }
}

@Composable
private fun LiveContent(vm: AppViewModel, nav: Nav, route: Route.Live, catalog: Catalog<LiveChannel>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val settingsNow by rememberUpdatedState(settings)
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val controller = remember { LiveController(ctx, vm.repo, scope) { settingsNow.liveFormat } }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val ob = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) controller.pause() else if (e == Lifecycle.Event.ON_START) controller.resume()
        }
        lifecycle.lifecycle.addObserver(ob)
        onDispose { lifecycle.lifecycle.removeObserver(ob) }
    }

    // Categories: all / favorites / server categories
    val cats = remember(catalog) { listOf(Category(ALL, "كل القنوات"), Category(FAV, "المفضلة")) + catalog.nonEmptyCategories }
    var catId by remember { mutableStateOf(route.catId ?: ALL) }
    val channels = remember(catalog, catId, favorites) {
        when (catId) {
            ALL -> catalog.items
            FAV -> catalog.items.filter { it.key in favorites }
            else -> catalog.byCategory[catId].orEmpty()
        }
    }

    var panelVisible by remember { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0L) }
    var infoTick by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var focusedCh by remember { mutableStateOf<LiveChannel?>(null) }

    val rootFocus = remember { FocusRequester() }
    val currentFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val zap: (LiveChannel) -> Unit = { ch ->
        controller.play(ch); vm.setLastLive(ch.id); infoTick++
    }

    // Initial channel
    LaunchedEffect(catalog) {
        if (controller.current == null) {
            val target = route.channelId?.let { catalog.byId[it] }
                ?: (if (settingsNow.resumeLast) vm.lastLiveId()?.let { catalog.byId[it] } else null)
                ?: catalog.items.firstOrNull()
            target?.let(zap)
        }
    }

    // Info banner auto-hide
    LaunchedEffect(infoTick) { if (infoTick > 0) { infoVisible = true; delay(4_500); infoVisible = false } }

    // Panel auto-hide + return focus to the root when closed
    LaunchedEffect(panelVisible, interaction) {
        if (panelVisible) { delay(settingsNow.panelTimeoutSec * 1000L); panelVisible = false }
    }
    LaunchedEffect(panelVisible) { if (!panelVisible) { repeat(8) { withFrameNanos { }; if (rootFocus.tryFocus()) return@LaunchedEffect; delay(40) } } }

    // Focus anchor when the panel opens (or category changes)
    val anchor = remember(panelVisible, catId) { channels.indexOfFirst { it.id == controller.current?.id }.coerceAtLeast(0) }
    LaunchedEffect(panelVisible, catId) {
        if (panelVisible) {
            listState.scrollToItem((anchor - 2).coerceAtLeast(0))
            repeat(10) { withFrameNanos { }; if (currentFocus.tryFocus()) return@LaunchedEffect; delay(40) }
        }
    }

    // Prefetch "now" programme titles for visible rows
    LaunchedEffect(panelVisible, channels) {
        if (!panelVisible) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collectLatest { idx ->
            delay(300)
            idx.forEach { i -> channels.getOrNull(i)?.let(controller::prefetchEpg) }
        }
    }
    // Keep EPG titles fresh
    LaunchedEffect(Unit) { while (true) { delay(180_000); controller.epgNow.clear() } }

    // Direct channel number entry
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1_600)
            val n = digits.toIntOrNull(); digits = ""
            if (n != null) catalog.items.firstOrNull { it.num == n }?.let(zap)
        }
    }

    fun step(delta: Int) {
        val list = channels.ifEmpty { catalog.items }
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == controller.current?.id }
        zap(list[((if (i < 0) 0 else i) + delta + list.size) % list.size])
    }

    fun switchCat(delta: Int) {
        val i = cats.indexOfFirst { it.id == catId }.coerceAtLeast(0)
        catId = cats[(i + delta + cats.size) % cats.size].id
    }

    BackHook { if (panelVisible) { panelVisible = false; true } else false }

    val onKey: (KeyEvent) -> Boolean = key@{ e ->
        if (e.type != KeyEventType.KeyDown) return@key false
        interaction = SystemClock.uptimeMillis()
        val code = e.nativeKeyEvent.keyCode
        if (code in AKeyEvent.KEYCODE_0..AKeyEvent.KEYCODE_9) {
            if (digits.length < 4) digits += (code - AKeyEvent.KEYCODE_0).toString()
            return@key true
        }
        when (code) {
            AKeyEvent.KEYCODE_CHANNEL_UP, AKeyEvent.KEYCODE_PAGE_UP -> { step(+1); true }
            AKeyEvent.KEYCODE_CHANNEL_DOWN, AKeyEvent.KEYCODE_PAGE_DOWN -> { step(-1); true }
            AKeyEvent.KEYCODE_GUIDE, AKeyEvent.KEYCODE_TV_DATA_SERVICE -> { nav.go(Route.Epg); true }
            AKeyEvent.KEYCODE_INFO -> { infoTick++; true }
            AKeyEvent.KEYCODE_MENU -> { panelVisible = !panelVisible; true }
            else -> if (!panelVisible) {
                when (code) {
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT -> { panelVisible = true; true }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER ->
                        if (controller.status == LiveController.Status.Error) { controller.retry(); true } else { panelVisible = true; true }
                    else -> false
                }
            } else when (code) {
                AKeyEvent.KEYCODE_DPAD_LEFT -> { switchCat(+1); true }   // RTL: left = next category
                AKeyEvent.KEYCODE_DPAD_RIGHT -> { switchCat(-1); true }
                else -> false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onKeyEvent(onKey)) {
        PlayerSurface(controller.player, Modifier.fillMaxSize())
        Box(Modifier.size(1.dp).focusRequester(rootFocus).focusable()) // key sink when the panel is hidden

        // Buffering / error state
        val showSpinner by produceState(false, controller.status) {
            value = false
            if (controller.status == LiveController.Status.Loading || controller.status == LiveController.Status.Buffering) { delay(500); value = true }
        }
        if (showSpinner) Box(Modifier.align(Alignment.Center)) { Spinner(48) }
        if (controller.status == LiveController.Status.Error) {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.u)).background(C.scrim).padding(24.u),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Ic.Info, 36, C.danger); Spacer(Modifier.height(8.u))
                UText("تعذر تشغيل هذه القناة", 18, FontWeight.Bold)
                Spacer(Modifier.height(4.u))
                UText("اضغط OK لإعادة المحاولة أو اختر قناة أخرى", 13, color = C.text2)
            }
        }

        // Direct number entry
        if (digits.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopStart).padding(28.u).clip(RoundedCornerShape(12.u)).background(C.scrim).padding(horizontal = 20.u, vertical = 10.u)) {
                UText(digits, 34, FontWeight.Bold, C.accent)
            }
        }

        // Zap banner
        AnimatedVisibility(
            visible = infoVisible && !panelVisible && controller.current != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(150)),
        ) { controller.current?.let { ZapBanner(it, controller) } }

        // Channel panel (start side)
        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(180)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(150)) { it } + fadeOut(tween(150)),
        ) {
            ChannelPanel(
                cats = cats, catId = catId, channels = channels, controller = controller, favorites = favorites,
                listState = listState, anchor = anchor, currentFocus = currentFocus, focusedCh = focusedCh,
                onFocusCh = { focusedCh = it },
                onGuide = { nav.go(Route.Epg) },
                onSelect = { ch -> if (ch.id == controller.current?.id) panelVisible = false else zap(ch) },
                onToggleFav = { vm.toggleFavorite(it.key) },
            )
        }
    }
}

@Composable
private fun ChannelPanel(
    cats: List<Category>, catId: String, channels: List<LiveChannel>, controller: LiveController, favorites: Set<String>,
    listState: androidx.compose.foundation.lazy.LazyListState, anchor: Int, currentFocus: FocusRequester,
    focusedCh: LiveChannel?, onFocusCh: (LiveChannel) -> Unit, onGuide: () -> Unit,
    onSelect: (LiveChannel) -> Unit, onToggleFav: (LiveChannel) -> Unit,
) {
    Column(
        Modifier.width(380.u).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xCC05070B), Color(0xF205070B))))
            .padding(top = 18.u, bottom = 12.u),
    ) {
        // Header: category with chevrons + guide button
        Row(Modifier.padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.ChevR, 18, C.text3)
            Spacer(Modifier.width(6.u))
            Column(Modifier.weight(1f)) {
                UText(cats.firstOrNull { it.id == catId }?.name ?: "", 17, FontWeight.Bold)
                UText("${channels.size} قناة", 11, color = C.text3)
            }
            Spacer(Modifier.width(6.u))
            Icon(Ic.ChevL, 18, C.text3)
            Spacer(Modifier.width(10.u))
            FocusBox(onGuide, Modifier.height(32.u), shape = RoundedCornerShape(50), focusScale = 1.05f, background = C.glass) {
                Row(Modifier.padding(horizontal = 12.u), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Guide, 15, C.accent); Spacer(Modifier.width(6.u)); UText("الدليل", 12, FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.u))

        if (channels.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                UText(if (catId == FAV) "لا توجد قنوات مفضلة.\nاضغط مطوّلًا على OK فوق أي قناة لإضافتها." else "لا توجد قنوات",
                    14, color = C.text2, align = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(24.u))
            }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 12.u, vertical = 4.u),
                verticalArrangement = Arrangement.spacedBy(4.u)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    ChannelRow(
                        ch, current = ch.id == controller.current?.id, favorite = ch.key in favorites,
                        nowTitle = controller.epgNow[ch.id],
                        focusRequester = if (i == anchor) currentFocus else null,
                        onFocus = { onFocusCh(ch) }, onLongClick = { onToggleFav(ch) },
                    ) { onSelect(ch) }
                }
            }
        }

        PanelFooter(focusedCh ?: controller.current, controller)
    }
}

@Composable
private fun ChannelRow(
    ch: LiveChannel, current: Boolean, favorite: Boolean, nowTitle: String?,
    focusRequester: FocusRequester?, onFocus: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit,
) {
    FocusBox(
        onClick, Modifier.fillMaxWidth().height(54.u), onLongClick = onLongClick, onFocus = onFocus,
        shape = RoundedCornerShape(10.u), focusScale = 1.02f,
        background = if (current) C.accent.copy(alpha = 0.12f) else Color.Transparent,
        focusRequester = focusRequester,
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.u), verticalAlignment = Alignment.CenterVertically) {
            UText(ch.num.toString(), 15, FontWeight.Bold, if (current) C.accent else C.text2, Modifier.width(42.u), align = TextAlign.Center)
            Spacer(Modifier.width(8.u))
            ChannelLogo(ch.logo, ch.name, Modifier.width(48.u).height(34.u).clip(RoundedCornerShape(6.u)).background(Color(0x14FFFFFF)).padding(3.u))
            Spacer(Modifier.width(10.u))
            Column(Modifier.weight(1f)) {
                UText(ch.name, 15, if (f || current) FontWeight.SemiBold else FontWeight.Medium)
                if (nowTitle != null) UText(nowTitle, 11, color = C.text3)
            }
            if (favorite) { Icon(Ic.HeartFill, 13, C.accent); Spacer(Modifier.width(6.u)) }
            if (current) Icon(Ic.Play, 14, C.accent)
        }
    }
}

@Composable
private fun PanelFooter(ch: LiveChannel?, controller: LiveController) {
    var entries by remember(ch?.id) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(ch?.id) {
        if (ch != null) { delay(250); entries = controller.epgFor(ch) }
    }
    val now by rememberNowSec()
    val (cur, next) = entries.nowNext(now)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.u).padding(top = 6.u).clip(RoundedCornerShape(12.u))
            .background(C.glass).padding(12.u),
    ) {
        if (ch == null) return@Column
        UText(ch.name, 12, color = C.text3)
        Spacer(Modifier.height(3.u))
        if (cur == null) {
            UText("لا تتوفر بيانات البرنامج لهذه القناة", 13, color = C.text2)
        } else {
            UText(cur.title, 15, FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.u))
            UText("${Fmt.clock(cur.start)} – ${Fmt.clock(cur.end)}", 12, color = C.accent)
            Spacer(Modifier.height(6.u))
            ProgressBar(cur.progress(now), Modifier.fillMaxWidth())
            if (next != null) { Spacer(Modifier.height(6.u)); UText("التالي: ${next.title}", 12, color = C.text2) }
        }
    }
}

@Composable
private fun ZapBanner(ch: LiveChannel, controller: LiveController) {
    val now by rememberNowSec()
    val (cur, next) = controller.epg.nowNext(now)
    Row(
        Modifier.padding(horizontal = 48.u, vertical = 28.u).fillMaxWidth().clip(RoundedCornerShape(18.u))
            .background(Brush.horizontalGradient(listOf(Color(0xEE0A0D13), Color(0xCC0A0D13)))).padding(16.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 84.u, height = 58.u).clip(RoundedCornerShape(10.u)).background(Color(0x14FFFFFF)).padding(6.u)) {
            ChannelLogo(ch.logo, ch.name, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.u))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UText(ch.num.toString(), 22, FontWeight.Bold, C.accent)
                Spacer(Modifier.width(10.u))
                UText(ch.name, 20, FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.u))
            if (cur == null) {
                UText("لا تتوفر بيانات البرنامج", 13, color = C.text2)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UText(cur.title, 15, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.u))
                    UText("${Fmt.clock(cur.start)} – ${Fmt.clock(cur.end)}", 13, color = C.accent)
                }
                Spacer(Modifier.height(6.u))
                ProgressBar(cur.progress(now), Modifier.fillMaxWidth())
                if (next != null) { Spacer(Modifier.height(6.u)); UText("التالي: ${next.title}  •  ${Fmt.clock(next.start)}", 12, color = C.text2) }
            }
        }
    }
}
