// Forward and backward compatibility, and the hourly wind fallback (Stage 1 decision 3).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityTest {
    private val now = SampleFiles.now
    private fun ok(text: String): Snapshot {
        val d = SnapshotCodec.parse(text, now)
        assertTrue("accepted: $d", d is Decoded.Ok)
        return (d as Decoded.Ok).value
    }

    @Test fun unknownFieldsAnywhereAreIgnored() {
        val base = SampleFiles.snapshot()
        val o = JSONObject(SnapshotCodec.toJsonText(base))
        o.put("addedInV1_5", JSONObject().put("x", 1))
        o.getJSONObject("place").put("elevation", 12)
        o.getJSONObject("current").put("visibility", 10000)
        o.getJSONArray("hourly").getJSONObject(0).put("uv", 3)
        o.getJSONArray("daily").getJSONObject(0).put("moonPhase", 0.5)
        o.getJSONObject("moon").put("libration", 1.2)
        assertEquals(base, ok(o.toString()))
    }

    @Test fun absentOptionalValuesReadAsNullOrDefault() {
        val s = SampleFiles.snapshot("minimal")
        assertNull(s.current.temperature)
        assertEquals(Wind.NONE, s.current.wind)
        assertEquals("", s.place.country)
        assertTrue(s.hourly.isEmpty() && s.daily.isEmpty() && s.warnings.isEmpty())
        assertNull(s.sun.rise)
        assertNull(s.moon.rise)
        assertEquals(false, s.moon.alwaysUp)
    }

    @Test fun nullAndAbsentMeanTheSame() {
        val nulls = SampleFiles.snapshot("explicit-nulls")
        assertNull(nulls.current.temperature)
        assertEquals(Wind.NONE, nulls.current.wind)
        assertTrue(nulls.hourly.all { it.temperature == null && it.weatherCode == null && it.wind == null })
        assertTrue(nulls.daily.all { it.sunrise == null && it.tempMax == null })
    }

    @Test fun hourlyWindIsUsedWhenSentAndCurrentWindOtherwise() {
        val s = SampleFiles.snapshot("hourly-wind-mixed")
        val current = s.current.wind
        assertTrue(current.hasAny)
        s.hourly.forEachIndexed { i, h ->
            if (i % 3 == 0) {
                assertTrue("hour $i has its own wind", h.wind != null)
                assertEquals(h.wind, s.windAt(h))
            } else {
                assertNull("hour $i has no wind", h.wind)
                assertEquals("hour $i falls back to current wind", current, s.windAt(h))
            }
        }
    }

    @Test fun anEmptyWindObjectFallsBackToo() {
        val o = JSONObject(SnapshotCodec.toJsonText(SampleFiles.snapshot()))
        o.getJSONArray("hourly").getJSONObject(0).put("wind", JSONObject())
        o.getJSONArray("hourly").getJSONObject(1).put("wind", JSONObject().put("speed", JSONObject.NULL))
        val s = ok(o.toString())
        assertNull(s.hourly[0].wind)
        assertNull(s.hourly[1].wind)
        assertEquals(s.current.wind, s.windAt(s.hourly[0]))
    }

    @Test fun samples072StyleWithoutHourlyWindUseCurrentWindForEveryHour() {
        val s = SampleFiles.snapshot("clear-day")
        assertTrue(s.hourly.isNotEmpty() && s.hourly.all { it.wind == null })
        assertTrue(s.hourly.all { s.windAt(it) == s.current.wind })
    }

    @Test fun wholeNumbersWrittenWithADecimalPointAreAccepted() {
        // Edited as text: org.json would write 12.0 back as 12.
        val text = SnapshotCodec.toJsonText(SampleFiles.snapshot())
        assertTrue(text.contains("\"sequence\":12") && text.contains("\"weatherCode\":0"))
        val s = ok(text.replace("\"sequence\":12", "\"sequence\":12.0").replace("\"weatherCode\":0", "\"weatherCode\":0.0"))
        assertEquals(12L, s.sequence)
        assertEquals(0, s.current.weatherCode)
    }
}
