// The animated sky's conditions and moving parts, mirroring the desktop app's test_scene.py.
// Ported from RBSB-Weather for Android v0.7.2 (cdf422d), app/src/test/.../ui/scene/SceneTest.kt.
// Watch changes: forecasts are Stage 1 snapshots (sceneSnap below, built from the clear-day sample);
// each call passes its moment explicitly; forecast playback is not on the watch, so that test checks
// only hourlyAt and a later hour's weather. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import io.github.shanebl030glitch.rbsbweather.sync.SnapHour
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

private val BASE: Snapshot by lazy {
    val f = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid/clear-day.json")
    SampleData.decode(mapOf("clear-day" to f.readText())).single().snapshot
}

fun nzMs(local: String): Long = LocalDateTime.parse(local).atZone(ZoneId.of("Pacific/Auckland")).toInstant().toEpochMilli()

/** A snapshot at a chosen local time with sunrise 06:00 and sunset 20:10 (as the desktop's _fc). */
fun sceneSnap(code: Int = 2, now: String = "2026-11-15T10:15", lat: Double = -36.8, windDir: Double? = 225.0,
              isDay: Boolean = true, fetchedAt: Long? = null): Snapshot {
    val date = now.take(10).takeIf { it.length == 10 && it[4] == '-' } ?: "2026-11-15"
    val d0 = BASE.daily[0].copy(date = date, sunrise = "${date}T06:00", sunset = "${date}T20:10")
    val at = fetchedAt ?: try { nzMs(now) } catch (e: Exception) { nzMs("2026-11-15T10:15") }
    return BASE.copy(
        place = BASE.place.copy(latitude = lat),
        current = BASE.current.copy(weatherCode = code, time = now, isDay = isDay, wind = BASE.current.wind.copy(direction = windDir)),
        daily = listOf(d0) + BASE.daily.drop(1), fetchedAt = at, sentAt = at,
    )
}

private fun cfs(s: Snapshot, nowMs: Long = s.fetchedAt, offsetS: Double = 0.0) = conditionsFromSnapshot(s, nowMs, offsetS)

class SceneTest {
    @Test fun everyKnownCodeClassified() {
        for (code in WeatherCodes.known) {
            val (k, i) = classify(code)
            assertNotNull(k); assertTrue(i in 0.0..1.0)
        }
        assertEquals(Kind.PARTLY, classify(null).first)
        assertEquals(Kind.PARTLY, classify(42).first)
        assertEquals(Kind.RAIN to 1.0, classify(65))
        assertEquals(Kind.THUNDER, classify(95).first)
    }

    @Test fun moonAgeKnownDates() {
        assertEquals(0.0, io.github.shanebl030glitch.rbsbweather.data.Moon.ageFraction(java.time.Instant.parse("2000-01-06T18:14:00Z")), 0.01)
        assertEquals(0.5, io.github.shanebl030glitch.rbsbweather.data.Moon.ageFraction(java.time.Instant.parse("2024-01-25T17:54:00Z")), 0.03)
    }

    @Test fun dayNightTwilight() {
        assertEquals(1.0, cfs(sceneSnap(now = "2026-11-15T13:00")).daylight, 0.0)
        val night = cfs(sceneSnap(now = "2026-11-15T23:30"))
        assertEquals(0.0, night.daylight, 0.0); assertEquals(0.0, night.twilight, 0.0)
        val dusk = cfs(sceneSnap(now = "2026-11-15T20:10"))
        assertEquals(1.0, dusk.twilight, 0.01); assertEquals(0.5, dusk.daylight, 0.01)
        val dawn = cfs(sceneSnap(now = "2026-11-15T05:50"))
        assertTrue(dawn.twilight > 0.6); assertTrue(dawn.daylight < 0.5)
    }

    @Test fun sunProgress() {
        assertEquals(0.0, cfs(sceneSnap(now = "2026-11-15T06:00")).sunProgress, 1e-9)
        assertEquals(0.5, cfs(sceneSnap(now = "2026-11-15T13:05")).sunProgress, 0.01)
    }

    @Test fun clockAdvancesSinceFetch() {
        val s = sceneSnap(now = "2026-11-15T19:00")
        assertEquals(0.0, cfs(s, s.fetchedAt + 3 * 3_600_000L).daylight, 0.0)    // 22:00 → night
        assertEquals(1.0, cfs(s, s.fetchedAt).daylight, 0.0)
    }

    @Test fun fallbackWithoutSunTimesUsesIsDay() {
        assertEquals(0.0, cfs(sceneSnap(isDay = false).copy(daily = emptyList())).daylight, 0.0)
        assertEquals(1.0, cfs(sceneSnap(isDay = true, now = "garbage")).daylight, 0.0)
    }

    @Test fun windDirectionAndHemisphere() {
        assertTrue(cfs(sceneSnap(windDir = 270.0)).windEast > 0)        // from the west → right
        assertTrue(cfs(sceneSnap(windDir = 90.0)).windEast < 0)         // from the east → left
        assertTrue(cfs(sceneSnap(lat = -36.8)).southern)
        assertFalse(cfs(sceneSnap(lat = 51.5)).southern)
        assertEquals(1.0, windDrift(270.0), 1e-9)
        assertEquals(-1.0, windDrift(90.0), 1e-9)
        assertEquals(0.3 + 0.7 * 0.7071, windDrift(315.0), 0.01)
        assertEquals(MIN_EAST_WEST, windDrift(0.0), 1e-9)                // north wind still drifts
        assertEquals(1.0, windDrift(null), 0.0)
    }

    @Test fun cloudSpeedsFollowTheDesktopTable() {
        assertEquals(1 / 150.0, cloudSpeed(0.0), 1e-9)
        assertEquals(1 / 150.0, cloudSpeed(null), 1e-9)
        assertEquals(1 / 70.0, cloudSpeed(10.0), 1e-9)
        assertEquals(1 / 32.0, cloudSpeed(40.0), 1e-9)
        assertEquals(1 / 19.0, cloudSpeed(250.0), 1e-9)
        assertTrue(cloudSpeed(15.0) in (1 / 70.0)..(1 / 50.0))
    }

    @Test fun cloudAmountOrdering() {
        fun amt(code: Int) = classify(code).let { Conditions(it.first, it.second).cloudAmount }
        assertEquals(0.0, amt(0), 0.0)
        assertTrue(amt(1) < amt(2)); assertTrue(amt(2) < amt(3))
        assertTrue(classify(99).let { Conditions(it.first, it.second).darkness } > classify(61).let { Conditions(it.first, it.second).darkness })
    }

    @Test fun particlesBoundedOverALongRun() {
        for (q in Quality.values()) for (code in listOf(65, 75, 99, 55, 45)) {
            val s = SceneEngine(seed = 9)
            s.quality = q
            s.setSize(500, 200)
            val (k, i) = classify(code)
            s.setConditions(Conditions(k, i, windKmh = 60.0))
            val counts = listOf(s.weather.drops.size, s.weather.flakes.size, s.clouds.size, s.weather.fog.size)
            repeat(6000) { s.advance(0.1) }                       // 10 simulated minutes
            assertEquals(counts, listOf(s.weather.drops.size, s.weather.flakes.size, s.clouds.size, s.weather.fog.size))
            assertTrue(s.weather.splashes.size <= q.maxSplashes)
            s.weather.drops.forEach { assertTrue(it.x in -60.0..560.0 && it.y in -40.0..220.0) }
            s.weather.flakes.forEach { assertTrue(it.x in -10.0..510.0 && it.y in -10.0..210.0) }
            s.clouds.forEach { assertTrue(it.x >= -it.w - 40 && it.x <= 540) }
        }
    }

    @Test fun lightningOnlyInStorms() {
        val s = SceneEngine(seed = 4)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.THUNDER, 1.0))
        var flashes = 0
        repeat(600) { val before = s.flash; s.advance(1 / 30.0); if (s.flash > before) flashes++ }
        assertTrue(flashes >= 2)
        assertNotNull(s.bolt)
        s.setConditions(Conditions(Kind.RAIN, 1.0))
        repeat(600) { s.advance(1 / 30.0) }
        assertEquals(0.0, s.flash, 0.0)
    }

    @Test fun rainSlantsWithWind() {
        for (sign in listOf(1.0, -1.0)) {
            val s = SceneEngine(seed = 2)
            s.setSize(400, 200)
            s.setConditions(Conditions(Kind.RAIN, 0.5, windKmh = 40.0, windEast = sign))
            val xs = s.weather.drops.map { it.x }
            s.advance(0.02)
            val moved = s.weather.drops.zip(xs).map { (d, x) -> d.x - x }.filter { kotlin.math.abs(it) < 50 }.average()
            assertEquals(sign > 0, moved > 0)
        }
    }

    @Test fun windEasesInsteadOfJumping() {
        val s = SceneEngine(seed = 1)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.PARTLY, 0.5, windKmh = 10.0, windEast = 1.0, fromForecast = true))
        s.setConditions(Conditions(Kind.PARTLY, 0.5, windKmh = 60.0, windEast = -1.0, fromForecast = true))
        assertEquals(1.0, s.driftEast!!, 1e-9)                    // not jumped
        s.advance(WIND_EASE_S)
        assertTrue(s.driftEast!! < 1.0 && s.driftEast!! > -1.0)
        repeat(100) { s.advance(0.5) }
        assertEquals(-1.0, s.driftEast!!, 0.01)
        assertEquals(cloudSpeed(60.0), s.driftSpeed!!, 1e-4)
    }

    @Test fun weatherChangeFadesAndEnds() {
        val s = SceneEngine(seed = 3)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.RAIN, 0.6))
        s.setConditions(Conditions(Kind.CLEAR, 0.0))
        assertEquals(0.0, s.cloudFade, 0.0)
        assertTrue(s.fading.isNotEmpty() && s.oldWeather.isNotEmpty())
        repeat(20) { s.advance(0.1) }
        assertEquals(1.0, s.cloudFade, 0.0)
        assertTrue(s.fading.isEmpty() && s.oldWeather.isEmpty())
        // without animation the change is instant
        val still = SceneEngine(seed = 3).apply { animations = false; setSize(400, 200); setConditions(Conditions(Kind.RAIN, 0.6)) }
        still.setConditions(Conditions(Kind.CLEAR, 0.0))
        assertEquals(1.0, still.cloudFade, 0.0)
    }

    @Test fun nightAndDaySkiesDiffer() {
        val day = SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0)) }.skyColors()
        val night = SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0)) }.skyColors()
        assertTrue(night.first.blue + night.first.green < day.first.blue + day.first.green)
    }

    @Test fun arcGoesFromBottomLeftThroughTheTopToBottomRight() {
        val rise = arcPoint(0.0, 0f, 0f, 400f, 200f, 20f)!!
        val top = arcPoint(0.5, 0f, 0f, 400f, 200f, 20f)!!
        val set = arcPoint(1.0, 0f, 0f, 400f, 200f, 20f)!!
        assertTrue(rise.second > 200f && set.second > 200f)              // hidden below the horizon
        assertTrue(top.second < 60f && rise.first < top.first && top.first < set.first)
        assertNull(arcPoint(1.2, 0f, 0f, 400f, 200f, 20f)); assertNull(arcPoint(null, 0f, 0f, 400f, 200f, 20f))
    }

    @Test fun moonIsPlacedForThePlaceAndTime() {
        // 21:00 NZDT on 15 Nov 2026 the Moon is up in Auckland; at 08:00 the next morning it isn't
        val evening = cfs(sceneSnap(now = "2026-11-15T21:00"))
        assertTrue(evening.moonUp)
        val p = evening.moonProgressAt(nzMs("2026-11-15T21:00"))!!
        assertTrue("$p", p in 0.0..1.0)
        assertNotNull(evening.moonPhaseAngle)
        val morning = cfs(sceneSnap(now = "2026-11-16T08:00"))
        assertFalse(morning.moonUp)
    }

    @Test fun aLaterHourUsesThatHoursWeather() {
        val base = sceneSnap(code = 0, now = "2026-11-15T10:00")
        val hours = (0 until 30).map { i ->
            SnapHour(LocalDateTime.parse("2026-11-15T10:00").plusHours(i.toLong()).toString(), 15.0 + i, 10.0, 0.0,
                if (i == 3) 63 else 0, wind = null)
        }
        val s = base.copy(hourly = hours)
        assertEquals(Kind.CLEAR, cfs(s).kind)
        assertEquals(Kind.RAIN, cfs(s, s.fetchedAt, 3 * 3600.0 + 600).kind)
        assertEquals(hours[3], hourlyAt(s, LocalDateTime.parse("2026-11-15T13:40")))
        assertEquals(Wind(14.0, 26.0, 225.0), s.current.wind)              // the sample's current wind
    }
}
