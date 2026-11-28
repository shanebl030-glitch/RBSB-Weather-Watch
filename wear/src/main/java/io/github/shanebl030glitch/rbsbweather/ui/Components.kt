// Building blocks for the Stage 3 screens: forecast rows, detail rows, warning cards, the wind
// compass, the real Moon picture and rotary (bezel or crown) scrolling. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import io.github.shanebl030glitch.rbsbweather.scene.AnimatedWeatherIcon
import io.github.shanebl030glitch.rbsbweather.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.sky.RotaryInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bezel or crown scrolling for a list. Turns arrive through Compose (when the list has focus) and
 * through the activity's fallback (RotaryInput); quick turns are added together and scrolled in
 * one smooth movement, so nothing is lost and the list doesn't jump.
 */
@Composable
fun Modifier.rotaryScroll(state: ScrollableState, active: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    val pending = remember { Channel<Float>(Channel.UNLIMITED) }
    LaunchedEffect(state, active) {
        if (!active) return@LaunchedEffect
        try { focus.requestFocus() } catch (e: Exception) { }
        launch { for (px in RotaryInput.events) pending.trySend(px) }
        for (px in pending) {
            var total = px
            while (true) total += pending.tryReceive().getOrNull() ?: break
            state.animateScrollBy(total, tween(durationMillis = 140, easing = LinearOutSlowInEasing))
        }
    }
    return this
        .onRotaryScrollEvent { e -> RotaryInput.seen(e.verticalScrollPixels); pending.trySend(e.verticalScrollPixels); true }
        .focusRequester(focus)
        .focusable()
}

/**
 * Fades list content out towards the top and bottom of the round screen, where the circle cuts
 * rows short, so text reaches the edge softly instead of being clipped mid-letter.
 */
fun Modifier.edgeFade(enabled: Boolean = true, fraction: Float = 0.14f): Modifier = if (!enabled) this else this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val band = size.height * fraction
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = band),
            size = androidx.compose.ui.geometry.Size(size.width, band), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - band, endY = size.height),
            topLeft = Offset(0f, size.height - band), size = androidx.compose.ui.geometry.Size(size.width, band), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/** One forecast hour: time, icon, temperature, rain chance (and amount when it rains). */
@Composable
fun HourRowView(r: HourRow, animate: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.time, style = if (r.time == "Now") Type.RowBold else Type.Row, modifier = Modifier.weight(1.15f), maxLines = 1)
        AnimatedWeatherIcon(r.code, r.isDay, size = 26.dp, animate = animate)
        Text(r.temp, style = Type.RowBold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End, maxLines = 1)
        Column(Modifier.weight(1.05f), horizontalAlignment = Alignment.End) {
            Text(r.rainChance, style = TextStyle(fontSize = 13.sp, color = Palette.Rain), maxLines = 1)
            r.rainAmount?.let { Text(it, style = TextStyle(fontSize = 10.sp, color = Palette.Rain), maxLines = 1) }
        }
    }
}

/** One forecast day: day, icon, high/low, rain chance. Tappable for the day's details. */
@Composable
fun DayRowView(r: DayRow, animate: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(42.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(r.label, style = if (r.label == "Today") Type.RowBold else Type.Row, modifier = Modifier.weight(1.1f), maxLines = 1)
        AnimatedWeatherIcon(r.code, true, size = 26.dp, animate = animate)
        Row(Modifier.weight(1.35f), horizontalArrangement = Arrangement.End) {
            Text(r.high, style = Type.RowBold, maxLines = 1)
            Text("/" + r.low, style = TextStyle(fontSize = 15.sp, color = Palette.Quiet), maxLines = 1)
        }
        Text(r.rainChance, style = TextStyle(fontSize = 13.sp, color = Palette.Rain), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, maxLines = 1)
    }
}

/** A label and value on one line. */
@Composable
fun DetailRowView(d: Detail, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(d.label, style = TextStyle(fontSize = 14.sp, color = Palette.Quiet), modifier = Modifier.weight(1f), maxLines = 1)
        Text(d.value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.Text), maxLines = 1, textAlign = TextAlign.End)
    }
}

/** A rounded card. */
@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Palette.Card, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth().background(color, RoundedCornerShape(20.dp))
    Box((if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
}

/** A warning in the list: coloured by level, tappable for details. */
@Composable
fun WarningCardView(w: WarningCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = Palette.warning(w.level)
    Card(modifier, bg, onClick) {
        Column {
            Text(w.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val where = listOf(w.area, w.span).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) Text(where, style = TextStyle(fontSize = 12.sp, color = fg), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The wind compass: N/E/S/W ring and an arrow pointing the way the wind blows to. */
@Composable
fun WindCompass(directionFrom: Double?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f - 2f
            val c = center
            drawCircle(Color(0xFF3A4050), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            for (k in 0 until 36) {
                val a = Math.toRadians(k * 10.0)
                val l = if (k % 9 == 0) r * 0.14f else r * 0.07f
                drawLine(Color(0xFF7A8496), Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat()),
                    Offset(c.x + ((r - l) * sin(a)).toFloat(), c.y - ((r - l) * cos(a)).toFloat()), 2f)
            }
            if (directionFrom != null) rotate((directionFrom + 180).toFloat(), c) {
                val tail = Offset(c.x, c.y + r * 0.62f); val head = Offset(c.x, c.y - r * 0.62f)
                drawLine(Palette.Wind, tail, Offset(head.x, head.y + r * 0.2f), r * 0.07f, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(head.x, head.y); lineTo(head.x - r * 0.15f, head.y + r * 0.28f); lineTo(head.x + r * 0.15f, head.y + r * 0.28f); close()
                }
                drawPath(p, Palette.Wind)
            }
        }
        // the cardinal points, upright
        val off = size / 2 - 13.dp
        Text("N", Modifier.padding(bottom = off * 2), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.TextSoft))
        Text("S", Modifier.padding(top = off * 2), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.TextSoft))
        Text("E", Modifier.padding(start = off * 2), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.TextSoft))
        Text("W", Modifier.padding(end = off * 2), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.TextSoft))
    }
}

/**
 * The real Moon picture (the shared NASA renderer, decisions D7 to D10), made off the main thread
 * and shown as soon as it is ready. sizeDp: the size on screen; rendered at full pixel resolution.
 */
@Composable
fun MoonPicture(phaseAngle: Double, brightLimb: Double, rotation: Double, sizeDp: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember { try { MoonRender.init(ctx.applicationContext.assets) } catch (e: Exception) { }; true }
    val px = with(LocalDensity.current) { sizeDp.toPx() }.roundToInt().coerceIn(16, MoonRender.CACHE_MAX_SIZE)
    MoonRender.ready.intValue                                          // redraw when a picture is ready
    val bmp = MoonRender.renderLater(px, phaseAngle, brightLimb, rotation = rotation)
    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = "The Moon", modifier = modifier.size(sizeDp))
    else Box(modifier.size(sizeDp))
}
