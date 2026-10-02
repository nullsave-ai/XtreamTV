package com.xtreamtv.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Canvas-drawn icons on a 24x24 grid: no icon fonts, no emoji, no icon libraries. */
enum class Ic {
    Home, Live, Movies, Series, Heart, HeartFill, Search, User, Settings, Play, Pause, Next, Prev, Rew, Fwd,
    Back, Plus, Trash, Edit, Refresh, Star, Audio, Subs, Quality, Check, Close, Guide, Info, ChevL, ChevR
}

@Composable
fun Icon(ic: Ic, size: Int = 20, tint: Color = C.text, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.size(size.u)) {
        Canvas(Modifier.size(size.u)) { drawIc(ic, tint) }
    }
}

fun DrawScope.drawIc(ic: Ic, c: Color) {
    val k = size.minDimension / 24f
    val sw = 2f * k
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun P(b: Path.() -> Unit) = Path().apply(b)
    fun Path.m(x: Float, y: Float) = moveTo(x * k, y * k)
    fun Path.l(x: Float, y: Float) = lineTo(x * k, y * k)
    fun Path.cu(a: Float, b: Float, c2: Float, d: Float, e: Float, f: Float) = cubicTo(a * k, b * k, c2 * k, d * k, e * k, f * k)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), sw, StrokeCap.Round)
    fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean) =
        drawRoundRect(c, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(r * k), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke)
    fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
        drawPath(P { m(ax, ay); l(bx, by); l(cx, cy); close() }, c)
    fun heart() = P {
        m(12f, 20.5f); cu(5f, 15f, 2.5f, 11.5f, 2.5f, 8.5f); cu(2.5f, 5.5f, 4.8f, 3.5f, 7.3f, 3.5f)
        cu(9.3f, 3.5f, 11f, 4.5f, 12f, 6.3f); cu(13f, 4.5f, 14.7f, 3.5f, 16.7f, 3.5f)
        cu(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f); cu(21.5f, 11.5f, 19f, 15f, 12f, 20.5f); close()
    }
    when (ic) {
        Ic.Home -> drawPath(P { m(12f, 3f); l(21.5f, 11.5f); l(19f, 11.5f); l(19f, 20f); l(14.5f, 20f); l(14.5f, 14.5f); l(9.5f, 14.5f); l(9.5f, 20f); l(5f, 20f); l(5f, 11.5f); l(2.5f, 11.5f); close() }, c)
        Ic.Live -> { rr(3f, 7f, 18f, 13f, 2.5f, false); line(8f, 3f, 12f, 7f); line(16f, 3f, 12f, 7f) }
        Ic.Movies -> {
            rr(3f, 4f, 18f, 16f, 2f, false); line(8f, 4f, 8f, 20f); line(16f, 4f, 16f, 20f)
            line(3f, 9f, 8f, 9f); line(3f, 15f, 8f, 15f); line(16f, 9f, 21f, 9f); line(16f, 15f, 21f, 15f)
        }
        Ic.Series -> { line(7f, 3.5f, 17f, 3.5f); line(5f, 7f, 19f, 7f); rr(3f, 10f, 18f, 11f, 2f, false); tri(10.5f, 13f, 15.5f, 15.5f, 10.5f, 18f) }
        Ic.Heart -> drawPath(heart(), c, style = stroke)
        Ic.HeartFill -> drawPath(heart(), c)
        Ic.Search -> { drawCircle(c, 6.5f * k, Offset(10.5f * k, 10.5f * k), style = stroke); line(15.5f, 15.5f, 21f, 21f) }
        Ic.User -> {
            drawCircle(c, 4f * k, Offset(12f * k, 8f * k), style = stroke)
            drawPath(P { m(4f, 21f); cu(4f, 16f, 8f, 14f, 12f, 14f); cu(16f, 14f, 20f, 16f, 20f, 21f) }, c, style = stroke)
        }
        Ic.Settings -> {
            drawCircle(c, 3f * k, Offset(12f * k, 12f * k), style = stroke)
            drawCircle(c, 7f * k, Offset(12f * k, 12f * k), style = stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                line(12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat(), 12f + 9.8f * cos(a).toFloat(), 12f + 9.8f * sin(a).toFloat())
            }
        }
        Ic.Play -> tri(7f, 4.5f, 19.5f, 12f, 7f, 19.5f)
        Ic.Pause -> { rr(6f, 4.5f, 4f, 15f, 1f, true); rr(14f, 4.5f, 4f, 15f, 1f, true) }
        Ic.Next -> { tri(5f, 5f, 15f, 12f, 5f, 19f); rr(17f, 5f, 3f, 14f, 1f, true) }
        Ic.Prev -> { tri(19f, 5f, 9f, 12f, 19f, 19f); rr(4f, 5f, 3f, 14f, 1f, true) }
        Ic.Rew -> { tri(11.5f, 5.5f, 2.5f, 12f, 11.5f, 18.5f); tri(21.5f, 5.5f, 12.5f, 12f, 21.5f, 18.5f) }
        Ic.Fwd -> { tri(12.5f, 5.5f, 21.5f, 12f, 12.5f, 18.5f); tri(2.5f, 5.5f, 11.5f, 12f, 2.5f, 18.5f) }
        Ic.Back -> { line(20f, 12f, 4f, 12f); drawPath(P { m(10f, 6f); l(4f, 12f); l(10f, 18f) }, c, style = stroke) }
        Ic.ChevL -> drawPath(P { m(15f, 5f); l(8f, 12f); l(15f, 19f) }, c, style = stroke)
        Ic.ChevR -> drawPath(P { m(9f, 5f); l(16f, 12f); l(9f, 19f) }, c, style = stroke)
        Ic.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ic.Trash -> { rr(6f, 8f, 12f, 13f, 2f, false); line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f); line(10f, 11f, 10f, 18f); line(14f, 11f, 14f, 18f) }
        Ic.Edit -> drawPath(P { m(4f, 20f); l(5f, 15f); l(16f, 4f); l(20f, 8f); l(9f, 19f); close() }, c, style = stroke)
        Ic.Refresh -> {
            drawArc(c, -50f, 290f, false, Offset(4f * k, 4f * k), Size(16f * k, 16f * k), style = stroke)
            tri(21f, 3f, 21f, 9.5f, 14.5f, 9.5f)
        }
        Ic.Star -> drawPath(P {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 9.5f else 4f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = 12f + r * cos(a).toFloat(); val y = 12.5f + r * sin(a).toFloat()
                if (i == 0) m(x, y) else l(x, y)
            }
            close()
        }, c)
        Ic.Audio -> {
            drawPath(P { m(3f, 9f); l(7f, 9f); l(12f, 4.5f); l(12f, 19.5f); l(7f, 15f); l(3f, 15f); close() }, c)
            drawArc(c, -45f, 90f, false, Offset(7f * k, 7f * k), Size(10f * k, 10f * k), style = stroke)
            drawArc(c, -45f, 90f, false, Offset(3.5f * k, 3.5f * k), Size(17f * k, 17f * k), style = stroke)
        }
        Ic.Subs -> { rr(3f, 5f, 18f, 14f, 2.5f, false); line(7f, 11f, 10.5f, 11f); line(13f, 11f, 17f, 11f); line(7f, 15f, 12f, 15f); line(14.5f, 15f, 17f, 15f) }
        Ic.Quality -> {
            rr(2.5f, 6f, 19f, 12f, 2.5f, false)
            line(7f, 9.5f, 7f, 14.5f); line(7f, 12f, 10f, 12f); line(10f, 9.5f, 10f, 14.5f)
            line(13f, 9.5f, 13f, 14.5f); drawPath(P { m(13f, 9.5f); cu(18.5f, 9.5f, 18.5f, 14.5f, 13f, 14.5f) }, c, style = stroke)
        }
        Ic.Check -> drawPath(P { m(5f, 12.5f); l(10f, 17.5f); l(19f, 7f) }, c, style = stroke)
        Ic.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.Guide -> { rr(3f, 4f, 18f, 16f, 2f, false); line(3f, 9.5f, 21f, 9.5f); line(9.5f, 9.5f, 9.5f, 20f) }
        Ic.Info -> { drawCircle(c, 9f * k, Offset(12f * k, 12f * k), style = stroke); line(12f, 11f, 12f, 17f); drawCircle(c, 1.2f * k, Offset(12f * k, 7.6f * k)) }
    }
}
