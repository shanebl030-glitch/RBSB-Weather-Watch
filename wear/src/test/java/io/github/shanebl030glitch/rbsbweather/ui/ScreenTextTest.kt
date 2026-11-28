// What the Stage 3 screens say, built from the Stage 1 samples: labels for old data, rows,
// wind, warnings and the Moon screen. Old or missing weather is never presented as current.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Freshness
import io.github.shanebl030glitch.rbsbweather.data.Moon
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import io.github.shanebl030glitch.rbsbweather.sync.ClockFormat
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.TemperatureUnit
import io.github.shanebl030glitch.rbsbweather.sync.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class ScreenTextTest {
    private val samples = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid").let { d ->
        SampleData.decode(d.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() })
    }.associateBy { it.name }
    private fun sample(name: String): Snapshot = samples.getValue(name).snapshot
    private val st = Units.DEFAULT_SETTINGS
    private val akl = ZoneId.of("Pacific/Auckland")
    private val HOUR = 3_600_000L

    @Test fun freshDataHasNoLabels() {
        val s = sample("clear-day")
        val l = SkyLinesBuilder.build(s, st, s.fetchedAt + 12 * 60_000, akl)
        assertEquals(ChipKind.NONE, l.chip); assertNull(l.chipText); assertNull(l.asOf)
        assertEquals("Updated 12 min ago", l.age)
        assertFalse(l.dimTemperature)
        assertEquals("Auckland", l.place)
        assertEquals(Units.fmtTemp(s.current.temperature, TemperatureUnit.C), l.temperature)
    }

    @Test fun staleDataSaysOldAndShowsTheForecastHour() {
        val s = sample("clear-day")                                      // fetched 10:00 local
        val l = SkyLinesBuilder.build(s, st, s.fetchedAt + 5 * HOUR + 5 * 60_000, akl)
        assertEquals(Freshness.STALE, l.freshness)
        assertEquals(ChipKind.OLD, l.chip)
        assertEquals("Old data · from 10:00", l.chipText)
        assertEquals("Forecast for 15:00", l.asOf)
        assertEquals("Updated 5 h ago", l.age)
    }

    @Test fun veryStaleAndExpiredAreDimmedAndLabelled() {
        val s = sample("clear-day")
        val v = SkyLinesBuilder.build(s, st, s.fetchedAt + 20 * HOUR, akl)
        assertEquals(ChipKind.NO_UPDATE, v.chip); assertTrue(v.dimTemperature)
        assertTrue(v.chipText!!.startsWith("No update since"))
        val e = SkyLinesBuilder.build(s, st, WeatherView.forecastEnd(s) + HOUR, akl)
        assertEquals(ChipKind.EXPIRED, e.chip); assertEquals("No current data", e.chipText)
        assertTrue(e.asOf!!.startsWith("Last known · "))
        assertTrue(e.dimTemperature)
    }

    @Test fun warningsChipCountsActiveWarnings() {
        val s = sample("rain-night-warnings")
        val l = SkyLinesBuilder.build(s, st, s.fetchedAt, akl)
        assertEquals(3, l.warningCount)
        assertEquals("⚠ 3 warnings", SkyLinesBuilder.warningChip(3))
        assertEquals("⚠ 1 warning", SkyLinesBuilder.warningChip(1))
    }

    @Test fun hourRowsStartWithNowAndFollowTheClockSetting() {
        val s = sample("clear-day")
        val rows = Rows.hours(s, st, s.fetchedAt)
        assertEquals("Now", rows.first().time)
        assertEquals("11:00", rows[1].time)
        assertEquals(48, rows.size)
        val h12 = Rows.hours(s, st.copy(clock = ClockFormat.H12), s.fetchedAt)
        assertEquals("11 am", h12[1].time)
    }

    @Test fun dayRowsAndDetails() {
        val s = sample("clear-day")
        val days = Rows.days(s, st, s.fetchedAt)
        assertEquals("Today", days[0].label); assertEquals("Tmrw", days[1].label)
        val d = Rows.dayDetails(s, st, s.daily.first()).associate { it.label to it.value }
        assertEquals(setOf("Rain chance", "Rain total", "Wind up to", "Gusts up to", "UV index", "Sunrise", "Sunset"), d.keys)
    }

    @Test fun nowDetailsLeaveOutReadingsThatAreNotCurrent() {
        val s = sample("clear-day")
        val fresh = Rows.nowDetails(s, st, s.fetchedAt).map { it.label }
        assertTrue(fresh.containsAll(listOf("Humidity", "Pressure")))
        val stale = Rows.nowDetails(s, st, s.fetchedAt + 5 * HOUR).map { it.label }
        assertFalse(stale.contains("Humidity")); assertFalse(stale.contains("Pressure"))
        assertEquals("Forecast for 15:00", Rows.nowTitle(s, st, s.fetchedAt + 5 * HOUR))
    }

    @Test fun windNextHoursOnlyWithHourlyWind() {
        val noHourly = sample("clear-day").let { it.copy(hourly = it.hourly.map { h -> h.copy(wind = null) }) }
        assertTrue(Rows.wind(noHourly, st, noHourly.fetchedAt).nextHours.isEmpty())   // 0.7.2 sends no hourly wind
        val mixed = sample("hourly-wind-mixed")
        assertTrue(Rows.wind(mixed, st, mixed.fetchedAt).nextHours.isNotEmpty())
        val w = Rows.wind(mixed, st.copy(windUnit = WindUnit.KN), mixed.fetchedAt)
        assertEquals("kn", w.unit)
    }

    @Test fun warningCardsAndStaleNote() {
        val s = sample("rain-night-warnings")
        val cards = Rows.warnings(s, st, s.fetchedAt, akl)
        assertEquals("ORANGE", cards.first().levelLabel)
        assertTrue(cards.first().span.startsWith("Until "))
        assertTrue(cards.last().source.contains("your own limit"))
        assertNull(Rows.warningsNote(s, st, s.fetchedAt, akl))
        assertEquals("As of 22:00 · may be out of date", Rows.warningsNote(s, st, s.fetchedAt + 4 * HOUR, akl))
    }

    @Test fun moonScreenUsesTheLiveCalculation() {
        val s = sample("clear-day")
        val at = s.fetchedAt + 6 * HOUR
        val m = MoonInfoBuilder.build(s, at, ClockFormat.H24, akl)
        assertNotNull(m); m!!
        assertTrue(m.live)
        assertEquals(Moon.phase(Instant.ofEpochMilli(at)).name, m.name)
        assertTrue(m.litLine.contains("% lit"))
        assertEquals(2, m.upcoming.size)
        assertTrue(m.phaseAngle in 0.0..180.0)
    }

    @Test fun everySampleBuildsEveryScreenAtEveryAge() {
        for ((name, smp) in samples) for (h in listOf(0L, 2L, 5L, 14L, 72L)) {
            val s = smp.snapshot; val at = s.fetchedAt + h * HOUR
            SkyLinesBuilder.build(s, st, at, akl); Rows.hours(s, st, at); Rows.days(s, st, at); Rows.nowDetails(s, st, at)
            Rows.wind(s, st, at); Rows.warnings(s, st, at, akl); Rows.warningsNote(s, st, at, akl)
            assertNotNull("$name +$h h moon", MoonInfoBuilder.build(s, at, ClockFormat.H24, akl))
        }
    }
}
