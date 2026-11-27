// Paths, capability names, settings round trips and the text/coordinate helpers.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndHelpersTest {
    @Test fun pathsAndCapabilitiesMatchThePlan() {
        assertEquals("/rbsb/v1/snapshot", SyncFormat.PATH_SNAPSHOT)
        assertEquals("/rbsb/v1/settings", SyncFormat.PATH_SETTINGS)
        assertEquals("/rbsb/v1/refresh", SyncFormat.PATH_REFRESH)
        assertEquals("rbsb_watch", SyncFormat.CAPABILITY_WATCH)
        assertEquals("rbsb_phone", SyncFormat.CAPABILITY_PHONE)
        assertEquals("rbsb_watch_v1", SyncFormat.protocolCapability(SyncFormat.CAPABILITY_WATCH))
        assertEquals("rbsb_phone_v1", SyncFormat.protocolCapability(SyncFormat.CAPABILITY_PHONE))
        for (p in listOf(SyncFormat.PATH_SNAPSHOT, SyncFormat.PATH_SETTINGS, SyncFormat.PATH_REFRESH)) assertTrue(SyncFormat.isRbsbPath(p))
        assertFalse(SyncFormat.isRbsbPath("/other/app"))
        assertFalse(SyncFormat.isRbsbPath(null))
    }

    @Test fun everySettingsCombinationRoundTrips() {
        var n = 0
        for (t in TemperatureUnit.values()) for (w in WindUnit.values()) for (p in PrecipitationUnit.values())
            for (c in ClockFormat.values()) for (a in listOf(true, false)) for (m in MinWarningLevel.values()) {
                val s = SyncSettings(SampleFiles.now, t, w, p, c, a, m)
                assertEquals(Decoded.Ok(s), SettingsCodec.decode(SettingsCodec.encode(s), SampleFiles.now))
                n++
            }
        assertEquals(2 * 4 * 2 * 2 * 2 * 4, n)
    }

    @Test fun minWarningLevelUsesThePhoneAppsNames() {
        assertEquals(listOf("all", "watch", "orange", "red"), MinWarningLevel.values().map { it.wire })
        assertEquals(listOf("red", "orange", "yellow", "watch", "minor", "limit"), WarningLevel.values().map { it.wire })
    }

    @Test fun refreshRoundTripsWithAndWithoutACachedSnapshot() {
        val now = SampleFiles.now
        for (r in listOf(RefreshRequest(now), RefreshRequest(now, now - 3600_000L)))
            assertEquals(Decoded.Ok(r), RefreshCodec.decode(RefreshCodec.encode(r), now))
    }

    @Test fun clipCountsCharactersNotBytesOrCodeUnits() {
        assertEquals("short", SyncText.clip("  short  ", 10))
        assertEquals(10, SyncText.length(SyncText.clip("a".repeat(50), 10)))
        assertTrue(SyncText.clip("a".repeat(50), 10).endsWith("…"))
        val emoji = "🌧️".repeat(30)                                 // characters outside the basic plane
        val clipped = SyncText.clip(emoji, 9)
        assertTrue(SyncText.length(clipped) <= 9)
        assertEquals("no half surrogate pairs", clipped, String(clipped.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertEquals(3, SyncText.length("Tø🌧"))
    }

    @Test fun coordinateRounding() {
        assertEquals(-36.848, Coordinates.round3(-36.84846), 0.0)
        assertEquals(174.763, Coordinates.round3(174.76334), 0.0)
        assertTrue(Coordinates.isRounded3(-36.848))
        assertTrue(Coordinates.isRounded3(0.0))
        assertTrue(Coordinates.isRounded3(90.0))
        assertFalse(Coordinates.isRounded3(-36.8485))
        assertFalse(Coordinates.isRounded3(174.76334))
        for (i in -180_000..180_000 step 997) assertTrue(Coordinates.isRounded3(Coordinates.round3(i / 1000.0 + 0.0004)))
    }
}
