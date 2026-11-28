// The first screen (Stage 3 decision D1): one vertical scroll that starts on the animated sky and
// continues through Now, Hourly, Daily, Wind, Warnings, Moon and About. The bezel or crown always
// scrolls; tap a day, a warning or the Moon for its details; swipe right goes back or leaves.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import io.github.shanebl030glitch.rbsbweather.AboutInfo
import io.github.shanebl030glitch.rbsbweather.data.Freshness
import io.github.shanebl030glitch.rbsbweather.scene.conditionsFromSnapshot
import io.github.shanebl030glitch.rbsbweather.sky.SkyStats
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import java.time.ZoneId

/** Item keys of the first screen, in order (also the debug "scrollTo" names). */
val HOME_SECTIONS = listOf("sky", "details", "hourly", "daily", "wind", "warnings", "moon", "about")

@Composable
fun HomeScreen(
    snapshot: Snapshot,
    settings: SyncSettings,
    nowMs: Long,
    sampleLine: String?,
    aboutLines: List<String>,
    onSkyTap: (() -> Unit)?,
    onOpen: (String) -> Unit,
    scrollTo: String?,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // The sky animates only while most of it is on screen.
    val skyShown by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < screenPx * 0.5f } }
    LaunchedEffect(scrollTo) {
        val i = HOME_SECTIONS.indexOf(scrollTo)
        if (i > 0) listState.scrollToItem(firstItemOf(i))
    }

    val lines = remember(snapshot, settings, nowMs / 30_000) { SkyLinesBuilder.build(snapshot, settings, nowMs, ZoneId.systemDefault()) }
    val forecastHour = lines.freshness == Freshness.STALE || lines.freshness == Freshness.VERY_STALE
    // The sky's conditions are refreshed each minute so the Sun and Moon move on.
    val cond = remember(snapshot, nowMs / 60_000, forecastHour) { conditionsFromSnapshot(snapshot, nowMs, forecastHour = forecastHour) }
    val hours = remember(snapshot, settings, nowMs / 60_000) { Rows.hours(snapshot, settings, nowMs) }
    val days = remember(snapshot, settings, nowMs / 60_000) { Rows.days(snapshot, settings, nowMs) }
    val details = remember(snapshot, settings, nowMs / 60_000) { Rows.nowDetails(snapshot, settings, nowMs) }
    val wind = remember(snapshot, settings, nowMs / 60_000) { Rows.wind(snapshot, settings, nowMs) }
    val warnings = remember(snapshot, settings, nowMs / 60_000) { Rows.warnings(snapshot, settings, nowMs) }
    val warningsNote = remember(snapshot, settings, nowMs / 60_000) { Rows.warningsNote(snapshot, settings, nowMs) }
    val moon = remember(snapshot, settings, nowMs / 60_000) { MoonInfoBuilder.build(snapshot, nowMs, settings.clock) }
    val animate = settings.animations
    val side = Round.side()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        positionIndicator = { if (!skyShown) PositionIndicator(lazyListState = listState) },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home").then(rotaryScroll(listState)),
            contentPadding = PaddingValues(bottom = (LocalConfiguration.current.screenHeightDp * 0.32f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "sky") {
                SkyHero(cond, lines, active = skyShown, animate = animate, sampleLine = sampleLine,
                    onWarnings = {
                        if (warnings.size == 1) onOpen("warning/${android.net.Uri.encode(warnings[0].id)}")
                        else scope.launch { listState.animateScrollToItem(HOME_SECTIONS.indexOf("warnings")) }
                    },
                    onSkyTap = onSkyTap, modifier = Modifier.fillParentMaxSize())
            }
            section("details", side) {
                SectionTitle(Rows.nowTitle(snapshot, settings, nowMs))
                if (details.isEmpty()) Text("No details for now", style = Type.Caption, textAlign = TextAlign.Center)
                details.forEach { DetailRowView(it) }
                if (lines.freshness != Freshness.FRESH && lines.freshness != Freshness.AGEING) {
                    Text("Current readings need fresh data from your phone", style = Type.Caption, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            section("hourly", side) {
                SectionTitle("Hourly")
                if (hours.isEmpty()) Text("No forecast hours left", style = Type.Caption)
                hours.take(6).forEach { HourRowView(it, animate) }
                if (hours.size > 6) Chip("All ${hours.size} hours", Palette.Card, Palette.Accent,
                    Modifier.padding(top = 6.dp).clickable { onOpen("hours") })
            }
            section("daily", side) {
                SectionTitle(if (days.size > 1) "Next ${days.size} days" else "Daily")
                if (days.isEmpty()) Text("No forecast days left", style = Type.Caption)
                days.forEach { d -> DayRowView(d, animate, onClick = { onOpen("day/${d.date}") }) }
            }
            section("wind", side) {
                SectionTitle("Wind")
                WindSection(wind)
            }
            section("warnings", side) {
                SectionTitle("Warnings")
                warningsNote?.let { Chip(it, Palette.Amber, androidx.compose.ui.graphics.Color(0xFF1A1200), Modifier.padding(bottom = 6.dp)) }
                if (warnings.isEmpty()) Text("No active warnings", style = Type.Body, textAlign = TextAlign.Center)
                warnings.forEach { w ->
                    WarningCardView(w, onClick = { onOpen("warning/${android.net.Uri.encode(w.id)}") }, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            section("moon", side) {
                SectionTitle("Moon")
                if (moon == null) Text("Moon not available", style = Type.Caption)
                else Card(onClick = { onOpen("moon") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonPicture(moon.phaseAngle, moon.brightLimb, moon.rotation, 56.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(moon.name, style = Type.RowBold, maxLines = 1)
                            Text(moon.litLine.substringBefore(" ·"), style = Type.Caption, maxLines = 1)
                        }
                    }
                }
            }
            section("about", side) {
                SectionTitle("About")
                val perf = SkyStats.latest.value
                (aboutLines + listOfNotNull(perf?.let { "Sky: $it" })).forEachIndexed { i, line ->
                    Text(line, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        style = when {
                            i == 0 -> TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                            line == AboutInfo.MOON_CREDIT || line.startsWith("Sky: ") -> Type.Caption
                            else -> Type.Body
                        })
                }
            }
        }
    }
}

/** Each section after the sky is one list item, so the debug "scrollTo" and the position indicator line up. */
private fun firstItemOf(section: Int) = section

private fun LazyListScope.section(key: String, side: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.fillMaxWidth().padding(horizontal = side), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun WindSection(w: WindInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        w.note?.let { Text(it, style = TextStyle(fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFFFE2A8)), textAlign = TextAlign.Center) }
        if (w.directionFrom == null && w.speed == "–") {
            Text("No wind data for now", style = Type.Body, textAlign = TextAlign.Center)
            return@Column
        }
        WindCompass(w.directionFrom, 110.dp, Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(w.speed, style = Type.Big)
            Text(" " + w.unit, style = TextStyle(fontSize = 13.sp, color = Palette.Quiet), modifier = Modifier.padding(bottom = 5.dp))
        }
        val gustFrom = listOfNotNull(w.gusts?.let { "Gusts $it" }, w.from.takeIf { it.isNotEmpty() }?.let { "from $it" }).joinToString(" · ")
        if (gustFrom.isNotEmpty()) Text(gustFrom, style = Type.Body, textAlign = TextAlign.Center)
        if (w.nextHours.isNotEmpty()) {
            Text("Next hours", style = Type.Caption, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                w.nextHours.forEach { (t, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, style = Type.RowBold)
                        Text(t, style = TextStyle(fontSize = 10.sp, color = Palette.Quiet))
                    }
                }
            }
        }
    }
}
