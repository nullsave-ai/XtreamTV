package com.xtreamtv.design

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.xtreamtv.core.UiState
import kotlinx.coroutines.delay

fun KeyEvent.isSelect() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

@Composable
fun UText(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = C.text,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text, modifier,
        style = TextStyle(color = color, fontSize = size.us, fontWeight = weight, textAlign = align, lineHeight = (size * 1.35f).us),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Remote-friendly click: select key = click, held select key = long click, plus touch/mouse taps. */
fun Modifier.tvClick(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    var longFired by remember { mutableStateOf(false) }
    Modifier
        .onKeyEvent { e ->
            if (!enabled || !e.isSelect()) return@onKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    val rc = e.nativeKeyEvent.repeatCount
                    if (rc == 0) longFired = false
                    else if (!longFired && long != null) { longFired = true; long?.invoke() }
                    true
                }
                KeyEventType.KeyUp -> { if (!longFired) click(); longFired = false; true }
                else -> false
            }
        }
        .focusable(enabled)
        .pointerInput(enabled) { detectTapGestures(onLongPress = { long?.invoke() }, onTap = { click() }) }
}

/** The focus system: scale + accent border + brighter surface. Everything focusable builds on this. */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.u),
    focusScale: Float = 1.06f,
    background: Color = C.surface,
    focusedBackground: Color = C.surfaceHi,
    showBorder: Boolean = true,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val anim = animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "focus")
    val bw = with(LocalDensity.current) { (2.5f * LocalUiScale.current).dp.toPx() }
    val onFocusState by rememberUpdatedState(onFocus)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { val s = 1f + (focusScale - 1f) * anim.value; scaleX = s; scaleY = s }
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), lerp(background, focusedBackground, anim.value)) }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused != focused) { focused = it.isFocused; if (it.isFocused) onFocusState?.invoke() }
            }
            .tvClick(enabled, onLongClick, onClick)
            .drawWithBorder(shape, anim, showBorder, bw),
        content = { content(focused) },
    )
}

private fun Modifier.drawWithBorder(shape: Shape, anim: State<Float>, show: Boolean, bw: Float): Modifier =
    this.then(Modifier.drawWithContent {
        drawContent()
        val a = anim.value
        if (show && a > 0.02f) drawOutline(shape.createOutline(size, layoutDirection, this), C.accent.copy(alpha = a), style = Stroke(bw))
    })

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
) {
    FocusBox(
        onClick, modifier, onFocus = onFocus, shape = RoundedCornerShape(10.u), focusScale = 1.05f,
        background = if (primary) C.accent else C.glass,
        focusedBackground = if (primary) C.accentHi else C.surfaceHi,
        focusRequester = focusRequester,
    ) {
        Row(Modifier.padding(horizontal = 18.u, vertical = 10.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, 18, if (primary) Color(0xFF14110A) else C.text); Spacer(Modifier.width(8.u)) }
            UText(text, 15, FontWeight.SemiBold, if (primary) Color(0xFF14110A) else C.text)
        }
    }
}

@Composable
fun IconButton(ic: Ic, onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 46, focusRequester: FocusRequester? = null, primary: Boolean = false) {
    FocusBox(
        onClick, modifier.size(size.u), shape = CircleShape, focusScale = 1.1f,
        background = if (primary) C.accent else C.glass, focusedBackground = if (primary) C.accentHi else C.surfaceHi,
        focusRequester = focusRequester,
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(ic, (size * 0.48f).toInt(), if (primary) Color(0xFF14110A) else C.text) } }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null, focusRequester: FocusRequester? = null) {
    FocusBox(
        onClick, modifier, onFocus = onFocus, shape = RoundedCornerShape(50), focusScale = 1.05f,
        background = if (selected) C.accent.copy(alpha = 0.22f) else C.glass, focusedBackground = C.surfaceHi,
        focusRequester = focusRequester,
    ) {
        UText(text, 14, if (selected) FontWeight.SemiBold else FontWeight.Medium, if (selected) C.accent else C.text, Modifier.padding(horizontal = 16.u, vertical = 8.u))
    }
}

@Composable
fun Img(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url.isNullOrBlank()) Box(modifier.background(C.surfaceHi))
    else AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = contentScale)
}

@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) UText(name.trim().take(2), 14, FontWeight.Bold, C.text2, align = TextAlign.Center)
        else AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
fun Spinner(size: Int = 36, color: Color = C.accent) {
    val t = rememberInfiniteTransition(label = "spin")
    val rot = t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
    val sw = with(LocalDensity.current) { (3f * LocalUiScale.current).dp.toPx() }
    Canvas(Modifier.size(size.u)) {
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(sw))
        drawArc(color, rot.value, 100f, false, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.accent) {
    Box(modifier.height(4.u).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier, icon: Ic? = null) {
    Column(modifier.fillMaxSize().padding(40.u), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, 44, C.text3); Spacer(Modifier.height(12.u)) }
        UText(text, 16, color = C.text2, align = TextAlign.Center, maxLines = 4)
    }
}

fun FocusRequester.tryFocus(): Boolean = try { requestFocus(); true } catch (_: Throwable) { false }

/** Requests focus once the target is composed and laid out (retries a few frames). */
@Composable
fun RequestFocusOnce(fr: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(12) {
            withFrameNanos { }
            if (fr.tryFocus()) return@LaunchedEffect
            delay(40)
        }
    }
}

/** Loading / error (with retry) / empty / content. */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: (T) -> Boolean = { false },
    emptyText: String = "لا يوجد محتوى",
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spinner(44); Spacer(Modifier.height(14.u)); UText("جارٍ التحميل…", 15, color = C.text2)
        }
        is UiState.Error -> {
            val fr = remember { FocusRequester() }
            RequestFocusOnce(fr)
            Column(modifier.fillMaxSize().padding(40.u), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Ic.Info, 44, if (state.offline) C.accent else C.danger)
                Spacer(Modifier.height(12.u))
                UText(if (state.offline) "لا يوجد اتصال" else "حدث خطأ", 20, FontWeight.Bold)
                Spacer(Modifier.height(6.u))
                UText(state.message, 14, color = C.text2, align = TextAlign.Center, maxLines = 3)
                Spacer(Modifier.height(18.u))
                TvButton("إعادة المحاولة", onRetry, icon = Ic.Refresh, primary = true, focusRequester = fr)
            }
        }
        is UiState.Success -> if (empty(state.data)) EmptyMessage(emptyText, modifier) else content(state.data)
    }
}

@Composable
fun TvDialog(onDismiss: () -> Unit, width: Int = 440, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(18.u)
            Column(
                Modifier.width(width.u).clip(shape).background(C.surface).border(1.dp, C.glassBorder, shape).padding(22.u),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    ltr: Boolean = false,
    last: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.u)
    Column(modifier) {
        UText(label, 12, color = C.text2)
        Spacer(Modifier.height(4.u))
        val border by animateColorAsState(if (focused) C.accent else C.glassBorder, label = "tf")
        Box(
            Modifier.fillMaxWidth().height(42.u).clip(shape).background(C.bg)
                .border(if (focused) 2.dp else 1.dp, border, shape).padding(horizontal = 12.u),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value, onValueChange, singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 15.us, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (last) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { fm.moveFocus(FocusDirection.Down) }, onDone = { kb?.hide(); onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.isSelect()) { kb?.show(); true } else false },
            )
        }
    }
}
