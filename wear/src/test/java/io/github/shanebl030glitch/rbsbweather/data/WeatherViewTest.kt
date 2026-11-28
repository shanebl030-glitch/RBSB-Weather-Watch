// Data age, "now", forecast hours and days, and warnings (Stage 3). The rule under test: old or
// missing weather is never presented as current. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import io.github.shanebl030glitch.rbsbweather.sync.MinWarningLevel
import io.github.shanebl030glitch.rbsbweather.sync.SnapHour
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class WeatherViewTest {
    private val samples = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid").let { d ->
        SampleData.decode(d.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() })
    }.associateBy { it.name }
    private fun sample(name: String): Snapshot = samples.getValue(name).snapshot
    private val MIN = 60_000L
    private val HOUR = 3_600_000L

    @Test fun freshnessThresholdsOneSecondEitherSide() {
        val s = sample("clear-day")                     // 48 hourly hours from fetch
        val t = s.fetchedAt
        assertEquals(Freshness.FRESH, WeatherView.freshness(s, t))
        assertEquals(Freshness.FRESH, WeatherView.freshness(s, t + 45 * MIN - 1000))
        assertEquals(Freshness.AGEING, WeatherView.freshness(s, t + 45 * MIN))
        assertEquals(Freshness.AGEING, WeatherView.freshness(s, t + 3 * HOUR - 1000))
        assertEquals(Freshness.STALE, WeatherView.freshness(s, t + 3 * HOUR))
        assertEquals(Freshness.STALE, WeatherView.freshness(s, t + 12 * HOUR - 1000))
        assertEquals(Freshness.VERY_STALE, WeatherView.freshness(s, t + 12 * HOUR))
        val end = WeatherView.forecastEnd(s)
        assertEquals(Freshness.VERY_STALE, WeatherView.freshness(s, end - 1000))
        assertEquals(Freshness.EXPIRED, WeatherView.freshness(s, end))
    }

    @Test fun forecastEndIsTheEndOfTheLastHour() {
        val s = sample("clear-day")
        val last = s.hourly.last()
        val start = LocalDateTime.parse(last.time).atZone(ZoneId.of(s.place.timezone)).toInstant().toEpochMilli()
        assertEquals(start + HOUR, WeatherView.forecastEnd(s))
        // with no forecast at all, the data expires 12 hours after the fetch
        val bare = s.copy(hourly = emptyList(), daily = emptyList())
        assertEquals(s.fetchedAt + 12 * HOUR, WeatherView.forecastEnd(bare))
    }

    @Test fun ageIsMeasuredFromFetchedAtNotArrival() {
        val s = sample("clear-day")
        assertEquals(30 * MIN, WeatherView.ageMs(s, s.fetchedAt + 30 * MIN))
        assertEquals(0L, WeatherView.ageMs(s, s.fetchedAt - 5 * MIN))      // a watch clock slightly behind
    }

    @Test fun ageText() {
        assertEquals("Updated just now", WeatherView.ageText(20_000))
        assertEquals("Updated 12 min ago", WeatherView.ageText(12 * MIN))
        assertEquals("Updated 1 h 20 min ago", WeatherView.ageText(80 * MIN))
        assertEquals("Updated 2 h ago", WeatherView.ageText(120 * MIN))
        assertEquals("Updated 5 h ago", WeatherView.ageText(5 * HOUR + 10 * MIN))
        assertEquals("Updated 1 day ago", WeatherView.ageText(30 * HOUR))
        assertEquals("Updated 3 days ago", WeatherView.ageText(75 * HOUR))
    }

    @Test fun freshDataShowsTheCurrentConditions() {
        val s = sample("clear-day")
        val n = WeatherView.now(s, s.fetchedAt + 10 * MIN)
        assertEquals(NowSource.CURRENT, n.source)
        assertEquals(s.current.temperature, n.temperature)
        assertEquals(s.current.humidity, n.humidity)
    }

    @Test fun staleDataUsesTheForecastForThisHourAndNeverTheOldReadings() {
        val s = sample("clear-day")
        val at = s.fetchedAt + 5 * HOUR + 10 * MIN
        val n = WeatherView.now(s, at)
        assertEquals(NowSource.FORECAST_HOUR, n.source)
        val h = WeatherView.hourAt(s, at)!!
        assertEquals(h.temperature, n.temperature)
        assertEquals(LocalDateTime.parse(h.time), n.asOfLocal)
        // values the hourly forecast doesn't carry stay empty rather than show 5-hour-old readings as now
        assertNull(n.humidity); assertNull(n.pressure); assertNull(n.apparentTemperature)
    }

    @Test fun expiredDataIsLabelledLastKnown() {
        val s = sample("clear-day")
        val n = WeatherView.now(s, WeatherView.forecastEnd(s) + HOUR)
        assertEquals(NowSource.LAST_KNOWN, n.source)
        assertEquals(LocalDateTime.parse(s.current.time), n.asOfLocal)
    }

    @Test fun aGapInTheForecastFallsBackToLastKnown() {
        val s = sample("clear-day")
        val at = s.fetchedAt + 6 * HOUR
        val gap = s.copy(hourly = s.hourly.filterNot { it == WeatherView.hourAt(s, at) })
        assertEquals(NowSource.LAST_KNOWN, WeatherView.now(gap, at).source)
    }

    @Test fun pastHoursAndDaysDropOff() {
        val s = sample("clear-day")
        assertEquals(48, WeatherView.hoursAhead(s, s.fetchedAt).size)
        val later = WeatherView.hoursAhead(s, s.fetchedAt + 5 * HOUR + 30 * MIN)
        assertEquals(43, later.size)                                     // the current hour is still listed
        assertEquals(WeatherView.hourAt(s, s.fetchedAt + 5 * HOUR + 30 * MIN), later.first())
        assertEquals(s.daily.size, WeatherView.daysAhead(s, s.fetchedAt).size)
        assertEquals(s.daily.size - 2, WeatherView.daysAhead(s, s.fetchedAt + 48 * HOUR).size)
        assertEquals(s.daily.first(), WeatherView.today(s, s.fetchedAt))
    }

    @Test fun daysFollowThePlacesDateNotTheWatchs() {
        // Auckland is UTC+13: 10:00 local on 28 Nov is 21:00 UTC on 27 Nov
        val s = sample("clear-day")
        assertEquals("2026-11-28", WeatherView.localNow(s, s.fetchedAt).toLocalDate().toString())
    }

    @Test fun warningsDropOffAtTheirOwnEndTimeEvenOffline() {
        val s = sample("rain-night-warnings")
        val all = WeatherView.activeWarnings(s, s.fetchedAt)
        assertEquals(3, all.size)
        assertEquals(WarningLevel.ORANGE, all.first().level)            // most severe first
        assertEquals(WarningLevel.LIMIT, all.last().level)              // the user's own limits last
        val yellowEnd = s.warnings.first { it.level == WarningLevel.YELLOW }.end!!
        assertEquals(2, WeatherView.activeWarnings(s, yellowEnd).size)
        assertTrue(WeatherView.activeWarnings(s, s.warnings.mapNotNull { it.end }.max()).isEmpty())
    }

    @Test fun minimumWarningLevel() {
        val s = sample("rain-night-warnings")                           // orange, yellow, limit
        assertEquals(3, WeatherView.activeWarnings(s, s.fetchedAt, MinWarningLevel.WATCH).size)
        val orange = WeatherView.activeWarnings(s, s.fetchedAt, MinWarningLevel.ORANGE).map { it.level }
        assertEquals(listOf(WarningLevel.ORANGE, WarningLevel.LIMIT), orange)
        val red = WeatherView.activeWarnings(s, s.fetchedAt, MinWarningLevel.RED).map { it.level }
        assertEquals(listOf(WarningLevel.LIMIT), red)                    // own limits always show
    }

    @Test fun everySampleWorksAtEveryAge() {
        for ((name, smp) in samples) {
            val s = smp.snapshot
            for (h in listOf(0L, 1L, 4L, 13L, 60L, 24L * 30)) {
                val at = s.fetchedAt + h * HOUR
                WeatherView.freshness(s, at); WeatherView.now(s, at); WeatherView.hoursAhead(s, at)
                WeatherView.daysAhead(s, at); WeatherView.activeWarnings(s, at)
                assertTrue("$name at +$h h", WeatherView.hoursAhead(s, at).all { hr: SnapHour -> WeatherView.hourStart(s, hr)!! + HOUR > at })
            }
        }
    }
}
