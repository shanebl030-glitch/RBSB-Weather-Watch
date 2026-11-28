// Small animated weather icons for every weather code, as on the desktop (scene.paint_icon):
// the sun's rays turn, clouds drift, rain and snow fall (more and faster in heavy rain), fog
// rolls, thunderstorms flash, and windy days add sweeping gusts. Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/ui/scene/Icons.kt (Stage 3, forecast icons
// deferred from Stage 2). Watch change: package name only.
package io.github.shanebl030glitch.rbsbweather.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun drawCloud(s: DrawScope, cx: Float, cy: Float, sz: Float, dark: Double = 0.0) = with(s) {
    val top = mix(Color(255, 255, 255), Color(140, 148, 160), dark)
    val bot = mix(Color(200, 208, 220), Color(90, 96, 108), dark)
    fun oval(l: Float, t: Float, r: Float, b: Float) = Path().apply { addOval(Rect(l, t, r, b)) }
    val p1 = oval(cx - sz * 0.62f, cy - sz * 0.26f, cx - sz * 0.02f, cy + sz * 0.26f)
    val p2 = oval(cx - sz * 0.34f, cy - sz * 0.52f, cx + sz * 0.38f, cy + sz * 0.16f)
    val p3 = oval(cx + sz * 0.10f, cy - sz * 0.22f, cx + sz * 0.62f, cy + sz * 0.26f)
    val p4 = Path().apply { addRoundRect(RoundRect(Rect(cx - sz * 0.6f, cy - sz * 0.02f, cx + sz * 0.6f, cy + sz * 0.28f), CornerRadius(sz * 0.15f))) }
    val u1 = Path().apply { op(p1, p2, PathOperation.Union) }
    val u2 = Path().apply { op(u1, p3, PathOperation.Union) }
    val p = Path().apply { op(u2, p4, PathOperation.Union) }
    drawPath(p, Brush.verticalGradient(listOf(top, bot), startY = cy - sz * 0.5f, endY = cy + sz * 0.35f))
    drawPath(p, Color(0, 0, 0, 35), style = Stroke(max(0.6f, sz * 0.02f)))
}

private fun drawSunIcon(s: DrawScope, cx: Float, cy: Float, rad: Float, t: Double) = with(s) {
    translate(cx, cy) {
        val base = (t * 30).toFloat() % 360f
        for (i in 1..8) rotate(base + 45f * i, Offset.Zero) {
            drawLine(Color(255, 186, 40), Offset(0f, -rad * 1.35f), Offset(0f, -rad * (1.75f + 0.12f * sin(t * 4).toFloat())),
                max(1f, rad * 0.18f), StrokeCap.Round)
        }
    }
    drawCircle(safeRadial(listOf(Color(255, 240, 150), Color(255, 176, 30)), Offset(cx - rad * 0.3f, cy - rad * 0.3f), rad * 1.4f), rad, Offset(cx, cy))
}

private fun drawMoonIcon(s: DrawScope, cx: Float, cy: Float, rad: Float) = with(s) {
    val disc = Path().apply { addOval(Rect(Offset(cx, cy), rad)) }
    val bite = Path().apply { addOval(Rect(Offset(cx + rad * 0.45f, cy - rad * 0.25f), rad * 0.85f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, Color(240, 236, 210))
}

private fun drawWindIcon(s: DrawScope, r: Rect, t: Double) = with(s) {
    val sz = min(r.width, r.height)
    listOf(0.70f to 0.62f, 0.82f to 0.78f, 0.93f to 0.5f).forEachIndexed { i, (y, length) ->
        val ph = ((t * 0.55 + i * 0.33) % 1.0).toFloat()
        val x0 = r.left + r.width * (-0.35f + 0.75f * ph)
        val a = (230 * sin(PI * ph)).toInt().coerceIn(0, 255)
        val yy = r.top + r.height * y
        val l = r.width * length
        val p = Path().apply {
            moveTo(x0, yy)
            cubicTo(x0 + l * 0.35f, yy - sz * 0.05f, x0 + l * 0.65f, yy + sz * 0.05f, x0 + l, yy - sz * 0.02f)
            arcTo(Rect(x0 + l - sz * 0.07f, yy - sz * 0.16f, x0 + l + sz * 0.07f, yy - sz * 0.02f), 90f, -250f, false)
        }
        drawPath(p, Color(235, 242, 252, a), style = Stroke(max(1f, sz * 0.055f), cap = StrokeCap.Round))
    }
}

/** An animated icon for any weather code in rect at animation time t; windy adds gusts underneath. */
fun DrawScope.weatherIcon(r: Rect, code: Int?, isDay: Boolean, t: Double, windy: Boolean = false) {
    if (windy) {
        weatherIcon(Rect(r.left, r.top, r.left + r.width * 0.86f, r.top + r.height * 0.74f), code, isDay, t)
        clipRect(r.left, r.top, r.right, r.bottom) { drawWindIcon(this, r, t) }
        return
    }
    val (kind, inten) = classify(code)
    val s = min(r.width, r.height)
    val cx = r.center.x; val cy = r.center.y
    clipRect(r.left, r.top, r.right, r.bottom) {
        when (kind) {
            Kind.CLEAR -> if (isDay) drawSunIcon(this, cx, cy, s * 0.22f, t) else drawMoonIcon(this, cx, cy, s * 0.3f)
            Kind.PARTLY -> {
                if (isDay) drawSunIcon(this, cx - s * 0.14f, cy - s * 0.14f, s * 0.17f, t) else drawMoonIcon(this, cx - s * 0.14f, cy - s * 0.16f, s * 0.2f)
                drawCloud(this, cx + s * 0.06f + sin(t * 0.8).toFloat() * s * 0.03f, cy + s * 0.12f, s * 0.62f)
            }
            Kind.OVERCAST -> {
                drawCloud(this, cx - s * 0.12f + sin(t * 0.6).toFloat() * s * 0.02f, cy - s * 0.1f, s * 0.5f, 0.25)
                drawCloud(this, cx + s * 0.05f + sin(t * 0.8 + 1).toFloat() * s * 0.03f, cy + s * 0.08f, s * 0.66f, 0.1)
            }
            Kind.FOG -> {
                drawCloud(this, cx, cy - s * 0.12f, s * 0.6f, 0.15)
                for (i in 0 until 3) {
                    val off = sin(t * 1.2 + i).toFloat() * s * 0.08f
                    val y = cy + s * (0.16f + 0.1f * i)
                    drawLine(Color(170, 178, 190), Offset(cx - s * 0.32f + off, y), Offset(cx + s * 0.32f + off, y), max(1f, s * 0.05f), StrokeCap.Round)
                }
            }
            else -> {
                val dark = when (kind) { Kind.DRIZZLE -> 0.15; Kind.RAIN -> 0.3; Kind.SNOW -> 0.1; else -> 0.55 }
                drawCloud(this, cx, cy - s * 0.14f, s * 0.66f, dark)
                val top = cy + s * 0.08f; val bottom = r.bottom
                if (kind == Kind.RAIN || kind == Kind.DRIZZLE || kind == Kind.THUNDER) {
                    val heavy = kind != Kind.DRIZZLE && inten >= 0.8
                    val n = if (kind != Kind.DRIZZLE) (if (heavy) 5 else 3) else 4
                    val width = max(1f, s * (if (kind != Kind.DRIZZLE) 0.05f else 0.035f))
                    val speed = if (kind != Kind.DRIZZLE) (if (heavy) 2.1 else 1.6) else 1.0
                    for (i in 0 until n) {
                        val ph = ((t * speed + i.toDouble() / n) % 1.0).toFloat()
                        val x = cx + (i - (n - 1) / 2f) * s * (if (n <= 4) 0.18f else 0.12f) - ph * s * 0.06f
                        val y = top + ph * (bottom - top)
                        val l = s * (if (kind != Kind.DRIZZLE) 0.12f else 0.06f)
                        drawLine(Color(70, 140, 230), Offset(x, y), Offset(x + s * 0.03f, y - l), width, StrokeCap.Round)
                    }
                }
                if (kind == Kind.SNOW) {
                    for (i in 0 until 3) {
                        val ph = ((t * 0.6 + i / 3.0) % 1.0).toFloat()
                        val x = cx + (i - 1) * s * 0.2f + sin(t * 2 + i).toFloat() * s * 0.04f
                        drawCircle(Color(130, 170, 220), s * 0.045f, Offset(x, top + ph * (bottom - top)))
                    }
                }
                if (kind == Kind.THUNDER && (t % 2.2) < 0.45) {
                    val p = Path().apply {
                        moveTo(cx + s * 0.02f, cy - s * 0.02f); lineTo(cx - s * 0.1f, cy + s * 0.2f); lineTo(cx, cy + s * 0.2f)
                        lineTo(cx - s * 0.08f, cy + s * 0.42f); lineTo(cx + s * 0.14f, cy + s * 0.12f); lineTo(cx + s * 0.03f, cy + s * 0.12f); close()
                    }
                    drawPath(p, Color(255, 210, 40))
                }
            }
        }
    }
}

/** A small animated icon (forecast rows), 12 frames a second like the desktop's. */
@Composable
fun AnimatedWeatherIcon(code: Int?, isDay: Boolean = true, size: Dp = 34.dp, phase: Double = 0.0, animate: Boolean = true,
                        windy: Boolean = false, modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(phase.toFloat()) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameNanos { it }
        var last = 0L
        while (true) withFrameNanos { now ->
            if (now - last >= 83_000_000L) { last = now; t = phase.toFloat() + (now - start) / 1e9f }
        }
    }
    Canvas(modifier.size(size)) { weatherIcon(Rect(1f, 1f, this.size.width - 1, this.size.height - 1), code, isDay, t.toDouble(), windy) }
}

