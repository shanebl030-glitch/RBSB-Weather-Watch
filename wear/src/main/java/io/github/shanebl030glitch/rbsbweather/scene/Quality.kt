// Automatic quality adjustment for the live sky (plan v1.1 section 4; approved Stage 2 decision 3).
// The sky adjusts its DETAIL, never its features: every effect runs at every level. The level is
// chosen from frame times measured on the watch (never by chip or model), then re-checked while it
// runs. Plain Kotlin, so the rules are unit-tested. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import kotlin.math.ceil
import kotlin.math.max

/**
 * The three detail levels. Multipliers apply to the counts the phone app's engine uses (HIGH is the
 * phone's own detail). Minimum counts keep every effect visible at every level.
 */
enum class Quality(
    val label: String,
    /** Rain drops, snowflakes and stars. */
    val particles: Double,
    /** Cloud sprites per layer (and in the low deck). */
    val clouds: Double,
    /** Cloud sprite resolution (drawn at full size; LIGHT makes them at half resolution). */
    val spriteScale: Double,
    /** At most this many rain splashes at once. */
    val maxSplashes: Int,
) {
    HIGH("High", 1.0, 1.0, 1.0, 60),
    STANDARD("Standard", 0.65, 0.75, 1.0, 36),
    LIGHT("Light", 0.4, 0.6, 0.5, 20);

    fun lower(): Quality = values()[minOf(ordinal + 1, values().size - 1)]
    fun higher(): Quality = values()[maxOf(ordinal - 1, 0)]

    companion object {
        /** Fewest drops, flakes and stars at any level, so rain, snow and stars never vanish. */
        const val MIN_PARTICLES = 12
    }
}

/**
 * The adjustable thresholds (approved Stage 2 decision 3: the proposed starting values, to be
 * reviewed and tuned with real-watch measurements at Stage 7).
 */
data class QualityConfig(
    /** Frames per second the sky aims for. */
    val targetFps: Int = 30,
    /** In the watch's power-saving mode: at most this many frames per second. */
    val powerSaveFps: Int = 20,
    /** A frame is slow when its work takes longer than this (one frame at 30 fps)... */
    val slowFrameMs: Double = 33.0,
    /** ...or when it arrives this much later than planned (a missed frame slot). */
    val lateFrameFactor: Double = 1.5,
    /** Step down when more than this share of a window's frames are slow. */
    val slowShareToStepDown: Double = 0.10,
    /** The first measurement, from when the sky starts. */
    val firstWindowS: Double = 3.0,
    /** Then re-checked over windows of this length. */
    val windowS: Double = 10.0,
    /** Step up only after this long in a row with the 90th-percentile frame work under [fastFrameMs]. */
    val stepUpAfterS: Double = 30.0,
    val fastFrameMs: Double = 20.0,
)

/** One measurement window: what was measured and what was decided. Logged and kept for Stage 7. */
data class WindowStats(
    val startedAtMs: Long,
    val seconds: Double,
    val frames: Int,
    val fps: Double,
    val workP50Ms: Double,
    val workP90Ms: Double,
    val workMaxMs: Double,
    val slowShare: Double,
    val quality: Quality,
    val powerSave: Boolean,
    val decision: String,
    val next: Quality,
) {
    fun csv(): String = String.format(java.util.Locale.US, "%d,%.1f,%d,%.1f,%.2f,%.2f,%.2f,%.3f,%s,%b,%s,%s",
        startedAtMs, seconds, frames, fps, workP50Ms, workP90Ms, workMaxMs, slowShare, quality.label, powerSave, decision, next.label)

    fun summary(): String = String.format(java.util.Locale.US, "%s · %.1f fps · work p90 %.1f ms · slow %.0f%%%s",
        quality.label, fps, workP90Ms, slowShare * 100, if (powerSave) " · power saving" else "")

    companion object {
        const val CSV_HEADER = "startedAtMs,seconds,frames,fps,workP50Ms,workP90Ms,workMaxMs,slowShare,quality,powerSave,decision,next"
    }
}

/**
 * Feeds on each animation frame and decides the quality level.
 *  - The first window is [QualityConfig.firstWindowS] long, then every [QualityConfig.windowS].
 *  - Step down one level when more than [QualityConfig.slowShareToStepDown] of a window's frames are
 *    slow (work over [QualityConfig.slowFrameMs], or arriving over [QualityConfig.lateFrameFactor]
 *    times later than planned).
 *  - Step up one level only after [QualityConfig.stepUpAfterS] in a row of windows whose
 *    90th-percentile work is under [QualityConfig.fastFrameMs] and with no step down; after a step
 *    down that wait starts again, so the level does not flip back and forth.
 *  - Power-saving mode: one level below the measured level and at most [QualityConfig.powerSaveFps].
 */
class QualityController(val config: QualityConfig = QualityConfig(), start: Quality = Quality.HIGH) {
    /** The level chosen from measurements (before any power-saving adjustment). */
    var measured: Quality = start; private set
    var powerSave: Boolean = false
    /** The level to draw at now. */
    val quality: Quality get() = if (powerSave) measured.lower() else measured
    val fps: Int get() = if (powerSave) minOf(config.powerSaveFps, config.targetFps) else config.targetFps
    /** Seconds between frames the loop should aim for. */
    val frameInterval: Double get() = 1.0 / fps

    private val works = ArrayList<Double>(512)
    private var slow = 0
    private var windowStartMs = -1L
    private var elapsed = 0.0
    private var fastFor = 0.0
    private var first = true
    val history = ArrayDeque<WindowStats>()

    /**
     * One frame: [intervalS] since the previous frame, [workMs] spent moving and drawing it,
     * [nowMs] the wall clock. Returns the finished window when one ends, else null.
     */
    fun onFrame(intervalS: Double, workMs: Double, nowMs: Long): WindowStats? {
        if (windowStartMs < 0) windowStartMs = nowMs
        val late = intervalS > frameInterval * config.lateFrameFactor
        if (workMs > config.slowFrameMs || late) slow++
        works += workMs
        elapsed += intervalS
        val length = if (first) config.firstWindowS else config.windowS
        if (elapsed < length) return null
        return endWindow(nowMs)
    }

    private fun endWindow(nowMs: Long): WindowStats {
        val sorted = works.sorted()
        fun pct(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[max(0, ceil(p * sorted.size).toInt() - 1)]
        val frames = works.size
        val share = if (frames == 0) 0.0 else slow.toDouble() / frames
        val p90 = pct(0.90)
        val before = quality
        var decision = "keep"
        if (share > config.slowShareToStepDown && measured != Quality.LIGHT) {
            measured = measured.lower(); fastFor = 0.0; decision = "step down"
        } else if (share > config.slowShareToStepDown) {
            fastFor = 0.0; decision = "keep (already lowest)"
        } else if (p90 < config.fastFrameMs) {
            fastFor += elapsed
            if (fastFor >= config.stepUpAfterS && measured != Quality.HIGH) {
                measured = measured.higher(); fastFor = 0.0; decision = "step up"
            }
        } else fastFor = 0.0
        val stats = WindowStats(windowStartMs, elapsed, frames, if (elapsed > 0) frames / elapsed else 0.0,
            pct(0.5), p90, sorted.lastOrNull() ?: 0.0, share, before, powerSave, decision, quality)
        history.addLast(stats)
        while (history.size > 100) history.removeFirst()
        works.clear(); slow = 0; elapsed = 0.0; windowStartMs = nowMs; first = false
        return stats
    }

    /** Forget the current window (e.g. after the sky was paused), keeping the chosen level. */
    fun resetWindow() {
        works.clear(); slow = 0; elapsed = 0.0; windowStartMs = -1L
    }
}
