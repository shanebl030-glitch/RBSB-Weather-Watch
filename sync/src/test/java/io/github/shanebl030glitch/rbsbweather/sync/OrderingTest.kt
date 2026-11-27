// Which snapshot wins (Stage 1 decision 4): fetchedAt first, sequence as the tie-breaker, phone
// reinstalls handled, and the future-date tolerance.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderingTest {
    private val now = SampleFiles.now
    private val cached = SampleFiles.snapshot()                       // fetchedAt F, sequence 12, sourceId S
    private val minute = 60_000L

    @Test fun nothingCachedMeansNewer() = assertEquals(Arrival.NEWER, Ordering.compare(cached, null))

    @Test fun laterFetchedAtWinsWhateverTheSequence() {
        val later = cached.copy(fetchedAt = cached.fetchedAt + minute, sentAt = cached.sentAt + minute, sequence = 3)
        assertEquals(Arrival.NEWER, Ordering.compare(later, cached))
        assertEquals(Arrival.OLDER, Ordering.compare(cached, later))
    }

    @Test fun earlierFetchedAtLosesEvenWithAHigherSequence() {
        val earlier = cached.copy(fetchedAt = cached.fetchedAt - minute, sequence = 99)
        assertEquals(Arrival.OLDER, Ordering.compare(earlier, cached))
    }

    @Test fun sameFetchedAtUsesTheSequence() {
        assertEquals(Arrival.NEWER, Ordering.compare(cached.copy(sequence = 13), cached))   // e.g. warnings changed
        assertEquals(Arrival.DUPLICATE, Ordering.compare(cached.copy(), cached))
        assertEquals(Arrival.OLDER, Ordering.compare(cached.copy(sequence = 11), cached))
        assertTrue(Ordering.shouldReplace(cached.copy(sequence = 13), cached))
        assertTrue(!Ordering.shouldReplace(cached.copy(), cached))
    }

    @Test fun reinstalledPhoneWithFreshWeatherIsAcceptedDespiteSequenceRestartingAtOne() {
        val reinstalled = cached.copy(sourceId = "a0b1c2d3-new-install", sequence = 1,
            fetchedAt = cached.fetchedAt + 2 * minute, sentAt = cached.sentAt + 2 * minute)
        assertEquals(Arrival.NEWER, Ordering.compare(reinstalled, cached))
        // and its next snapshots keep winning
        val next = reinstalled.copy(sequence = 2, fetchedAt = reinstalled.fetchedAt + minute, sentAt = reinstalled.sentAt + minute)
        assertEquals(Arrival.NEWER, Ordering.compare(next, reinstalled))
    }

    @Test fun reinstalledPhoneResendingTheSameWeatherIsAccepted() {
        // Same fetchedAt (the phone's cached forecast), new install id, sequence back to 1.
        val resend = cached.copy(sourceId = "a0b1c2d3-new-install", sequence = 1)
        assertEquals(Arrival.NEWER, Ordering.compare(resend, cached))
    }

    @Test fun reinstalledPhoneWithOlderWeatherIsIgnored() {
        val older = cached.copy(sourceId = "a0b1c2d3-new-install", sequence = 1, fetchedAt = cached.fetchedAt - minute)
        assertEquals(Arrival.OLDER, Ordering.compare(older, cached))
    }

    @Test fun futureToleranceIsExactlyTenMinutes() {
        assertEquals(10 * minute, Limits.FUTURE_TOLERANCE_MS)
        val atLimit = cached.copy(fetchedAt = now + Limits.FUTURE_TOLERANCE_MS, sentAt = now + Limits.FUTURE_TOLERANCE_MS)
        assertTrue("exactly at the tolerance is accepted", SnapshotCodec.decode(SnapshotCodec.encode(atLimit), now) is Decoded.Ok)
        val over = atLimit.copy(fetchedAt = now + Limits.FUTURE_TOLERANCE_MS + 1, sentAt = now + Limits.FUTURE_TOLERANCE_MS + 1)
        val d = SnapshotCodec.decode(SnapshotCodec.encode(over), now)
        assertEquals(Reason.FUTURE_DATED, (d as Decoded.Rejected).reason)
        assertEquals("fetchedAt", d.path)
    }

    @Test fun aFutureSentAtAloneIsRejected() {
        val s = cached.copy(sentAt = now + Limits.FUTURE_TOLERANCE_MS + 1)
        val d = SnapshotCodec.decode(SnapshotCodec.encode(s), now)
        assertEquals(Reason.FUTURE_DATED, (d as Decoded.Rejected).reason)
        assertEquals("sentAt", d.path)
    }

    @Test fun aFutureDatedSnapshotCannotBlockLaterOnes() {
        // It is rejected on arrival, so it never becomes the cached copy that later snapshots are compared with.
        val bad = cached.copy(fetchedAt = now + 6 * 3600_000L, sentAt = now + 6 * 3600_000L)
        assertTrue(SnapshotCodec.decode(SnapshotCodec.encode(bad), now) is Decoded.Rejected)
        val nextReal = cached.copy(fetchedAt = cached.fetchedAt + 30 * minute, sentAt = cached.sentAt + 30 * minute)
        assertEquals(Arrival.NEWER, Ordering.compare(nextReal, cached))
    }

    @Test fun oldSnapshotsAreNotRejectedByAgeOnlyByOrdering() {
        // A day-old snapshot still decodes (the watch shows it as stale, Stage 3); ordering decides if it is used.
        val old = cached.copy(fetchedAt = now - 24 * 3600_000L, sentAt = now - 24 * 3600_000L)
        assertTrue(SnapshotCodec.decode(SnapshotCodec.encode(old), now) is Decoded.Ok)
    }

    @Test fun settingsAndRefreshUseTheSameTolerance() {
        val st = SyncSettings(sentAt = now + Limits.FUTURE_TOLERANCE_MS)
        assertTrue(SettingsCodec.decode(SettingsCodec.encode(st), now) is Decoded.Ok)
        val st2 = st.copy(sentAt = now + Limits.FUTURE_TOLERANCE_MS + 1)
        assertEquals(Reason.FUTURE_DATED, (SettingsCodec.decode(SettingsCodec.encode(st2), now) as Decoded.Rejected).reason)
        val r = RefreshRequest(sentAt = now + Limits.FUTURE_TOLERANCE_MS + 1)
        assertEquals(Reason.FUTURE_DATED, (RefreshCodec.decode(RefreshCodec.encode(r), now) as Decoded.Rejected).reason)
    }
}
