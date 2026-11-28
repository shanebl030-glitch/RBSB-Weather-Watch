// The sky's conditions from Stage 1 snapshots, including the live Moon (Stage 3 decision D10,
// which replaced Stage 2's option C).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import io.github.shanebl030glitch.rbsbweather.data.Moon
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class SnapshotConditionsTest {
    private val samples = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid").let { d ->
        SampleData.decode(d.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() })
    }.associateBy { it.name }
    private fun cond(name: String) = samples.getValue(name).snapshot.let { conditionsFromSnapshot(it, it.fetchedAt) }

    @Test fun everySampleGivesConditionsWithoutErrors() {
        for ((name, s) in samples) {
            val c = conditionsFromSnapshot(s.snapshot, s.snapshot.fetchedAt)
            assertTrue("$name daylight", c.daylight in 0.0..1.0)
            assertTrue("$name wind", c.windKmh >= 0.0)
        }
    }

    @Test fun eachWeatherSampleShowsItsKind() {
        val want = mapOf("clear-day" to Kind.CLEAR, "partly-cloudy-day" to Kind.PARTLY, "overcast" to Kind.OVERCAST,
            "fog" to Kind.FOG, "drizzle" to Kind.DRIZZLE, "rain-night-warnings" to Kind.RAIN, "snow-northern" to Kind.SNOW,
            "thunder-warning" to Kind.THUNDER)
        for ((name, kind) in want) assertEquals(name, kind, cond(name).kind)
    }

    @Test fun dayAndNightFollowTheSamplesOwnTimeAndSunTimes() {
        assertEquals(1.0, cond("clear-day").daylight, 0.0)          // 10:00, sunrise 05:58, sunset 20:21
        assertEquals(0.0, cond("clear-night").daylight, 0.0)        // 23:00
        assertEquals(0.0, cond("polar-night").daylight, 0.0)        // no sunrise or sunset: isDay = false
        assertTrue(cond("clear-day").southern)
        assertTrue(!cond("snow-northern").southern)
    }

    @Test fun moonPhaseAndIlluminationAreCalculatedLive() {
        // Decision D10: the same calculation as Desktop and Android for the moment shown, not the
        // snapshot's fixed values, so the Moon stays consistent however old the data is.
        val s = samples.getValue("clear-day").snapshot
        for (ageH in listOf(0L, 3L, 12L)) {
            val at = s.fetchedAt + ageH * 3600_000L
            val live = Moon.phase(Instant.ofEpochMilli(at))
            val c = conditionsFromSnapshot(s, at)
            assertEquals("age $ageH h", live.phaseAngle, c.moonPhaseAngle!!, 1e-9)
            assertEquals("age $ageH h", live.elongation / 360.0, c.moonAge, 1e-9)
            assertEquals("age $ageH h", live.brightLimb, c.moonLimb, 1e-9)
        }
        assertEquals(0.0, phaseAngleFromIllumination(1.0), 1e-9)       // full (kept for the fallback)
        assertEquals(180.0, phaseAngleFromIllumination(0.0), 1e-9)     // new
        assertEquals(90.0, phaseAngleFromIllumination(0.5), 1e-9)      // quarter
    }

    @Test fun moonPositionAndTiltAreCalculatedLive() {
        val s = samples.getValue("clear-night").snapshot
        val c = conditionsFromSnapshot(s, s.fetchedAt)
        val g = Moon.skyGeometry(Moon.julianDay(Instant.ofEpochMilli(s.fetchedAt)), s.place.latitude, s.place.longitude)
        assertEquals(g.q, c.moonRotation!!, 1e-9)
        assertEquals(g.altitude, c.moonAltitude, 1e-9)
        assertEquals(g.altitude > g.h0, c.moonUp)
        assertEquals(Moon.phase(Instant.ofEpochMilli(s.fetchedAt)).brightLimb, c.moonLimb, 1e-9)
        // an hour later the live position and the phase have both moved on (decision D10)
        val later = conditionsFromSnapshot(s, s.fetchedAt + 3600_000L)
        assertTrue(later.moonAltitude != c.moonAltitude)
        assertTrue(later.moonPhaseAngle!! != c.moonPhaseAngle!!)
    }

    @Test fun moonRiseAndSetComeFromTheSnapshotWhenTheyBracketTheMoment() {
        val s0 = samples.getValue("clear-night").snapshot
        val at = s0.fetchedAt
        val s = s0.copy(moon = s0.moon.copy(rise = at - 3 * 3600_000L, set = at + 5 * 3600_000L))
        val c = conditionsFromSnapshot(s, at)
        if (c.moonUp) {
            assertEquals(at - 3 * 3600_000L, c.moonRiseMs)
            assertEquals(at + 5 * 3600_000L, c.moonSetMs)
            assertNotNull(c.moonProgress)
            assertEquals(3.0 / 8.0, c.moonProgress!!, 1e-9)
        }
    }

    @Test fun moonIsAboveOrBelowTheHorizonAsCalculated() {
        // Checked against the desktop app's moon.py (which Moon.kt ports line by line):
        // clear-night (23:00 Auckland) the Moon is 13 degrees below the horizon, so none is drawn;
        // fog (07:00 Wellington) it is 14 degrees up; polar-night (Tromsø) 9 degrees up.
        assertTrue("clear-night: below the horizon", !cond("clear-night").moonUp)
        assertTrue(cond("clear-night").moonAltitude < -10)
        assertTrue("fog: up", cond("fog").moonUp)
        assertTrue("polar-night: up", cond("polar-night").moonUp)
        for (name in listOf("clear-day", "partly-cloudy-day", "rain-night-warnings", "thunder-warning")) assertTrue(name, !cond(name).moonUp)
    }
}
