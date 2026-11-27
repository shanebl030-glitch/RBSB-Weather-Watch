// place.timezone must be a real IANA time zone, not just the right characters (review fix for PR #2).
// The cases in sync/samples/timezones.json are also checked by scripts/check-samples.py, and CI
// compares the full lists of names Java and Python accept, so the two checkers agree.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class TimeZoneTest {
    private val now = SampleFiles.now
    private val cases = JSONObject(SampleFiles.text("timezones.json"))

    private fun decodeWith(zone: String): Decoded<Snapshot> {
        val o = JSONObject(SampleFiles.text("snapshot/valid/clear-day.json"))
        o.getJSONObject("place").put("timezone", zone)
        return SnapshotCodec.parse(o.toString(), now)
    }

    @Test fun realIanaZonesAreAccepted() {
        val accept = cases.getJSONArray("accept")
        for (i in 0 until accept.length()) {
            val zone = accept.getString(i)
            val d = decodeWith(zone)
            assertTrue("$zone accepted: $d", d is Decoded.Ok)
            assertEquals(zone, (d as Decoded.Ok).value.place.timezone)
        }
        assertTrue(TimeZones.isValid("Pacific/Auckland"))
        assertTrue(TimeZones.isValid("UTC"))
    }

    @Test fun unknownOrMalformedZonesAreRejectedWithTheExpectedReason() {
        val reject = cases.getJSONObject("reject")
        for (zone in reject.keySet().sorted()) {
            val d = decodeWith(zone)
            assertTrue("${zone.replace("\n", "\\n")} rejected", d is Decoded.Rejected)
            d as Decoded.Rejected
            assertEquals(zone.replace("\n", "\\n"), Reason.valueOf(reject.getString(zone)), d.reason)
            assertEquals("place.timezone", d.path)
        }
        assertFalse(TimeZones.isValid("Not/ARealZone"))
    }

    @Test fun strictnessGoesBeyondZoneIdOf() {
        // ZoneId.of() also accepts offset forms; the format requires a region name from the database.
        ZoneId.of("UTC+13")
        ZoneId.of("Z")
        assertFalse(TimeZones.isValid("UTC+13"))
        assertFalse(TimeZones.isValid("Z"))
        assertFalse(TimeZones.isValid("+13:00"))
    }

    @Test fun everyAcceptedNameIsARealZoneAndNoExcludedNameIsAccepted() {
        assertTrue("hundreds of zones", TimeZones.accepted.size > 400)
        for (z in TimeZones.accepted) ZoneId.of(z)                     // would throw if not real
        for (z in TimeZones.EXCLUDED) assertFalse(z, TimeZones.isValid(z))
        assertTrue(TimeZones.accepted.none { it.startsWith("SystemV/") })
    }

    @Test fun exclusionListMatchesTheContractFile() {
        val rules = JSONObject(File(SampleFiles.dir.parentFile, "contract/timezone-rules.json").readText())
        val excluded = rules.getJSONArray("excluded").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val prefixes = rules.getJSONArray("excludedPrefixes").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(TimeZones.EXCLUDED, excluded)
        assertEquals(TimeZones.EXCLUDED_PREFIXES, prefixes)
    }

    @Test fun encodeRefusesAnUnknownZone() {
        val s = SampleFiles.snapshot()
        for (zone in listOf("auto", "Not/ARealZone")) {
            try {
                SnapshotCodec.encode(s.copy(place = s.place.copy(timezone = zone)))
                throw AssertionError("encode accepted $zone")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("BAD_VALUE"))
            }
        }
    }

    @Test fun writesTheAcceptedListForTheCrossCheck() {
        // CI compares this file with Python's accepted list (scripts/check-samples.py --java-zones).
        val out = File(System.getProperty("rbsb.zoneList") ?: return)
        out.parentFile.mkdirs()
        out.writeText(TimeZones.accepted.sorted().joinToString("\n", postfix = "\n"))
        println("TIMEZONES (Java ${System.getProperty("java.version")}, tzdb ${java.time.zone.ZoneRulesProvider.getVersions("UTC").lastKey()}): ${TimeZones.accepted.size} names accepted")
    }
}
