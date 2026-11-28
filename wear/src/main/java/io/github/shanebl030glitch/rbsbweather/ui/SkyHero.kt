// The first screen: the live animated sky with "now" over it (Stage 2's sky, Stage 3's data).
//  - The sky animates only while it is on screen and the app is in front (it stops when scrolled
//    away, in the background and when the watch dims; the always-on still sky is Stage 4).
//  - Aims for 30 fps; QualityController picks High, Standard or Light detail from measured frame
//    times, and each 10-second window is recorded (SkyStats) for review at Stage 7.
//  - With data 3 h old or more the sky follows the cached forecast for this hour, and the words
//    say so: old or missing weather is never presented as current.
//  - The Moon is the shared NASA renderer, lit live for this moment (decisions D7 to D10).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.shanebl030glitch.rbsbweather.data.Freshness
import io.github.shanebl030glitch.rbsbweather.scene.Conditions
import io.github.shanebl030glitch.rbsbweather.scene.Kind
import io.github.shanebl030glitch.rbsbweather.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.scene.QualityController
import io.github.shanebl030glitch.rbsbweather.scene.SceneEngine
import io.github.shanebl030glitch.rbsbweather.scene.drawScene
import io.github.shanebl030glitch.rbsbweather.scene.safeRadial
import io.github.shanebl030glitch.rbsbweather.sky.SkyStats
import io.github.shanebl030glitch.rbsbweather.sky.SkyText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * The animated sky for [cond], filling its box. [active]: animate now (on screen, in front).
 * [animate]: the phone's "animations" setting; off draws a still sky.
 */
@Composable
fun AnimatedSky(cond: Conditions, active: Boolean, animate: Boolean, modifier: Modifier = Modifier, dim: Float = 0f) {
    val ctx = LocalContext.current
    remember { try { MoonRender.init(ctx.applicationContext.assets) } catch (e: Exception) { }; true }
    val engine = remember { SceneEngine(seed = 7) }
    val controller = remember { QualityController() }
    val power = remember { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val lifecycle = LocalLifecycleOwner.current
    engine.animations = animate

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
    Canvas(modifier.fillMaxSize().background(Color.Black)) {
        frame                                            // redraw on each animation frame
        val t0 = System.nanoTime()
        // First frame: the weather before the size, so the sky opens as itself (no fade from the
        // engine's default daytime sky). Later changes still cross-fade.
        if (engine.width <= 1 && engine.cond !== cond) engine.setConditions(cond)
        engine.setSize(size.width.toInt(), size.height.toInt())
        if (engine.cond !== cond) engine.setConditions(cond)
        drawScene(engine, scrim = false, underSun = {
            // readability: a soft dark centre behind the text, under the Sun, Moon and clouds
            val r = size.minDimension * 0.5f
            drawCircle(safeRadial(0f to Color(0x66000000), 0.6f to Color(0x38000000), 1f to Color(0x00000000),
                center = center, radius = r), r, center)
        })
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        drawMs[0] = (System.nanoTime() - t0) / 1e6
    }
}

private val Sky = SkyShadow

/** The sky with "now" over it. */
@Composable
fun SkyHero(
    cond: Conditions,
    lines: SkyLines,
    active: Boolean,
    animate: Boolean,
    sampleLine: String?,
    onWarnings: () -> Unit,
    onSkyTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val narrow = Round.narrow()
    Box(modifier.testTag("sky").pointerInput(onSkyTap) { if (onSkyTap != null) detectTapGestures(onTap = { onSkyTap() }) }) {
        AnimatedSky(cond, active, animate, dim = if (lines.freshness == Freshness.EXPIRED) 0.35f else if (lines.freshness == Freshness.VERY_STALE) 0.2f else 0f)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status chip (old data) or warnings chip; the age line below is always shown.
            when {
                lines.chipText != null -> Chip(lines.chipText, when (lines.chip) {
                    ChipKind.EXPIRED -> Palette.Expired; ChipKind.NO_UPDATE -> Palette.Amber; else -> Palette.Amber
                }, if (lines.chip == ChipKind.EXPIRED) Color.White else Color(0xFF1A1200), Modifier.widthIn(max = 168.dp))
                lines.warningCount > 0 -> {
                    val (bg, fg) = Palette.warning(lines.topWarning ?: io.github.shanebl030glitch.rbsbweather.sync.WarningLevel.WATCH)
                    Chip(SkyLinesBuilder.warningChip(lines.warningCount), bg, fg, Modifier.testTag("warningChip").clickable { onWarnings() })
                }
                else -> Spacer(Modifier.height(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(lines.place, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White, shadow = Sky),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(lines.temperature, style = TextStyle(fontSize = if (narrow) 46.sp else 52.sp, fontWeight = FontWeight.Light,
                color = if (lines.dimTemperature) Color(0x99FFFFFF) else Color.White, shadow = Sky))
            Text(lines.condition, style = TextStyle(fontSize = 15.sp, color = Color.White, shadow = Sky),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            lines.asOf?.let {
                Text(it, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFFE2A8), shadow = Sky),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            lines.highLow?.let { Text(it, style = TextStyle(fontSize = 13.sp, color = Palette.TextSoft, shadow = Sky)) }
            Spacer(Modifier.height(4.dp))
            Text(lines.age, style = TextStyle(fontSize = 12.sp, color = Palette.TextSoft, shadow = Sky),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (sampleLine != null) {
                Text(SkyText.SAMPLE_LABEL, modifier = Modifier.background(Palette.Sample, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A1F00), letterSpacing = 0.8.sp))
                Text(sampleLine, modifier = Modifier.widthIn(max = 120.dp), style = TextStyle(fontSize = 9.sp, color = Palette.TextSoft, shadow = Sky),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            } else {
                Text("⌄", style = TextStyle(fontSize = 14.sp, color = Palette.TextSoft, shadow = Sky))   // more below
            }
        }
    }
}

/** What the first screen shows when there is no weather to show (no data yet, or no phone app). */
enum class NoDataReason { WAITING, PHONE_APP_NEEDED, NO_PHONE }

@Composable
fun NoDataHero(reason: NoDataReason, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val still = remember { Conditions(Kind.CLEAR, 0.0, daylight = 0.0) }
    Box(modifier.testTag("noData")) {
        AnimatedSky(still, active = false, animate = false, dim = 0.45f)
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, body) = when (reason) {
                NoDataReason.WAITING -> "Waiting for your phone" to "Open RBSB-Weather on your phone. Version 0.8.0 or newer sends the weather to your watch."
                NoDataReason.PHONE_APP_NEEDED -> "Phone app update needed" to "Your phone is connected, but its RBSB-Weather can't send to the watch yet. Update it to 0.8.0 or newer."
                NoDataReason.NO_PHONE -> "Phone not connected" to "Connect your phone to see the weather. Nothing is shown until real weather arrives."
            }
            Text(title, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, style = Type.Body, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Chip("Try again", Palette.Card, Palette.Text, Modifier.clickable { onRetry() })
            }
        }
    }
}
