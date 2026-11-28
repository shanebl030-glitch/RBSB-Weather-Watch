// The first screen: the live animated sky for a snapshot, with the place, temperature and
// conditions over it (Stage 2). Until Stage 3 it shows the Stage 1 sample data, labelled
// "SAMPLE DATA"; tap, or turn the bezel/crown, to switch samples.
//  - The sky animates only while this page is on screen and the app is in front (it stops in the
//    background and when the watch dims; the always-on still sky is Stage 4).
//  - Aims for 30 fps; QualityController picks High, Standard or Light detail from measured frame
//    times, and each 10-second window is recorded (SkyStats) for review at Stage 7.
//  - The text-readability shade is drawn under the Sun, Moon and clouds, so the Sun stays bright.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import android.content.Context
import android.os.PowerManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material.Text
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.scene.QualityController
import io.github.shanebl030glitch.rbsbweather.scene.SceneEngine
import io.github.shanebl030glitch.rbsbweather.scene.conditionsFromSnapshot
import io.github.shanebl030glitch.rbsbweather.scene.drawScene
import io.github.shanebl030glitch.rbsbweather.scene.safeRadial
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** The words shown over the sky (kept separate so they can be unit-tested on the JVM). */
object SkyText {
    const val SAMPLE_LABEL = "SAMPLE DATA"

    /** Below this width (dp) the sample names are shortened, so they stay inside a 432 px circle. */
    const val NARROW_DP = 230

    private val SHORT = mapOf(
        "partly-cloudy-day" to "partly cloudy", "rain-night-warnings" to "rain + warnings", "thunder-warning" to "thunder",
        "snow-northern" to "snow", "hourly-wind-mixed" to "hourly wind", "times-with-seconds" to "seconds",
        "future-within-tolerance" to "future time", "unknown-fields" to "extra fields", "explicit-nulls" to "no values",
        "utc-timezone" to "UTC", "polar-night" to "polar night", "clear-night" to "clear night", "clear-day" to "clear day",
    )

    fun temperature(c: Double?): String = if (c == null || !c.isFinite()) "--°" else "${c.roundToInt()}°"

    fun condition(s: Snapshot): String = WeatherCodes.describe(s.current.weatherCode)

    /** Today's high and low, or null when the snapshot has no daily forecast for today. */
    fun highLow(s: Snapshot): String? {
        val today = s.daily.firstOrNull { it.date == s.current.time.take(10) } ?: s.daily.firstOrNull() ?: return null
        if (today.tempMax == null && today.tempMin == null) return null
        return "H ${temperature(today.tempMax)}  L ${temperature(today.tempMin)}"
    }

    fun shortName(name: String): String = SHORT[name] ?: name

    fun sampleLine(index: Int, count: Int, name: String, narrow: Boolean = false): String =
        "${index + 1}/$count · ${if (narrow) shortName(name) else name}"
}

private val Shade = Shadow(Color(0xCC000000), Offset(0f, 1.5f), 5f)

@Composable
fun SkyScreen(samples: List<Sample>, initialIndex: Int = 0, active: Boolean = true, animate: Boolean = true) {
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
    val controller = remember { QualityController() }
    val power = remember { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val lifecycle = LocalLifecycleOwner.current
    engine.animations = animate

    // The sample's clock runs on from its own moment, so its day or night shows and the Sun moves.
    val shownAt = remember(index) { System.currentTimeMillis() }
    engine.clock = { snap.fetchedAt + (System.currentTimeMillis() - shownAt) }
    var cond by remember(index) { mutableStateOf(conditionsFromSnapshot(snap, snap.fetchedAt)) }
    LaunchedEffect(index) {
        while (isActive) {                                   // the Sun and Moon move on each minute
            delay(60_000)
            cond = conditionsFromSnapshot(snap, engine.clock())
        }
    }

    // The animation loop: only while this page is shown and the app is in front.
    var frame by remember { mutableLongStateOf(0L) }
    val drawMs = remember { DoubleArray(1) }
    LaunchedEffect(active, animate) {
        if (!active || !animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            controller.resetWindow()
            controller.powerSave = power?.isPowerSaveMode == true
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1e9
                if (dt < controller.frameInterval - 0.004) continue      // pace to the target frame rate
                last = now
                val t0 = System.nanoTime()
                engine.quality = controller.quality
                engine.advance(min(dt, 0.25))
                val workMs = (System.nanoTime() - t0) / 1e6 + drawMs[0]
                frame++
                controller.onFrame(dt, workMs, System.currentTimeMillis())?.let { stats ->
                    withContext(Dispatchers.IO) { SkyStats.record(stats, ctx.filesDir) }
                    controller.powerSave = power?.isPowerSaveMode == true
                }
            }
        }
    }

    val focus = remember { FocusRequester() }
    var rotary by remember { mutableFloatStateOf(0f) }
    fun step(by: Int) { index = (index + by + samples.size) % samples.size }
    fun turn(pixels: Float) {
        rotary += pixels
        if (abs(rotary) >= RotaryInput.STEP_PIXELS) { step(if (rotary > 0) 1 else -1); rotary = 0f }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        try { focus.requestFocus() } catch (e: Exception) { }
        for (px in RotaryInput.events) turn(px)              // rotary events no view took (see RotaryInput)
    }
    val narrow = LocalConfiguration.current.screenWidthDp < SkyText.NARROW_DP

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("sky")
            .pointerInput(samples.size) { detectTapGestures(onTap = { step(1) }) }
            .onRotaryScrollEvent { e -> turn(e.verticalScrollPixels); true }
            .focusRequester(focus)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame                                            // redraw on each animation frame
            val t0 = System.nanoTime()
            engine.setSize(size.width.toInt(), size.height.toInt())
            if (engine.cond !== cond) engine.setConditions(cond)
            drawScene(engine, scrim = false, underSun = {
                // readability: a soft dark centre behind the text, under the Sun, Moon and clouds
                val r = size.minDimension * 0.5f
                drawCircle(safeRadial(0f to Color(0x66000000), 0.6f to Color(0x38000000), 1f to Color(0x00000000),
                    center = center, radius = r), r, center)
            })
            drawMs[0] = (System.nanoTime() - t0) / 1e6
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
            Text(SkyText.sampleLine(index, samples.size, sample.name, narrow), modifier = Modifier.widthIn(max = 120.dp),
                style = TextStyle(fontSize = 9.sp, color = Color(0xFFDDE3EA), shadow = Shade),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
