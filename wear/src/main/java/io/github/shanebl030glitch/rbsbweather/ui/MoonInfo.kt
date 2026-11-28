// What the Moon screen shows (Stage 3, decisions D8 and D10), worked out exactly as RBSB-Weather
// for Android's Moon screen does (MoonScreen.kt buildMoonReport, v0.7.2 commit cdf422d): the phase
// now; the picture as the Moon looks in the place's sky now, or at its next highest point when it
// is below the horizon; moonrise and moonset today; the next principal phases. All live with the
// shared Moon.kt; the snapshot's values are the fallback (MoonNow). Plain Kotlin, unit tested.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Moon
import io.github.shanebl030glitch.rbsbweather.data.MoonNow
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.sync.ClockFormat
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class MoonInfo(
    val name: String,
    /** "78% lit · 9.6 days old". */
    val litLine: String,
    val riseText: String,
    val setText: String,
    /** "Up now · 35° high in the NE" / "Below horizon · shown at its highest, Sun 22:59". */
    val skyText: String,
    val sunRise: String,
    val sunSet: String,
    /** The next principal phases: "Full Moon" to "Wed 2 Dec". */
    val upcoming: List<Pair<String, String>>,
    /** For the picture (MoonRender): phase angle, bright limb, rotation clockwise. */
    val phaseAngle: Double,
    val brightLimb: Double,
    val rotation: Double,
    /** False when the watch's calculation failed and the phone's values were used. */
    val live: Boolean,
)

object MoonInfoBuilder {
    private fun clock(t: LocalDateTime, f: ClockFormat) = Units.clock(t, f)

    fun build(s: Snapshot, nowMs: Long, clockFormat: ClockFormat, watchZone: ZoneId = ZoneId.systemDefault()): MoonInfo? {
        val now = Instant.ofEpochMilli(nowMs)
        val zone = WeatherView.zone(s)
        val lat = s.place.latitude; val lon = s.place.longitude
        val phase = MoonNow.phase(now, s.moon) ?: return null
        // The picture: the Moon as it looks now if it is up, else at its next highest point (as the phone).
        val view = try { Moon.skyView(now, lat, lon, zone) } catch (e: Exception) { null }
        val pic = if (view == null || view.isNow) phase else MoonNow.phase(view.whenT.toInstant(), null) ?: phase
        val rotation = view?.rotation ?: s.moon.tilt
        val skyText = when {
            view == null -> "Position not available"
            view.isNow -> String.format(Locale.US, "Up now · %.0f° high in the %s", view.altitude, Units.compass(view.azimuth))
            view.up -> "Below horizon · shown at its highest, ${Units.dayName(view.whenT.dayOfWeek)} ${clock(view.whenT.toLocalDateTime(), clockFormat)}"
            else -> "Below the horizon all day · shown at its highest"
        }
        val rs = MoonNow.riseSet(now, lat, lon, zone, s.moon)
        fun at(i: Instant?) = i?.let { Units.clockAt(it.toEpochMilli(), zone, watchZone, clockFormat) }
        val rise = at(rs.rise) ?: if (rs.alwaysUp) "Up all day" else "No moonrise"
        val set = at(rs.set) ?: if (rs.alwaysDown) "Down all day" else "No moonset"
        val age = try { Moon.ageDays(now) } catch (e: Exception) { phase.elongation / 360.0 * 29.530588 }
        val upcoming = try {
            Moon.upcomingPhases(now, zone).take(2).map { (n, t) -> n to "${Units.dayName(t.dayOfWeek)} ${t.dayOfMonth} ${t.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)}" }
        } catch (e: Exception) { emptyList() }
        val today = WeatherView.today(s, nowMs)
        fun sun(local: String?, fallback: Long?): String = local?.let { try { clock(LocalDateTime.parse(it), clockFormat) } catch (e: Exception) { null } }
            ?: fallback?.let { at(Instant.ofEpochMilli(it)) } ?: "–"
        val todayIsFetchDay = today != null && WeatherView.localNow(s, s.fetchedAt).toLocalDate().toString() == today.date
        return MoonInfo(
            name = phase.name,
            litLine = "${(phase.illumination * 100).roundToInt()}% lit · ${String.format(Locale.US, "%.1f", age)} days old",
            riseText = rise, setText = set, skyText = skyText,
            sunRise = sun(today?.sunrise, s.sun.rise.takeIf { todayIsFetchDay }),
            sunSet = sun(today?.sunset, s.sun.set.takeIf { todayIsFetchDay }),
            upcoming = upcoming,
            phaseAngle = pic.phaseAngle, brightLimb = pic.brightLimb, rotation = rotation,
            live = phase.live,
        )
    }
}
