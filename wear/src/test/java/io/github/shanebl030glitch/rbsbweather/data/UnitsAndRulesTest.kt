// Display units, clocks, zone labels (decision D3) and the refresh rule (decision D4).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.ClockFormat
import io.github.shanebl030glitch.rbsbweather.sync.PrecipitationUnit
import io.github.shanebl030glitch.rbsbweather.sync.TemperatureUnit
import io.github.shanebl030glitch.rbsbweather.sync.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UnitsAndRulesTest {
    @Test fun temperaturesRoundLikeThePhone() {
        assertEquals("19°", Units.fmtTemp(19.4, TemperatureUnit.C))
        assertEquals("-3°", Units.fmtTemp(-3.5, TemperatureUnit.C))      // roundToInt, as the phone
        assertEquals("66°", Units.fmtTemp(19.0, TemperatureUnit.F))
        assertEquals("--°", Units.fmtTemp(null, TemperatureUnit.C))   // as the sky shows it (SkyText)
        assertEquals("--°", Units.fmtTemp(Double.NaN, TemperatureUnit.C))
    }

    @Test fun windInEveryUnit() {
        assertEquals("36 km/h", Units.fmtWind(36.0, WindUnit.KMH))
        assertEquals("10 m/s", Units.fmtWind(36.0, WindUnit.MS))
        assertEquals("22 mph", Units.fmtWind(36.0, WindUnit.MPH))
        assertEquals("19 kn", Units.fmtWind(36.0, WindUnit.KN))
        assertEquals("–", Units.fmtWind(null, WindUnit.KMH))
        assertEquals("SW", Units.compass(225.0)); assertEquals("N", Units.compass(359.0)); assertEquals("", Units.compass(null))
    }

    @Test fun rainPressureHumidity() {
        assertEquals("2.4 mm", Units.fmtPrecip(2.4, PrecipitationUnit.MM))
        assertEquals("0.09 in", Units.fmtPrecip(2.4, PrecipitationUnit.IN))
        assertEquals("1014 hPa", Units.fmtPressure(1013.6))
        assertEquals("64%", Units.fmtPercent(64.2))
        assertEquals("–", Units.fmtPercent(null))
    }

    @Test fun clocksAndDays() {
        val t = LocalDateTime.parse("2026-11-28T14:05")
        assertEquals("14:05", Units.clock(t, ClockFormat.H24))
        assertEquals("2:05 pm", Units.clock(t, ClockFormat.H12))
        assertEquals("12:00 am", Units.clock(0, 0, ClockFormat.H12))
        assertEquals("15:00", Units.hourLabel(LocalDateTime.parse("2026-11-28T15:00"), ClockFormat.H24))
        assertEquals("3 pm", Units.hourLabel(LocalDateTime.parse("2026-11-28T15:00"), ClockFormat.H12))
        val today = LocalDate.parse("2026-11-28")
        assertEquals("Today", Units.dayLabel(today, today))
        assertEquals("Tmrw", Units.dayLabel(today.plusDays(1), today))
        assertEquals("Mon", Units.dayLabel(today.plusDays(2), today))
    }

    @Test fun zoneLabelOnlyWhenTheWatchDiffers() {
        val akl = ZoneId.of("Pacific/Auckland")
        val at = Instant.parse("2026-11-28T01:05:00Z")                   // 14:05 NZDT
        assertEquals("", Units.zoneLabel(akl, akl, at))
        assertEquals("14:05", Units.clockAt(at.toEpochMilli(), akl, akl, ClockFormat.H24))
        val label = Units.zoneLabel(akl, ZoneId.of("Europe/London"), at)
        assertTrue(label, label.isNotEmpty())
        assertEquals("14:05 $label", Units.clockAt(at.toEpochMilli(), akl, ZoneId.of("Europe/London"), ClockFormat.H24))
    }

    @Test fun refreshOnlyWhenOldAndNotTooOften() {
        val min = 60_000L; val now = 10_000_000_000L
        assertFalse(RefreshRule.shouldRequest(30 * min, null, now))       // fresh data: no request
        assertFalse(RefreshRule.shouldRequest(45 * min, null, now))
        assertTrue(RefreshRule.shouldRequest(46 * min, null, now))
        assertTrue(RefreshRule.shouldRequest(null, null, now))            // no data yet
        assertFalse(RefreshRule.shouldRequest(null, now - 9 * min, now))  // asked 9 minutes ago
        assertTrue(RefreshRule.shouldRequest(null, now - 10 * min, now))
        assertTrue(RefreshRule.shouldRequest(null, now + 60 * min, now))  // clock moved back
    }

    @Test fun neverMoreThanOneRequestInAnyTenMinutes() {
        // A simulated hour of opening the app every 30 seconds with old data.
        val min = 60_000L; var last: Long? = null; val sent = mutableListOf<Long>()
        for (i in 0 until 120) {
            val now = 1_000_000_000L + i * 30_000L
            if (RefreshRule.shouldRequest(5 * 60 * min, last, now)) { sent += now; last = now }
        }
        assertEquals(6, sent.size)
        assertTrue(sent.zipWithNext().all { (a, b) -> b - a >= 10 * min })
    }
}
