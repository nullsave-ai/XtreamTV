package com.xtreamtv.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import com.xtreamtv.design.*
import com.xtreamtv.domain.LiveChannel

@Composable
fun PosterCard(
    title: String,
    image: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    favorite: Boolean = false,
    progress: Float? = null,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier) {
        FocusBox(
            onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick, onFocus = onFocus,
            shape = RoundedCornerShape(10.u), focusScale = 1.07f, focusRequester = focusRequester,
        ) {
            Img(image, Modifier.fillMaxSize().clip(RoundedCornerShape(10.u)))
            if (favorite) Box(Modifier.align(Alignment.TopStart).padding(6.u)) { Icon(Ic.HeartFill, 16, C.accent) }
            if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.u))
        }
        Spacer(Modifier.height(6.u))
        UText(title, 12, FontWeight.Medium)
        if (meta != null) UText(meta, 11, color = C.text3)
    }
}

@Composable
fun ChannelCard(
    ch: LiveChannel,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier) {
        FocusBox(
            onClick, Modifier.fillMaxWidth().aspectRatio(16f / 10f), onLongClick = onLongClick, onFocus = onFocus,
            shape = RoundedCornerShape(10.u), focusScale = 1.06f, focusRequester = focusRequester,
        ) {
            ChannelLogo(ch.logo, ch.name, Modifier.fillMaxSize().padding(14.u))
            UText(ch.num.toString(), 10, FontWeight.Bold, C.text3, Modifier.align(Alignment.TopEnd).padding(6.u))
            if (favorite) Box(Modifier.align(Alignment.TopStart).padding(6.u)) { Icon(Ic.HeartFill, 14, C.accent) }
        }
        Spacer(Modifier.height(6.u))
        UText(ch.name, 12, FontWeight.Medium)
        if (subtitle != null) UText(subtitle, 11, color = C.text3)
    }
}

/** Generic poster grid. The item whose index equals [startIndex] gets the entry focus. */
@Composable
fun <T> PosterGrid(
    items: List<T>,
    keyOf: (T) -> Any,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    minCell: Int = 112,
    startIndex: Int = 0,
    emptyText: String = "لا يوجد محتوى",
    focusOnEntry: Boolean = true,
    card: @Composable (item: T, focusRequester: FocusRequester?) -> Unit,
) {
    if (items.isEmpty()) { EmptyMessage(emptyText, modifier, Ic.Search); return }
    val fr = remember { FocusRequester() }
    if (focusOnEntry) RequestFocusOnce(fr)
    val start = startIndex.coerceIn(0, items.lastIndex)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCell.u), state = state, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.u, vertical = 22.u),
        horizontalArrangement = Arrangement.spacedBy(16.u),
        verticalArrangement = Arrangement.spacedBy(18.u),
    ) {
        itemsIndexed(items, key = { _, it -> keyOf(it) }) { i, it -> card(it, if (i == start) fr else null) }
    }
}
