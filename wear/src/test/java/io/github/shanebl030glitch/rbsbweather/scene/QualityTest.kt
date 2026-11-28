// Automatic quality adjustment: the starting thresholds (approved Stage 2 decision 3) and the rule
// that detail changes but effects never disappear. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityTest {
    private val frame = 1.0 / 30

    /** Feeds [seconds] of 30 fps frames with the given work, returning the windows that ended. */
    private fun run(q: QualityController, seconds: Double, workMs: (Int) -> Double, interval: Double = frame): List<WindowStats> {
        val out = mutableListOf<WindowStats>()
        var t = 0L
        repeat((seconds / interval).toInt()) { i -> t += (interval * 1000).toLong(); q.onFrame(interval, workMs(i), t)?.let { out += it } }
        return out
    }

    @Test fun startingThresholdsAreTheApprovedOnes() {
        val c = QualityConfig()
        assertEquals(30, c.targetFps); assertEquals(20, c.powerSaveFps)
        assertEquals(33.0, c.slowFrameMs, 0.0); assertEquals(0.10, c.slowShareToStepDown, 0.0)
        assertEquals(3.0, c.firstWindowS, 0.0); assertEquals(10.0, c.windowS, 0.0)
        assertEquals(30.0, c.stepUpAfterS, 0.0); assertEquals(20.0, c.fastFrameMs, 0.0)
    }

    @Test fun firstWindowIsThreeSecondsThenTen() {
        val q = QualityController()
        val w = run(q, 23.1, { 5.0 })
        assertEquals(listOf(3.0, 10.0, 10.0), w.map { Math.round(it.seconds).toDouble() })
        assertEquals(30.0, w[0].fps, 1.0)
    }

    @Test fun stepsDownWhenMoreThanTenPercentOfFramesAreSlow() {
        val q = QualityController()
        val w = run(q, 3.05, { i -> if (i % 5 == 0) 40.0 else 10.0 })            // 20% slow
        assertEquals("step down", w.single().decision)
        assertEquals(Quality.STANDARD, q.quality)
        run(q, 10.05, { i -> if (i % 5 == 0) 40.0 else 10.0 })
        assertEquals(Quality.LIGHT, q.quality)
        val last = run(q, 10.05, { 40.0 }).single()
        assertEquals("keep (already lowest)", last.decision)
        assertEquals(Quality.LIGHT, q.quality)
    }

    @Test fun tenPercentOrFewerSlowFramesKeepsTheLevel() {
        val q = QualityController()
        run(q, 3.05, { i -> if (i % 12 == 0) 40.0 else 25.0 })                    // about 8% slow
        assertEquals(Quality.HIGH, q.quality)
    }

    @Test fun lateFramesCountAsSlow() {
        val q = QualityController()
        val w = run(q, 3.1, { 5.0 }, interval = 0.06)                              // 60 ms apart: missed slots
        assertEquals("step down", w.single().decision)
    }

    @Test fun stepsUpOnlyAfterThirtySecondsOfFastFrames() {
        val q = QualityController(start = Quality.LIGHT)
        val w = run(q, 3.05 + 30.1, { 8.0 })
        assertEquals(listOf("keep", "keep", "keep", "step up"), w.map { it.decision })
        assertEquals(Quality.STANDARD, q.quality)
    }

    @Test fun doesNotFlipBackAndForth() {
        val q = QualityController()
        run(q, 3.05, { 40.0 })                                                    // down to STANDARD
        assertEquals(Quality.STANDARD, q.quality)
        run(q, 20.1, { 8.0 })                                                     // 20 s of fast frames: not enough
        assertEquals(Quality.STANDARD, q.quality)
        run(q, 10.05, { 8.0 })                                                    // 30 s: up again
        assertEquals(Quality.HIGH, q.quality)
    }

    @Test fun mediumFramesResetTheWaitToStepUp() {
        val q = QualityController(start = Quality.STANDARD)
        run(q, 3.05 + 20.1, { 8.0 })
        run(q, 10.05, { 25.0 })                                                   // not fast, not slow
        run(q, 20.1, { 8.0 })
        assertEquals(Quality.STANDARD, q.quality)
    }

    @Test fun powerSavingDropsOneLevelAndCapsTheFrameRate() {
        val q = QualityController()
        q.powerSave = true
        assertEquals(Quality.STANDARD, q.quality); assertEquals(20, q.fps)
        assertEquals(0.05, q.frameInterval, 1e-9)
        q.powerSave = false
        assertEquals(Quality.HIGH, q.quality); assertEquals(30, q.fps)
        val light = QualityController(start = Quality.LIGHT).apply { powerSave = true }
        assertEquals(Quality.LIGHT, light.quality)                                // never below LIGHT
    }

    @Test fun windowsAreRecordedWithTheirMeasurements() {
        val q = QualityController()
        val w = run(q, 3.05, { i -> 4.0 + (i % 10) })
        val s = w.single()
        assertTrue(s.frames in 90..91)
        assertTrue(s.workP50Ms in 7.0..10.0 && s.workP90Ms >= s.workP50Ms && s.workMaxMs == 13.0)
        assertEquals(WindowStats.CSV_HEADER.split(',').size, s.csv().split(',').size)
        assertTrue(s.summary().startsWith("High · 30.0 fps"))
        assertEquals(1, q.history.size)
    }

    @Test fun everyEffectStaysAtEveryLevelAndDetailFalls() {
        val kinds = listOf(Kind.RAIN to 0.3, Kind.DRIZZLE to 0.3, Kind.SNOW to 0.3, Kind.FOG to 0.7, Kind.THUNDER to 0.7, Kind.PARTLY to 0.5)
        for ((kind, i) in kinds) {
            val counts = Quality.values().map { q ->
                val e = SceneEngine(seed = 5).apply { quality = q; setSize(432, 432); setConditions(Conditions(kind, i, daylight = 0.0)) }
                assertTrue("$kind $q clouds", e.clouds.isNotEmpty())
                assertTrue("$kind $q stars", e.stars.size >= Quality.MIN_PARTICLES)
                if (kind in listOf(Kind.RAIN, Kind.DRIZZLE, Kind.THUNDER)) assertTrue("$kind $q drops", e.weather.drops.size >= Quality.MIN_PARTICLES)
                if (kind == Kind.SNOW) assertTrue("$kind $q flakes", e.weather.flakes.size >= Quality.MIN_PARTICLES)
                if (kind == Kind.FOG) assertEquals("$kind $q fog", 5, e.weather.fog.size)
                if (kind == Kind.THUNDER) {
                    var flashes = 0
                    repeat(900) { val b = e.flash; e.advance(1 / 30.0); if (e.flash > b) flashes++ }
                    assertTrue("$q lightning", flashes >= 2)
                }
                e.clouds.size + e.weather.drops.size + e.weather.flakes.size
            }
            assertTrue("$kind detail falls: $counts", counts[0] > counts[2])
        }
    }

    @Test fun highIsThePhonesDetail() {
        // At HIGH the watch makes the same scene as the phone app's engine (no multipliers).
        assertEquals(1.0, Quality.HIGH.particles, 0.0); assertEquals(1.0, Quality.HIGH.clouds, 0.0)
        assertEquals(1.0, Quality.HIGH.spriteScale, 0.0); assertEquals(60, Quality.HIGH.maxSplashes)
        assertEquals(0.5, Quality.LIGHT.spriteScale, 0.0)
    }

    @Test fun changingTheLevelRebuildsTheScene() {
        val e = SceneEngine(seed = 5).apply { setSize(480, 480); setConditions(Conditions(Kind.RAIN, 1.0)) }
        val high = e.weather.drops.size
        e.quality = Quality.LIGHT
        assertNotNull(e.weather)
        assertTrue(e.weather.drops.size < high)
        assertEquals(Math.round(high * 0.4).toInt(), e.weather.drops.size)
    }
}
