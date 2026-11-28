// The detail screens opened from the first screen (Stage 3): all forecast hours, one day, one
// warning, the Moon and the full-screen Moon (decision D8). Each is a Wear OS list that scrolls
// with the bezel or crown; swipe right goes back. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.scene.AnimatedWeatherIcon
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/** A standard Wear OS detail list with the time, position indicator and bezel scrolling. */
@Composable
fun DetailList(tag: String, content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val side = Round.side()
    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(state)) },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().background(Palette.Background).testTag(tag).then(rotaryScroll(state)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = side, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
fun HoursScreen(s: Snapshot, st: SyncSettings, nowMs: Long) {
    val rows = remember(s, st, nowMs / 60_000) { Rows.hours(s, st, nowMs) }
    val note = remember(s, st, nowMs / 60_000) { Rows.warningsNote(s, st, nowMs)?.replace("· may be out of date", "· forecast") }
    DetailList("hours") {
        item { SectionTitle("Hourly") }
        note?.let { item { Text(it, style = Type.Caption, textAlign = TextAlign.Center) } }
        if (rows.isEmpty()) item { Text("No forecast hours left", style = Type.Body) }
        rows.forEach { r -> item { HourRowView(r, st.animations) } }
    }
}

@Composable
fun DayScreen(s: Snapshot, st: SyncSettings, nowMs: Long, date: String) {
    val day = s.daily.firstOrNull { it.date == date }
    DetailList("day") {
        if (day == null) { item { Text("This day is no longer in the forecast", style = Type.Body, textAlign = TextAlign.Center) }; return@DetailList }
        val d = try { LocalDate.parse(date) } catch (e: Exception) { null }
        val today = WeatherView.localNow(s, nowMs).toLocalDate()
        val title = d?.let { "${Units.dayLabel(it, today)} ${it.dayOfMonth} ${it.month.getDisplayName(DateStyle.SHORT, Locale.ENGLISH)}" } ?: date
        item { SectionTitle(title) }
        item { AnimatedWeatherIcon(day.weatherCode, true, size = 44.dp, animate = st.animations) }
        item { Text(Rows.condition(day.weatherCode), style = Type.Row, textAlign = TextAlign.Center) }
        item {
            Text("H ${Units.fmtTemp(day.tempMax, st.temperatureUnit)}  L ${Units.fmtTemp(day.tempMin, st.temperatureUnit)}",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text))
        }
        Rows.dayDetails(s, st, day).forEach { item { DetailRowView(it) } }
    }
}

@Composable
fun WarningScreen(s: Snapshot, st: SyncSettings, nowMs: Long, id: String) {
    val w = remember(s, st, id, nowMs / 60_000) { Rows.warnings(s, st, nowMs).firstOrNull { it.id == id } }
    val note = remember(s, st, nowMs / 60_000) { Rows.warningsNote(s, st, nowMs) }
    DetailList("warning") {
        if (w == null) { item { Text("This warning has ended", style = Type.Body, textAlign = TextAlign.Center) }; return@DetailList }
        val (bg, fg) = Palette.warning(w.level)
        item { Chip(w.levelLabel, bg, fg) }
        note?.let { item { Chip(it, Palette.Amber, Color(0xFF1A1200), Modifier.padding(top = 4.dp)) } }
        item { Text(w.title, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        val where = listOf(w.area, w.span).filter { it.isNotBlank() }
        where.forEach { item { Text(it, style = Type.Caption, textAlign = TextAlign.Center) } }
        if (w.summary.isNotBlank()) item { Text(w.summary, style = Type.Body, modifier = Modifier.padding(top = 6.dp)) }
        if (w.source.isNotBlank()) item { Text(w.source, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
fun MoonScreen(s: Snapshot, st: SyncSettings, nowMs: Long, onFullScreen: () -> Unit) {
    val m = remember(s, st, nowMs / 60_000) { MoonInfoBuilder.build(s, nowMs, st.clock) }
    val screen = LocalConfiguration.current.screenWidthDp
    DetailList("moon") {
        item { Text("Moon", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.MoonTitle)) }
        if (m == null) { item { Text("Moon not available", style = Type.Body) }; return@DetailList }
        item {
            MoonPicture(m.phaseAngle, m.brightLimb, m.rotation, (screen * 0.5f).dp,
                Modifier.padding(vertical = 4.dp).clickable(onClick = onFullScreen).testTag("moonPicture"))
        }
        item { Text(m.name, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text)) }
        item { Text(m.litLine, style = Type.Body) }
        item { Spacer(Modifier.height(4.dp)) }
        item { DetailRowView(Detail("Moonrise", m.riseText)) }
        item { DetailRowView(Detail("Moonset", m.setText)) }
        item { Text(m.skyText, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp)) }
        item { DetailRowView(Detail("Sunrise", m.sunRise)) }
        item { DetailRowView(Detail("Sunset", m.sunSet)) }
        m.upcoming.forEach { (n, d) -> item { DetailRowView(Detail(n, d)) } }
        item { Text("Tap the Moon for full screen", style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** The Moon filling the round screen (decision D8): the shared NASA renderer at full watch resolution. */
@Composable
fun MoonFullScreen(s: Snapshot, st: SyncSettings, nowMs: Long, onClose: () -> Unit) {
    val m = remember(s, st, nowMs / 60_000) { MoonInfoBuilder.build(s, nowMs, st.clock) }
    val screen = LocalConfiguration.current.screenWidthDp
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose).testTag("moonFull"), contentAlignment = Alignment.Center) {
        if (m != null) MoonPicture(m.phaseAngle, m.brightLimb, m.rotation, (screen * 0.92f).dp)
        else Text("Moon not available", style = Type.Body)
        Column(Modifier.fillMaxSize().padding(bottom = 10.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            m?.let { Text(it.name, style = TextStyle(fontSize = 12.sp, color = Palette.TextSoft), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}
