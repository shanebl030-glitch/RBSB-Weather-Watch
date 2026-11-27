// Size limits and hostile or broken payloads: always rejected with a reason, never a crash.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

class PayloadSafetyTest {
    private val now = SampleFiles.now

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun reasonOf(d: Decoded<*>): Reason = (d as? Decoded.Rejected)?.reason ?: run { fail("accepted: $d"); Reason.UNEXPECTED }

    @Test fun largestSnapshotStaysUnderTheTwentyKilobyteTarget() {
        val bytes = SnapshotCodec.encode(SampleFiles.snapshot("largest"))
        println("SIZE largest snapshot: ${bytes.size} bytes compressed (target ${Limits.TARGET_COMPRESSED_BYTES}, Data Layer limit about ${Limits.MAX_COMPRESSED_BYTES})")
        assertTrue("largest snapshot ${bytes.size} bytes", bytes.size < Limits.TARGET_COMPRESSED_BYTES)
        val typical = SnapshotCodec.encode(SampleFiles.snapshot("rain-night-warnings"))
        println("SIZE typical snapshot (48 h, 7 days, 3 warnings): ${typical.size} bytes compressed")
        assertTrue(typical.size < Limits.TARGET_COMPRESSED_BYTES)
    }

    @Test fun overTheCompressedLimitIsRejected() {
        val big = Random(1).nextBytes(Limits.MAX_COMPRESSED_BYTES + 1)
        assertEquals(Reason.TOO_LARGE, reasonOf(SnapshotCodec.decode(big, now)))
    }

    @Test fun zipBombIsStoppedAtTheDecompressedLimit() {
        val bomb = gzip(ByteArray(5_000_000) { ' '.code.toByte() })
        assertTrue("the bomb itself is small: ${bomb.size}", bomb.size < Limits.MAX_COMPRESSED_BYTES)
        assertEquals(Reason.TOO_LARGE, reasonOf(SnapshotCodec.decode(bomb, now)))
    }

    @Test fun brokenBytesAreCorruptNotACrash() {
        assertEquals(Reason.CORRUPT, reasonOf(SnapshotCodec.decode(ByteArray(0), now)))
        assertEquals(Reason.CORRUPT, reasonOf(SnapshotCodec.decode("not gzip at all".toByteArray(), now)))
        val good = SnapshotCodec.encode(SampleFiles.snapshot())
        assertEquals(Reason.CORRUPT, reasonOf(SnapshotCodec.decode(good.copyOf(good.size / 2), now)))
        val badUtf8 = gzip(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte()))
        assertEquals(Reason.CORRUPT, reasonOf(SnapshotCodec.decode(badUtf8, now)))
        repeat(200) { i ->                                   // random garbage, with and without a gzip header
            val junk = Random(i).nextBytes(Random(i + 1000).nextInt(1, 4000))
            if (i % 2 == 0) { junk[0] = 0x1f; junk[1] = 0x8b.toByte() }
            assertTrue(SnapshotCodec.decode(junk, now) is Decoded.Rejected)
        }
    }

    @Test fun notOneJsonObjectIsBadJson() {
        for (text in listOf("", "   ", "[]", "null", "42", "\"text\"", "{", "{} {}", "{\"kind\":\"rbsb.snapshot\"} x"))
            assertEquals("'$text'", Reason.BAD_JSON, reasonOf(SnapshotCodec.parse(text, now)))
    }

    @Test fun absurdNestingIsRejectedNotAStackOverflowCrash() {
        val deep = "{\"kind\":" + "[".repeat(200_000) + "]".repeat(200_000) + "}"
        assertTrue(deep.length < Limits.MAX_JSON_BYTES * 2)
        val d = SnapshotCodec.parse(deep, now)
        assertTrue("rejected: $d", d is Decoded.Rejected)
    }

    @Test fun wrongPayloadKindIsRejectedByEachCodec() {
        val snapshotText = SnapshotCodec.toJsonText(SampleFiles.snapshot())
        assertEquals(Reason.WRONG_KIND, reasonOf(SettingsCodec.parse(snapshotText, now)))
        assertEquals(Reason.WRONG_KIND, reasonOf(RefreshCodec.parse(snapshotText, now)))
        val settingsText = SettingsCodec.toJson(SyncSettings(sentAt = now)).toString()
        assertEquals(Reason.WRONG_KIND, reasonOf(SnapshotCodec.parse(settingsText, now)))
    }

    @Test fun encodeRefusesASnapshotTheWatchWouldReject() {
        val ok = SampleFiles.snapshot()
        val bad = listOf(
            ok.copy(place = ok.place.copy(latitude = -36.8485)),           // not rounded
            ok.copy(sequence = 0),
            ok.copy(sentAt = ok.fetchedAt - 1),
            ok.copy(fetchedAt = 0L, sentAt = 0L),                           // 0.7.2's "unknown" fetchedAt
            ok.copy(provider = ""),
            ok.copy(current = ok.current.copy(temperature = Double.NaN)),
            ok.copy(current = ok.current.copy(humidity = 140.0)),
            ok.copy(hourly = ok.hourly + ok.hourly.last().copy(time = "2026-12-01T00:00")),   // 49 hours
            ok.copy(sourceId = "short"),
        )
        for (s in bad) {
            try {
                SnapshotCodec.encode(s)
                fail("encode accepted a bad snapshot: $s")
            } catch (e: IllegalArgumentException) {
                println("ENCODE REFUSED: ${e.message}")
            }
        }
    }
}
