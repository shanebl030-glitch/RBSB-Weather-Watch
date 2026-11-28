// The Moon for the moment shown (Stage 3 decision D10, approved 28 Nov 2026): phase, illumination,
// bright limb, position and tilt are calculated live on the watch with Moon.kt, the same calculation
// RBSB-Weather Desktop and Android use, so the watch Moon matches them however old the weather
// data is. The phone's snapshot values are a cross-check (a difference beyond a small tolerance is
// logged) and the fallback if the calculation fails. Plain Kotlin, unit tested.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.MoonState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos

/** The Moon's phase values used for drawing and for the Moon screen. */
data class MoonPhaseNow(
    /** Degrees, 0 = full, 180 = new (what MoonRender lights the picture with). */
    val phaseAngle: Double,
    /** Lit fraction 0..1. */
    val illumination: Double,
    /** Elongation 0..360 (0 new, 90 first quarter, 180 full, 270 last quarter). */
    val elongation: Double,
    /** Position angle of the bright limb, degrees from north through east. */
    val brightLimb: Double,
    val name: String,
    /** True when calculated live; false when the phone's snapshot values were used (fallback). */
    val live: Boolean,
)

object MoonNow {
    /** The phone and the watch run the same code, so their values should agree almost exactly. */
    const val ILLUMINATION_TOLERANCE = 0.01
    const val ANGLE_TOLERANCE_DEG = 1.5

    fun phaseAngleFromIllumination(illumination: Double): Double =
        Math.toDegrees(acos((2 * illumination.coerceIn(0.0, 1.0) - 1).coerceIn(-1.0, 1.0)))

    private fun angleDiff(a: Double, b: Double) = abs(((a - b) % 360 + 540) % 360 - 180)

    /** The phase at [t], live; the snapshot's values if the calculation fails; null if neither is available. */
    fun phase(t: Instant, snapshot: MoonState?, calc: (Instant) -> Moon.Phase = Moon::phase): MoonPhaseNow? {
        try {
            val p = calc(t)
            if (listOf(p.phaseAngle, p.illumination, p.elongation, p.brightLimb).all { it.isFinite() }) {
                return MoonPhaseNow(p.phaseAngle, p.illumination, p.elongation, p.brightLimb, p.name, live = true)
            }
        } catch (e: Exception) { }
        val m = snapshot ?: return null
        return MoonPhaseNow(phaseAngleFromIllumination(m.illumination), m.illumination, m.phase, m.brightLimb,
            m.phaseName.ifBlank { Moon.phaseName(m.phase) }, live = false)
    }

    /**
     * Checks the phone's values against the watch's own calculation at the moment the phone
     * calculated them. Returns null when they agree, or a short description of the difference
     * (logged; the live values are still used).
     */
    fun crossCheck(snapshot: MoonState, calc: (Instant) -> Moon.Phase = Moon::phase): String? {
        val p = try { calc(Instant.ofEpochMilli(snapshot.at)) } catch (e: Exception) { return "watch calculation failed: ${e.javaClass.simpleName}" }
        val problems = buildList {
            if (abs(p.illumination - snapshot.illumination) > ILLUMINATION_TOLERANCE)
                add(String.format(java.util.Locale.US, "illumination %.3f vs phone %.3f", p.illumination, snapshot.illumination))
            if (angleDiff(p.elongation, snapshot.phase) > ANGLE_TOLERANCE_DEG)
                add(String.format(java.util.Locale.US, "phase %.1f° vs phone %.1f°", p.elongation, snapshot.phase))
            if (angleDiff(p.brightLimb, snapshot.brightLimb) > ANGLE_TOLERANCE_DEG)
                add(String.format(java.util.Locale.US, "bright limb %.1f° vs phone %.1f°", p.brightLimb, snapshot.brightLimb))
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /** Moonrise and moonset for the place's date of [t] (local times), live; the snapshot's as fallback. */
    data class RiseSet(val rise: Instant?, val set: Instant?, val alwaysUp: Boolean, val alwaysDown: Boolean, val live: Boolean)

    fun riseSet(t: Instant, lat: Double, lon: Double, zone: ZoneId, snapshot: MoonState?): RiseSet {
        try {
            val day = Moon.moonDays(lat, lon, zone, LocalDate.ofInstant(t, zone), 1).first()
            return RiseSet(day.rise?.toInstant(), day.set?.toInstant(), day.alwaysUp, day.alwaysDown, live = true)
        } catch (e: Exception) { }
        val m = snapshot ?: return RiseSet(null, null, false, false, live = false)
        return RiseSet(m.rise?.let(Instant::ofEpochMilli), m.set?.let(Instant::ofEpochMilli), m.alwaysUp, m.alwaysDown, live = false)
    }
}
