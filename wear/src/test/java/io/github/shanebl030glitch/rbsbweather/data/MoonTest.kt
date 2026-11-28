// Moon astronomy against the same PyEphem reference values as the desktop app's tests.
// Copied from RBSB-Weather for Android v0.7.2 (cdf422d), app/src/test/.../data/MoonTest.kt, with its
// moon_reference.json. Watch change: resource() reads from the classpath here (helper below).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

class MoonTest {
    private val ref = JSONObject(resource("moon_reference.json"))
    private fun t(s: String): Instant = OffsetDateTime.parse(s).toInstant()

    @Test fun riseSetMatchesReference() {
        val a = ref.getJSONArray("riseset")
        var worst = 0L
        for (i in 0 until a.length()) {
            val row = a.getJSONObject(i)
            val zone = Moon.zoneFor(row.getString("tz"))
            val d = LocalDate.parse(row.getString("date"))
            val (rise, set) = Moon.riseSet(d, row.getDouble("lat"), row.getDouble("lon"), zone)
            for ((mine, key) in listOf(rise to "rise", set to "set")) {
                val r = if (row.isNull(key)) null else row.getString(key)
                assertEquals("${row.getString("place")} $d $key: $mine vs $r", r == null, mine == null)
                if (mine != null && r != null) {
                    val diff = abs(mine.toEpochSecond() - t(r).epochSecond)
                    worst = maxOf(worst, diff)
                    val limit = if (row.getString("place") == "Tromso") 300 else 90
                    assertTrue("${row.getString("place")} $d $key off by ${diff}s", diff < limit)
                    assertEquals(d, mine.toLocalDate())
                }
            }
        }
        println("Moon rise/set worst vs PyEphem: ${worst}s")
    }

    @Test fun illuminationMatchesReference() {
        val a = ref.getJSONArray("illum")
        var worst = 0.0
        for (i in 0 until a.length()) {
            val row = a.getJSONArray(i)
            worst = maxOf(worst, abs(Moon.phase(t(row.getString(0))).illumination * 100 - row.getDouble(1)))
        }
        assertTrue("worst $worst", worst < 0.3)
    }

    @Test fun principalPhaseTimes() {
        val a = ref.getJSONArray("phases")
        var worst = 0L
        for (i in 0 until a.length()) {
            val row = a.getJSONArray(i)
            val mine = Moon.nextPhaseTime(t(row.getString(1)), row.getDouble(0))
            worst = maxOf(worst, abs(mine.epochSecond - t(row.getString(2)).epochSecond))
        }
        assertTrue("worst ${worst}s", worst < 120)
    }

    @Test fun parallacticAngleMatchesReference() {
        val a = ref.getJSONArray("parallactic")
        var worst = 0.0
        for (i in 0 until a.length()) {
            val row = a.getJSONArray(i)
            val g = Moon.skyGeometry(Moon.julianDay(t(row.getString(3))), row.getDouble(1), row.getDouble(2))
            worst = maxOf(worst, abs(((g.q - row.getDouble(4) + 180) % 360 + 360) % 360 - 180))
        }
        assertTrue("worst $worst", worst < 2.5)
    }

    @Test fun phaseNames() {
        assertEquals("New Moon", Moon.phaseName(0.0)); assertEquals("New Moon", Moon.phaseName(359.0))
        assertEquals("Waxing Crescent", Moon.phaseName(45.0)); assertEquals("First Quarter", Moon.phaseName(90.0))
        assertEquals("Waxing Gibbous", Moon.phaseName(135.0)); assertEquals("Full Moon", Moon.phaseName(180.0))
        assertEquals("Waning Gibbous", Moon.phaseName(225.0)); assertEquals("Last Quarter", Moon.phaseName(270.0))
        assertEquals("Waning Crescent", Moon.phaseName(300.0))
    }

    @Test fun sevenDaysNamePrincipalPhaseOnlyOnItsDay() {
        val days = Moon.moonDays(-36.85, 174.76, ZoneId.of("Pacific/Auckland"), LocalDate.of(2026, 11, 15))
        assertEquals(7, days.size)
        assertEquals(1, days.count { it.name == "First Quarter" })
        assertEquals("First Quarter", days[3].name)
        val ill = days.map { it.phase.illumination }
        assertEquals(ill.sorted(), ill)
    }

    @Test fun waxingAndBrightLimb() {
        assertTrue(Moon.phase(Instant.parse("2026-11-15T00:00:00Z")).waxing)
        assertFalse(Moon.phase(Instant.parse("2026-11-28T00:00:00Z")).waxing)
        val chi = Moon.phase(Instant.parse("2026-11-15T00:00:00Z")).brightLimb
        assertTrue("$chi", chi > 200 && chi < 340)                      // towards the setting sun
        val chi2 = Moon.phase(Instant.parse("2026-11-29T00:00:00Z")).brightLimb
        assertTrue("$chi2", chi2 > 20 && chi2 < 160)
    }

    @Test fun polarAlwaysUpOrDown() {
        val days = Moon.moonDays(78.2, 15.6, ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 11, 1), 28)    // Svalbard
        assertTrue(days.any { it.alwaysUp || it.alwaysDown })
        days.filter { it.alwaysUp || it.alwaysDown }.forEach { assertNull(it.rise); assertNull(it.set) }
    }

    @Test fun zones() {
        assertEquals(ZoneId.of("Pacific/Auckland"), Moon.zoneFor("Pacific/Auckland"))
        assertEquals(ZoneOffset.ofHours(13), Moon.zoneFor("auto", 46800))
        assertEquals(ZoneOffset.ofHours(1), Moon.zoneFor("Not/AZone", 3600))
        assertEquals(ZoneOffset.UTC, Moon.zoneFor(null))
    }

    @Test fun upcomingSortedAndFuture() {
        val now = Instant.parse("2026-11-15T00:00:00Z")
        val up = Moon.upcomingPhases(now, ZoneId.of("Pacific/Auckland"))
        assertEquals(setOf("New Moon", "First Quarter", "Full Moon", "Last Quarter"), up.map { it.first }.toSet())
        assertEquals(up.map { it.second }.sorted(), up.map { it.second })
        assertTrue(up.all { it.second.toInstant().isAfter(now) })
    }

    @Test fun hemispheresAtHighestPoint() {
        val t0 = Instant.parse("2026-11-15T00:00:00Z")
        val north = Moon.skyGeometry(Moon.julianDay(Moon.nextTransit(t0, -0.13)), 51.51, -0.13).q
        val south = Moon.skyGeometry(Moon.julianDay(Moon.nextTransit(t0, 174.76)), -36.85, 174.76).q
        assertTrue("$north", minOf(north, 360 - north) < 1.0)
        assertTrue("$south", abs(south - 180) < 1.0)
    }

    @Test fun skyViewNowOrNextHighest() {
        val tz = ZoneId.of("Pacific/Auckland")
        val up = Moon.skyView(Instant.parse("2026-11-15T08:00:00Z"), -36.85, 174.76, tz)
        assertTrue(up.isNow && up.up)
        val down = Moon.skyView(Instant.parse("2026-11-15T19:00:00Z"), -36.85, 174.76, tz)
        assertFalse(down.isNow)
        assertTrue(down.whenT.toInstant().isAfter(Instant.parse("2026-11-15T19:00:00Z")))
        assertTrue(down.up)
    }

    @Test fun passIsAroundNow() {
        val now = Instant.parse("2026-11-15T08:00:00Z")
        val p = Moon.moonPass(now, -36.85, 174.76)
        assertTrue(p.up)
        assertTrue(p.rise!!.isBefore(now) && p.set!!.isAfter(now))
        assertFalse(Moon.moonPass(Instant.parse("2026-11-15T19:00:00Z"), -36.85, 174.76).up)
    }
}

private fun resource(name: String): String =
    MoonTest::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
