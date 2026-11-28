// The cache: every Stage 1 sample through the real receive path, ordering, restarts and damaged
// files. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.Arrival
import io.github.shanebl030glitch.rbsbweather.sync.PayloadGzip
import io.github.shanebl030glitch.rbsbweather.sync.SettingsCodec
import io.github.shanebl030glitch.rbsbweather.sync.SnapshotCodec
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import io.github.shanebl030glitch.rbsbweather.sync.TemperatureUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WeatherStoreTest {
    private val samplesDir = File(System.getProperty("rbsb.samples") ?: "../sync/samples")
    private fun dir() = Files.createTempDirectory("rbsb-store").toFile().apply { deleteOnExit() }
    private fun gz(f: File) = PayloadGzip.compress(f.readText())
    private val expected = JSONObject(File(samplesDir, "expected.json").readText())

    /** The "now" the Stage 1 expected results were made for. */
    private val now = expected.getLong("now")

    private fun ok(text: String) = (SnapshotCodec.parse(text, null) as io.github.shanebl030glitch.rbsbweather.sync.Decoded.Ok).value

    @Test fun everySampleGivesTheExpectedResultAndRejectionsNeverTouchTheCache() {
        val exp = expected.getJSONObject("snapshot")
        val valid = exp.getJSONArray("valid").let { a -> List(a.length()) { a.getString(it) } }
        val invalid = exp.getJSONObject("invalid")
        val cached = File(samplesDir, "snapshot/valid/clear-day.json")
        for (path in valid) {
            val store = WeatherStore(dir())
            assertEquals(path, Outcome.Saved, store.offerSnapshot(gz(File(samplesDir, path)), now))
            assertEquals(path, ok(File(samplesDir, path).readText()), store.snapshot())
        }
        for (path in invalid.keys()) {
            val store = WeatherStore(dir())
            assertEquals(Outcome.Saved, store.offerSnapshot(gz(cached), now))         // a good cached copy first
            val outcome = store.offerSnapshot(gz(File(samplesDir, path)), now)
            assertTrue("$path: $outcome", outcome is Outcome.Rejected)
            assertEquals(path, invalid.getString(path), (outcome as Outcome.Rejected).reason)
            assertEquals(path, ok(cached.readText()), store.snapshot())             // the cached copy is kept
        }
        assertEquals(18, valid.size); assertEquals(44, invalid.length())
    }

    @Test fun newerReplacesOlderAndRepeatsAreKept() {
        val d = dir(); val store = WeatherStore(d)
        val base = SnapshotCodec.parse(File(samplesDir, "snapshot/valid/clear-day.json").readText(), null)
            .let { (it as io.github.shanebl030glitch.rbsbweather.sync.Decoded.Ok).value }
        val newer = base.copy(fetchedAt = base.fetchedAt + 600_000, sentAt = base.sentAt + 600_000, sequence = base.sequence + 1)
        assertEquals(Outcome.Saved, store.offerSnapshot(SnapshotCodec.encode(base), now))
        assertEquals(Outcome.Kept(Arrival.DUPLICATE), store.offerSnapshot(SnapshotCodec.encode(base), now))
        assertEquals(Outcome.Saved, store.offerSnapshot(SnapshotCodec.encode(newer), now))
        assertEquals(Outcome.Kept(Arrival.OLDER), store.offerSnapshot(SnapshotCodec.encode(base), now))
        assertEquals(newer, store.snapshot())
        assertEquals(newer, WeatherStore(d).snapshot())                 // after a restart
        assertTrue(File(d, WeatherStore.SNAPSHOT_FILE + ".tmp").exists().not())
    }

    @Test fun damagedFilesAreDiscardedNotCrashedOn() {
        for (bad in listOf(ByteArray(0), "not gzip".toByteArray(), PayloadGzip.compress("{\"kind\":\"rbsb.snapshot\"}"), ByteArray(200_000) { 1 })) {
            val d = dir()
            File(d, WeatherStore.SNAPSHOT_FILE).writeBytes(bad)
            File(d, WeatherStore.SETTINGS_FILE).writeBytes(bad)
            val store = WeatherStore(d)
            assertNull(store.snapshot()); assertNull(store.settings())
            assertTrue(!File(d, WeatherStore.SNAPSHOT_FILE).exists() || bad.size > 100_000)
        }
    }

    @Test fun aLeftoverTemporaryFileFromACrashIsIgnored() {
        val d = dir()
        File(d, WeatherStore.SNAPSHOT_FILE + ".tmp").writeBytes("half a file".toByteArray())
        val store = WeatherStore(d)
        assertNull(store.snapshot())
        assertEquals(Outcome.Saved, store.offerSnapshot(gz(File(samplesDir, "snapshot/valid/clear-day.json")), now))
        assertTrue(WeatherStore(d).snapshot() != null)
    }

    @Test fun settingsReplaceOnlyWhenNotOlder() {
        val d = dir(); val store = WeatherStore(d)
        val a = SyncSettings(sentAt = 1_790_000_000_000L)
        val b = a.copy(sentAt = a.sentAt + 1000, temperatureUnit = TemperatureUnit.F)
        assertEquals(Outcome.Saved, store.offerSettings(SettingsCodec.encode(a), now))
        assertEquals(Outcome.Saved, store.offerSettings(SettingsCodec.encode(b), now))
        assertEquals(Outcome.Kept(Arrival.OLDER), store.offerSettings(SettingsCodec.encode(a), now))
        assertEquals(Outcome.Kept(Arrival.DUPLICATE), store.offerSettings(SettingsCodec.encode(b), now))
        assertEquals(TemperatureUnit.F, WeatherStore(d).settings()!!.temperatureUnit)
    }

    @Test fun futureDatedPayloadsAreRejected() {
        val store = WeatherStore(dir())
        val f = File(samplesDir, "snapshot/valid/clear-day.json")
        val sent = JSONObject(f.readText()).getLong("sentAt")
        val outcome = store.offerSnapshot(gz(f), sent - 11 * 60_000L)    // watch clock 11 minutes behind the phone
        assertEquals("FUTURE_DATED", (outcome as Outcome.Rejected).reason)
    }
}
