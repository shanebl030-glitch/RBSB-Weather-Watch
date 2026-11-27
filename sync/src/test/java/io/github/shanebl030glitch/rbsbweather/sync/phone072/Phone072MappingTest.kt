// Real RBSB-Weather for Android 0.7.2 data (its own data classes, copied for tests) fits sync
// format v1: mapped, encoded, sent and decoded without loss of anything the format carries.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync.phone072

import io.github.shanebl030glitch.rbsbweather.sync.Decoded
import io.github.shanebl030glitch.rbsbweather.sync.Limits
import io.github.shanebl030glitch.rbsbweather.sync.MoonState
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SnapshotCodec
import io.github.shanebl030glitch.rbsbweather.sync.SunTimes
import io.github.shanebl030glitch.rbsbweather.sync.SyncText
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

class Phone072MappingTest {
    // 28 Nov 2026 10:15 in Auckland (UTC+13) = 2026-11-27T21:15Z
    private val fetchedAt = 1_795_814_100_000L
    private val now = fetchedAt + 5 * 60_000L

    /** What 0.7.2 holds after an Open-Meteo fetch: 7 days of hourly data from midnight, unrounded place. */
    private fun forecast(fetched: Long = fetchedAt): Forecast {
        val start = LocalDateTime.of(2026, 11, 28, 0, 0)
        val hours = (0 until 168).map { i ->
            val t = start.plusHours(i.toLong())
            Hour(t.toString(), if (i == 20) null else 15.0 + (i % 24) / 3.0, (i * 7 % 100).toDouble(), (i % 5) * 0.4, if (i % 9 == 0) 61 else 2)
        }
        val days = (0 until 7).map { d ->
            val date = start.toLocalDate().plusDays(d.toLong())
            Day(date.toString(), 3, 22.0, 13.0, 4.2, 70.0, 31.0, "${date}T05:58", "${date}T20:21", 9.0, if (d == 0) 48.0 else null)
        }
        return Forecast(
            place = Place("Auckland", -36.84846, 174.76334, "New Zealand", "Auckland", "Pacific/Auckland", current = true, detail = 2),
            timezone = "Pacific/Auckland", utcOffsetSeconds = 46800,
            current = Current("2026-11-28T10:15", 18.4, 17.9, 71.0, 2, true, 14.8, 226.0, 27.4, 1016.2, 48.0, 0.0),
            hourly = hours, daily = days, source = "Open-Meteo", fetchedAt = fetched,
        )
    }

    /** What the phone's Watch.alertsFor() returns: shown warnings (own limits included, muted ones already removed). */
    private val shown = listOf(
        Alert("metservice-heavy-rain-1", "MetService", "orange", "Heavy Rain Warning",
            headline = "Heavy rain for Auckland", description = "Periods of heavy rain. ".repeat(60), area = "Auckland",
            onset = (fetchedAt / 1000 - 3600).toDouble(), expires = (fetchedAt / 1000 + 12 * 3600).toDouble(), hazard = "rain"),
        Alert("limit-wind-60", "RBSB-Weather", "limit", "My limit: gusts over 60 km/h", description = "Forecast gusts 72 km/h.",
            official = false, hazard = "wind"),
        Alert("nws-advisory-1", "US National Weather Service", "minor", "Small Craft Advisory"),
    )

    private val sun = SunTimes(fetchedAt - 4 * 3600_000L, fetchedAt + 10 * 3600_000L)
    private val moon = MoonState(at = fetchedAt, phase = 152.0, phaseName = "Waxing Gibbous", illumination = 0.94,
        brightLimb = 268.5, tilt = -31.2, rise = fetchedAt + 6 * 3600_000L, set = fetchedAt - 5 * 3600_000L)

    private fun roundTrip(): Pair<Snapshot, Snapshot> {
        val snap = Phone072Mapper.toSnapshot(forecast(), shown, sun, moon, sequence = 1, sourceId = "3f2b9c1e-6d4a-4b7e-9a51-0c8d2e7f4a10", sentAt = fetchedAt + 1000)
        val bytes = SnapshotCodec.encode(snap)
        println("MAPPING 0.7.2 -> v1: ${snap.hourly.size} hours, ${snap.daily.size} days, ${snap.warnings.size} warnings, ${bytes.size} bytes compressed")
        return when (val d = SnapshotCodec.decode(bytes, now)) {
            is Decoded.Ok -> snap to d.value
            is Decoded.Rejected -> { fail("0.7.2 data rejected: ${d.reason} at ${d.path}: ${d.detail}"); throw AssertionError() }
        }
    }

    @Test fun phone072ForecastMapsToAValidSnapshotAndArrivesUnchanged() {
        val (sent, received) = roundTrip()
        assertEquals(sent, received)
        assertTrue(received.hourly.size == Limits.MAX_HOURS)
        assertEquals("starts at the current hour", "2026-11-28T10:00", received.hourly.first().time)
        assertEquals("2026-11-30T09:00", received.hourly.last().time)
        assertNull("a missing 0.7.2 value stays null", received.hourly[10].temperature)       // 0.7.2 hour 20
        assertEquals(7, received.daily.size)
        assertEquals(48.0, received.daily[0].gustMax!!, 0.0)
        assertNull(received.daily[1].gustMax)
    }

    @Test fun coordinatesAreRoundedToThreeDecimals() {
        val (_, r) = roundTrip()
        assertEquals(-36.848, r.place.latitude, 0.0)
        assertEquals(174.763, r.place.longitude, 0.0)
    }

    @Test fun phone072HasNoHourlyWindSoEveryHourUsesCurrentWind() {
        val (_, r) = roundTrip()
        assertTrue(r.hourly.all { it.wind == null })
        assertEquals(14.8, r.current.wind.speed!!, 0.0)
        assertEquals(27.4, r.current.wind.gusts!!, 0.0)
        assertEquals(226.0, r.current.wind.direction!!, 0.0)
        assertTrue(r.hourly.all { r.windAt(it) == r.current.wind })
    }

    @Test fun shownWarningsIncludingOwnLimitsAreSentWithinLimits() {
        val (_, r) = roundTrip()
        assertEquals(3, r.warnings.size)
        val rain = r.warnings[0]
        assertEquals(WarningLevel.ORANGE, rain.level)
        assertEquals("Orange Heavy Rain Warning", rain.title)                // the phone's own title()
        assertEquals(Limits.WARNING_SUMMARY, SyncText.length(rain.summary))
        assertTrue("long summary is cut with an ellipsis", rain.summary.endsWith("…"))
        assertEquals(fetchedAt - 3600_000L, rain.start)
        assertEquals(fetchedAt + 12 * 3600_000L, rain.end)
        val limit = r.warnings[1]
        assertEquals(WarningLevel.LIMIT, limit.level)
        assertEquals(false, limit.official)
        assertEquals("My limit: gusts over 60 km/h", limit.title)
        val noTimes = r.warnings[2]
        assertNull(noTimes.start)
        assertNull(noTimes.end)
    }

    @Test fun phone072CachedForecastWithoutAFetchTimeIsRefused() {
        // 0.7.2's Forecast.fetchedAt defaults to 0; the phone must stamp the real fetch time.
        val snap = Phone072Mapper.toSnapshot(forecast(fetched = 0L), shown, sun, moon, 1, "3f2b9c1e-6d4a-4b7e-9a51-0c8d2e7f4a10", fetchedAt)
        try {
            SnapshotCodec.encode(snap)
            fail("encode accepted fetchedAt = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("fetchedAt"))
        }
    }

    @Test fun moreThanTwentyShownWarningsAreCappedNotRejected() {
        val many = (1..25).map { Alert("w$it", "MetService", "yellow", "Wind Warning $it") }
        val snap = Phone072Mapper.toSnapshot(forecast(), many, sun, moon, 1, "3f2b9c1e-6d4a-4b7e-9a51-0c8d2e7f4a10", fetchedAt + 1000)
        val d = SnapshotCodec.decode(SnapshotCodec.encode(snap), now)
        assertEquals(Limits.MAX_WARNINGS, (d as Decoded.Ok).value.warnings.size)
    }
}
