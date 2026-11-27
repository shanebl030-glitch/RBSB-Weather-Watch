// The first screen: the sky for a snapshot, with the place, temperature and conditions over it.
// Stage 2 milestone 1 (for Shane's design review): a still picture of the sky drawn by the copied
// scene code from Stage 1 sample data, labelled "Sample data"; tap or turn the bezel/crown to
// switch samples. The animation, quality levels and frame timing follow after the review.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.scene.SceneEngine
import io.github.shanebl030glitch.rbsbweather.scene.conditionsFromSnapshot
import io.github.shanebl030glitch.rbsbweather.scene.drawScene
import io.github.shanebl030glitch.rbsbweather.scene.safeRadial
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** The words shown over the sky (kept separate so they can be unit-tested on the JVM). */
object SkyText {
    const val SAMPLE_LABEL = "SAMPLE DATA"

    fun temperature(c: Double?): String = if (c == null || !c.isFinite()) "--°" else "${c.roundToInt()}°"

    fun condition(s: Snapshot): String = WeatherCodes.describe(s.current.weatherCode)

    /** Today's high and low, or null when the snapshot has no daily forecast for today. */
    fun highLow(s: Snapshot): String? {
        val today = s.daily.firstOrNull { it.date == s.current.time.take(10) } ?: s.daily.firstOrNull() ?: return null
        if (today.tempMax == null && today.tempMin == null) return null
        return "H ${temperature(today.tempMax)}  L ${temperature(today.tempMin)}"
    }

    fun sampleLine(index: Int, count: Int, name: String): String = "${index + 1}/$count · $name"
}

private val Shade = Shadow(Color(0xCC000000), Offset(0f, 1.5f), 4f)

@Composable
fun SkyScreen(samples: List<Sample>, initialIndex: Int = 0) {
    val ctx = LocalContext.current
    remember { try { MoonRender.init(ctx.applicationContext.assets) } catch (e: Exception) { }; true }
    if (samples.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No sample data", textAlign = TextAlign.Center)
        }
        return
    }
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, samples.size - 1)) }
    val sample = samples[index]
    val snap = sample.snapshot
    val engine = remember { SceneEngine(seed = 7) }
    engine.animations = false                        // milestone 1: a still picture
    engine.clock = { snap.fetchedAt }                 // the sample's own moment, so its day or night shows
    val cond = remember(index) { conditionsFromSnapshot(snap, snap.fetchedAt) }

    val focus = remember { FocusRequester() }
    var rotary by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
    fun step(by: Int) { index = (index + by + samples.size) % samples.size }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("sky")
            .pointerInput(samples.size) { detectTapGestures(onTap = { step(1) }) }
            .onRotaryScrollEvent { e ->
                rotary += e.verticalScrollPixels
                if (abs(rotary) > 60f) { step(if (rotary > 0) 1 else -1); rotary = 0f }
                true
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            engine.setSize(size.width.toInt(), size.height.toInt())
            if (engine.cond !== cond) engine.setConditions(cond)
            drawScene(engine, scrim = false)
            // readability: a soft dark centre behind the text, fading out towards the edge
            val r = size.minDimension * 0.5f
            drawCircle(safeRadial(0f to Color(0x73000000), 0.62f to Color(0x40000000), 1f to Color(0x00000000),
                center = center, radius = r), r, center)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(6.dp))
            Text(snap.place.name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = Shade),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(SkyText.temperature(snap.current.temperature),
                style = TextStyle(fontSize = 50.sp, fontWeight = FontWeight.Light, color = Color.White, shadow = Shade))
            Text(SkyText.condition(snap), style = TextStyle(fontSize = 14.sp, color = Color.White, shadow = Shade),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            SkyText.highLow(snap)?.let {
                Text(it, style = TextStyle(fontSize = 13.sp, color = Color(0xFFE6ECF2), shadow = Shade))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(SkyText.SAMPLE_LABEL, modifier = Modifier
                .background(Color(0xFFFFC94D), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.dp),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A1F00), letterSpacing = 0.8.sp))
            Text(SkyText.sampleLine(index, samples.size, sample.name),
                style = TextStyle(fontSize = 9.sp, color = Color(0xFFDDE3EA), shadow = Shade), maxLines = 1)
        }
    }
}
