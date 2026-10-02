@file:OptIn(UnstableApi::class)

package com.xtreamtv.player

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Video surface only: all controls are drawn in Compose. */
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setKeepContentOnPlayerReset(true) // keep the last frame while zapping
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                isFocusable = false
                isFocusableInTouchMode = false
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
