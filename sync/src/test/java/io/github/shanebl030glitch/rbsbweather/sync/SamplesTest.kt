// Every sample file decodes (or is rejected) exactly as expected.json says.
// The independent Python check (scripts/check-samples.py) checks the same files against the same
// expected.json, so both passing means the Kotlin decoder and the schema check agree.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SamplesTest {
    private val now = SampleFiles.now

    @Test fun everySampleFileIsListedAndEveryListedFileExists() {
        for (kind in listOf("snapshot", "settings", "refresh")) {
            val listed = SampleFiles.valid(kind).toSet() + SampleFiles.invalid(kind).keys
            assertEquals("sample files for $kind", listed, SampleFiles.onDisk(kind))
        }
        assertTrue("at least 15 valid snapshots", SampleFiles.valid("snapshot").size >= 15)
        assertTrue("at least 40 invalid snapshots", SampleFiles.invalid("snapshot").size >= 40)
    }

    @Test fun validSnapshotsDecodeAndSurviveARoundTrip() {
        for (rel in SampleFiles.valid("snapshot")) {
            val s = when (val d = SnapshotCodec.parse(SampleFiles.text(rel), now)) {
                is Decoded.Ok -> d.value
                is Decoded.Rejected -> { fail("$rel rejected: ${d.reason} at ${d.path}: ${d.detail}"); return }
            }
            // JSON text round trip
            val again = SnapshotCodec.parse(SnapshotCodec.toJsonText(s), now)
            assertEquals("$rel JSON round trip", Decoded.Ok(s), again)
            // Compressed round trip, as over the Data Layer
            assertEquals("$rel compressed round trip", Decoded.Ok(s), SnapshotCodec.decode(SnapshotCodec.encode(s), now))
            println("SAMPLE OK $rel")
        }
    }

    @Test fun invalidSnapshotsAreRejectedWithTheExpectedReason() {
        for ((rel, reason) in SampleFiles.invalid("snapshot")) {
            val d = SnapshotCodec.parse(SampleFiles.text(rel), now)
            if (d !is Decoded.Rejected) fail("$rel was accepted, expected $reason")
            d as Decoded.Rejected
            assertEquals("$rel (${d.path}: ${d.detail})", reason, d.reason)
            println("SAMPLE REJECTED $rel: ${d.reason} at ${d.path.ifEmpty { "(root)" }}")
        }
    }

    @Test fun settingsSamples() {
        for (rel in SampleFiles.valid("settings")) {
            val d = SettingsCodec.parse(SampleFiles.text(rel), now)
            if (d !is Decoded.Ok) fail("$rel rejected: $d")
            val s = (d as Decoded.Ok).value
            assertEquals("$rel round trip", d, SettingsCodec.parse(SettingsCodec.toJson(s).toString(), now))
            assertEquals("$rel compressed round trip", d, SettingsCodec.decode(SettingsCodec.encode(s), now))
        }
        for ((rel, reason) in SampleFiles.invalid("settings")) {
            val d = SettingsCodec.parse(SampleFiles.text(rel), now)
            assertTrue("$rel should be rejected", d is Decoded.Rejected)
            assertEquals(rel, reason, (d as Decoded.Rejected).reason)
        }
    }

    @Test fun refreshSamples() {
        for (rel in SampleFiles.valid("refresh")) {
            val d = RefreshCodec.parse(SampleFiles.text(rel), now)
            if (d !is Decoded.Ok) fail("$rel rejected: $d")
            val r = (d as Decoded.Ok).value
            assertEquals("$rel compressed round trip", d, RefreshCodec.decode(RefreshCodec.encode(r), now))
        }
        for ((rel, reason) in SampleFiles.invalid("refresh")) {
            val d = RefreshCodec.parse(SampleFiles.text(rel), now)
            assertTrue("$rel should be rejected", d is Decoded.Rejected)
            assertEquals(rel, reason, (d as Decoded.Rejected).reason)
        }
    }

    @Test fun samplesCoverEverySkyTypeDayAndNightAndBothHemispheres() {
        val snaps = SampleFiles.valid("snapshot").map { (SnapshotCodec.parse(SampleFiles.text(it), now) as Decoded.Ok).value }
        // WMO code groups the phone's scene code maps to its sky kinds
        val groups = mapOf("clear" to setOf(0, 1), "partly" to setOf(2), "overcast" to setOf(3), "fog" to setOf(45, 48),
            "drizzle" to (51..57).toSet(), "rain" to (61..67).toSet() + (80..82).toSet(),
            "snow" to (71..77).toSet() + setOf(85, 86), "thunder" to setOf(95, 96, 99))
        for ((name, codes) in groups) assertTrue("a sample with $name", snaps.any { it.current.weatherCode in codes })
        assertTrue("a day sample", snaps.any { it.current.isDay })
        assertTrue("a night sample", snaps.any { !it.current.isDay })
        assertTrue("southern hemisphere", snaps.any { it.place.latitude < 0 })
        assertTrue("northern hemisphere", snaps.any { it.place.latitude > 0 })
        assertTrue("polar: Moon always up", snaps.any { it.moon.alwaysUp })
        assertTrue("warnings including a user limit", snaps.any { s -> s.warnings.any { it.level == WarningLevel.LIMIT && !it.official } })
    }
}
