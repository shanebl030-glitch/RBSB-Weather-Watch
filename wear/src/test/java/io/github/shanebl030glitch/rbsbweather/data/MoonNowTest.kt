// The live Moon (Stage 3 decision D10): the same calculation as Desktop and Android for the moment
// shown, the phone's values as cross-check and fallback. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.MoonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MoonNowTest {
    private val t = Instant.parse("2026-11-27T21:00:00Z")
    private fun phoneValues(at: Instant): MoonState {
        val p = Moon.phase(at)      // what the phone's identical Moon.kt sends
        return MoonState(at.toEpochMilli(), p.elongation, p.name, p.illumination, p.brightLimb, 0.0)
    }

    @Test fun liveForTheMomentShownEvenWhenTheSnapshotIsOld() {
        val fetched = t.minusSeconds(12 * 3600)
        val m = MoonNow.phase(t, phoneValues(fetched))!!
        val live = Moon.phase(t)
        assertTrue(m.live)
        assertEquals(live.phaseAngle, m.phaseAngle, 0.0)
        assertEquals(live.illumination, m.illumination, 0.0)
        assertEquals(live.brightLimb, m.brightLimb, 0.0)
        // and it differs from the 12-hour-old snapshot value, which is why D10 calculates live
        assertTrue(kotlin.math.abs(m.elongation - phoneValues(fetched).phase) > 3.0)
    }

    @Test fun snapshotValuesAreTheFallback() {
        val snap = phoneValues(t)
        val m = MoonNow.phase(t, snap) { error("calculation failed") }!!
        assertFalse(m.live)
        assertEquals(snap.illumination, m.illumination, 0.0)
        assertEquals(MoonNow.phaseAngleFromIllumination(snap.illumination), m.phaseAngle, 1e-9)
        assertEquals(snap.brightLimb, m.brightLimb, 0.0)
        assertNull(MoonNow.phase(t, null) { error("calculation failed") })
    }

    @Test fun nonFiniteResultsAreNotUsed() {
        val bad = Moon.phase(t).copy(illumination = Double.NaN)
        assertFalse(MoonNow.phase(t, phoneValues(t)) { bad }!!.live)
    }

    @Test fun crossCheckAgreesWithTheSameCalculation() {
        assertNull(MoonNow.crossCheck(phoneValues(t)))
    }

    @Test fun crossCheckReportsADifference() {
        val wrong = phoneValues(t).let { it.copy(illumination = (it.illumination + 0.2).coerceAtMost(1.0), brightLimb = it.brightLimb + 40) }
        val msg = MoonNow.crossCheck(wrong)
        assertNotNull(msg)
        assertTrue(msg!!, msg.contains("illumination") && msg.contains("bright limb"))
    }

    @Test fun riseAndSetLive() {
        val rs = MoonNow.riseSet(t, -36.848, 174.763, ZoneId.of("Pacific/Auckland"), null)
        assertTrue(rs.live)
        assertTrue(rs.rise != null || rs.set != null || rs.alwaysUp || rs.alwaysDown)
    }
}
