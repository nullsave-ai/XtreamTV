package com.xtreamtv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*

/** Design tokens: dark / AMOLED base with a gold accent. */
object C {
    val bg = Color(0xFF06080C)
    val surface = Color(0xFF0F131A)
    val surfaceHi = Color(0xFF1C2431)
    val glass = Color(0x1AFFFFFF)
    val glassBorder = Color(0x26FFFFFF)
    val scrim = Color(0xE6070A0F)
    val accent = Color(0xFFE3B35A)
    val accentHi = Color(0xFFF2C970)
    val blue = Color(0xFF4C8DFF)
    val text = Color(0xFFF3F5F9)
    val text2 = Color(0xFFA6AFBE)
    val text3 = Color(0xFF6C7686)
    val danger = Color(0xFFFF5C63)
    val ok = Color(0xFF3ED598)
}

/** All sizes are authored for a 960dp-wide screen and scaled proportionally (720p/1080p/4K look identical). */
val LocalUiScale = compositionLocalOf { 1f }

val Int.u: Dp @Composable @ReadOnlyComposable get() = (this * LocalUiScale.current).dp
val Float.u: Dp @Composable @ReadOnlyComposable get() = (this * LocalUiScale.current).dp
val Int.us: TextUnit @Composable @ReadOnlyComposable get() = (this * LocalUiScale.current).sp
val Float.us: TextUnit @Composable @ReadOnlyComposable get() = (this * LocalUiScale.current).sp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / 960f).coerceIn(0.7f, 2.4f)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalUiScale provides scale,
        LocalDensity provides Density(density.density, 1f), // ignore system font scale: the layout is tuned for TV
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
    ) {
        Box(Modifier.fillMaxSize().background(C.bg)) { content() }
    }
}
