// Moon astronomy worked out on the phone (no web service, so it works offline): phase,
// illumination, moonrise/moonset, the Moon's place and tilt in the sky. A line-by-line port of
// the desktop app's moon.py: the main periodic terms of Meeus, "Astronomical Algorithms"
// (2nd ed.), ch. 47 (Moon, about 10") and ch. 25 (Sun). Rise/set is where the Moon's centre
// reaches h0 = 0.7275·π − 0.5667° (parallax and refraction), typically within a minute of
// published almanacs (checked against PyEphem in the tests, as on the desktop).
// Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/data/Moon.kt. Watch changes: none (used for the live Moon position and tilt, plan option C).
package io.github.shanebl030glitch.rbsbweather.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

object Moon {
    const val SYNODIC_MONTH = 29.530588853
    private const val DELTA_T_S = 69.2
    const val SYNODIC_RATE = 360.0 / SYNODIC_MONTH
    private const val LUNAR_DAY_RATE = 360.0 / 1.0350501

    private fun r(d: Double) = Math.toRadians(d)
    private fun d(x: Double) = Math.toDegrees(x)
    private fun sn(x: Double) = sin(r(x))
    private fun cs(x: Double) = cos(r(x))
    private fun mod(a: Double, m: Double) = ((a % m) + m) % m

    fun julianDay(t: Instant): Double = 2440587.5 + t.toEpochMilli() / 86_400_000.0
    fun fromJd(jd: Double): Instant = Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86_400_000.0))

    data class Position(val ra: Double, val dec: Double, val lon: Double, val lat: Double, val distKm: Double)

    private fun obliquity(t: Double) = 23.439291 - 0.0130042 * t - 1.64e-7 * t * t

    private fun eclToEq(lon: Double, lat: Double, eps: Double): Pair<Double, Double> {
        val ra = mod(d(atan2(sn(lon) * cs(eps) - tan(r(lat)) * sn(eps), cs(lon))), 360.0)
        val dec = d(asin(sn(lat) * cs(eps) + cs(lat) * sn(eps) * sn(lon)))
        return ra to dec
    }

    fun moonPosition(jdUt: Double): Position {
        val t = (jdUt + DELTA_T_S / 86400 - 2451545.0) / 36525.0
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t + t.pow(3) / 538841 - t.pow(4) / 65194000
        val dd = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t + t.pow(3) / 545868 - t.pow(4) / 113065000
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t.pow(3) / 24490000
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t + t.pow(3) / 69699 - t.pow(4) / 14712000
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t - t.pow(3) / 3526000 + t.pow(4) / 863310000
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        var sl = 0.0; var sr = 0.0; var sb = 0.0
        for (row in LR) {
            val arg = row[0] * dd + row[1] * m + row[2] * mp + row[3] * f
            val ee = e.pow(abs(row[1]))
            sl += row[4] * ee * sn(arg)
            sr += row[5] * ee * cs(arg)
        }
        for (row in BT) sb += row[4] * e.pow(abs(row[1])) * sn(row[0] * dd + row[1] * m + row[2] * mp + row[3] * f)
        sl += 3958 * sn(a1) + 1962 * sn(lp - f) + 318 * sn(a2)
        sb += (-2235 * sn(lp) + 382 * sn(a3) + 175 * sn(a1 - f) + 175 * sn(a1 + f) + 127 * sn(lp - mp) - 115 * sn(lp + mp))
        val om = 125.04452 - 1934.136261 * t
        val dpsi = (-17.20 * sn(om) - 1.32 * sn(2 * (280.4665 + 36000.7698 * t)) - 0.23 * sn(2 * lp) + 0.21 * sn(2 * om)) / 3600
        val lon = mod(lp + sl / 1e6 + dpsi, 360.0)
        val lat = sb / 1e6
        val dist = 385000.56 + sr / 1000
        val eps = obliquity(t) + 9.20 / 3600 * cs(om)
        val (ra, dec) = eclToEq(lon, lat, eps)
        return Position(ra, dec, lon, lat, dist)
    }

    fun sunPosition(jdUt: Double): Position {
        val t = (jdUt + DELTA_T_S / 86400 - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val e = 0.016708634 - 0.000042037 * t
        val c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * sn(m) + (0.019993 - 0.000101 * t) * sn(2 * m) + 0.000289 * sn(3 * m))
        val v = m + c
        val rr = 1.000001018 * (1 - e * e) / (1 + e * cs(v))
        val om = 125.04 - 1934.136 * t
        val lon = mod(l0 + c - 0.00569 - 0.00478 * sn(om), 360.0)
        val eps = obliquity(t) + 0.00256 * cs(om)
        val (ra, dec) = eclToEq(lon, 0.0, eps)
        return Position(ra, dec, lon, 0.0, rr * 149597870.7)
    }

    fun gmst(jdUt: Double): Double {
        val t = (jdUt - 2451545.0) / 36525.0
        return mod(280.46061837 + 360.98564736629 * (jdUt - 2451545.0) + 0.000387933 * t * t - t.pow(3) / 38710000, 360.0)
    }

    /** Geocentric altitude of the Moon's centre, and the rise/set standard altitude h0. */
    fun moonAltitude(jdUt: Double, lat: Double, lon: Double): Pair<Double, Double> {
        val p = moonPosition(jdUt)
        val h = gmst(jdUt) + lon - p.ra
        val alt = d(asin(sn(lat) * sn(p.dec) + cs(lat) * cs(p.dec) * cs(h)))
        val parallax = d(asin(6378.14 / p.distKm))
        return alt to 0.7275 * parallax - 0.5667
    }

    // ---------------------------------------------------------------- phase
    data class Phase(
        val illumination: Double,      // 0..1
        val phaseAngle: Double,        // degrees, 0 = full, 180 = new
        val elongation: Double,        // 0..360 (0 new, 180 full)
        val waxing: Boolean,
        val brightLimb: Double,        // position angle of the bright limb (from north through east)
        val ageDays: Double,
        val distanceKm: Double,
    ) {
        val name: String get() = phaseName(elongation)
    }

    fun phaseName(elong: Double): String {
        val e = mod(elong, 360.0)
        val w = 6.0
        return when {
            e < w || e > 360 - w -> "New Moon"
            abs(e - 90) < w -> "First Quarter"
            abs(e - 180) < w -> "Full Moon"
            abs(e - 270) < w -> "Last Quarter"
            e < 90 -> "Waxing Crescent"
            e < 180 -> "Waxing Gibbous"
            e < 270 -> "Waning Gibbous"
            else -> "Waning Crescent"
        }
    }

    fun phase(t: Instant): Phase {
        val jd = julianDay(t)
        val m = moonPosition(jd); val s = sunPosition(jd)
        val elong = mod(m.lon - s.lon, 360.0)
        val cosPsi = cs(m.lat) * cs(m.lon - s.lon)
        val psi = acos(cosPsi.coerceIn(-1.0, 1.0))
        val i = atan2(s.distKm * sin(psi), m.distKm - s.distKm * cos(psi))
        val k = (1 + cos(i)) / 2
        val chi = mod(d(atan2(cs(s.dec) * sn(s.ra - m.ra), sn(s.dec) * cs(m.dec) - cs(s.dec) * sn(m.dec) * cs(s.ra - m.ra))), 360.0)
        return Phase(k, d(i), elong, elong < 180, chi, elong / 360 * SYNODIC_MONTH, m.distKm)
    }

    /** Moon age as a fraction 0..1 (0 = new, 0.5 = full), accurate to about a day (scene.moon_phase). */
    fun ageFraction(t: Instant): Double {
        val ref = Instant.parse("2000-01-06T18:14:00Z")
        val days = (t.toEpochMilli() - ref.toEpochMilli()) / 86_400_000.0
        return mod(days / SYNODIC_MONTH, 1.0)
    }

    private fun elong(jd: Double) = mod(moonPosition(jd).lon - sunPosition(jd).lon, 360.0)

    /** Next instant when the elongation equals target (0 new, 90 first quarter, 180 full, 270 last quarter). */
    fun nextPhaseTime(after: Instant, target: Double): Instant {
        val jd0 = julianDay(after)
        fun f(jd: Double) = mod(elong(jd) - target + 180, 360.0) - 180
        var jd = jd0 + mod(-f(jd0), 360.0) / SYNODIC_RATE
        for (i in 0 until 12) {
            val dv = f(jd)
            jd -= dv / SYNODIC_RATE
            if (abs(dv) < 1e-6) break
        }
        if (jd < jd0 - 1e-6) return nextPhaseTime(fromJd(jd0 + 1.0), target)
        return fromJd(jd)
    }

    /** Days since the most recent new moon (moon.age_days). */
    fun ageDays(t: Instant): Double {
        var prev = nextPhaseTime(t.minusSeconds(((SYNODIC_MONTH + 1) * 86400).toLong()), 0.0)
        while (true) {
            val nxt = nextPhaseTime(prev.plusSeconds(86400), 0.0)
            if (nxt.isAfter(t)) break
            prev = nxt
        }
        return (t.toEpochMilli() - prev.toEpochMilli()) / 86_400_000.0
    }

    // ------------------------------------------------------------ rise / set
    private fun g(jd: Double, lat: Double, lon: Double): Double { val (a, h0) = moonAltitude(jd, lat, lon); return a - h0 }

    private fun refine(gf: (Double) -> Double, a0: Double, b0: Double, fa0: Double, fb0: Double, iters: Int): Double {
        var a = a0; var b = b0; var fa = fa0; var fb = fb0
        repeat(iters) {
            val c = if (fb != fa) b - fb * (b - a) / (fb - fa) else (a + b) / 2
            val fc = gf(c)
            if (abs(fc) < 1e-5 || abs(b - a) < 1e-6) return c
            if ((fa < 0) == (fc < 0)) { a = c; fa = fc } else { b = c; fb = fc }
        }
        return (a + b) / 2
    }

    /** Moonrise and moonset during the local calendar day (null if it doesn't happen). */
    fun riseSet(day: LocalDate, lat: Double, lon: Double, zone: ZoneId): Pair<ZonedDateTime?, ZonedDateTime?> {
        val jdA = julianDay(day.atStartOfDay(zone).toInstant())
        val jdB = julianDay(day.plusDays(1).atStartOfDay(zone).toInstant())
        val gf = { jd: Double -> g(jd, lat, lon) }
        var rise: ZonedDateTime? = null; var set: ZonedDateTime? = null
        val step = 30.0 / 1440
        var jd = jdA; var v = gf(jdA)
        while (jd < jdB - 1e-9) {
            val nxt = minOf(jd + step, jdB)
            val nv = gf(nxt)
            if ((v < 0 && nv >= 0) || (v >= 0 && nv < 0)) {
                val c = refine(gf, jd, nxt, v, nv, 8)
                val whenT = fromJd(c).atZone(zone)
                if (v < 0 && nv >= 0 && rise == null) rise = whenT
                else if (v >= 0 && nv < 0 && set == null) set = whenT
            }
            jd = nxt; v = nv
        }
        return rise to set
    }

    data class Pass(val up: Boolean, val rise: Instant?, val set: Instant?)

    /** The Moon's current trip across the sky at t: the moonrise that began it and the moonset that ends it. */
    fun moonPass(t: Instant, lat: Double, lon: Double, spanHours: Double = 30.0): Pass {
        val gf = { jd: Double -> g(jd, lat, lon) }
        val jd0 = julianDay(t)
        val v0 = gf(jd0)
        if (v0 < 0) return Pass(false, null, null)
        val step = 20.0 / 1440
        val n = (spanHours * 60 / 20).toInt()
        var rise: Instant? = null; var set: Instant? = null
        var a = jd0; var fa = v0
        for (i in 0 until n) {
            val b = a - step; val fb = gf(b)
            if (fb < 0) { rise = fromJd(refine(gf, b, a, fb, fa, 10)); break }
            a = b; fa = fb
        }
        a = jd0; fa = v0
        for (i in 0 until n) {
            val b = a + step; val fb = gf(b)
            if (fb < 0) { set = fromJd(refine(gf, a, b, fa, fb, 10)); break }
            a = b; fa = fb
        }
        return Pass(true, rise, set)
    }

    // ------------------------------------------------------------ daily view
    data class MoonDay(val date: LocalDate, val phase: Phase, val name: String, val rise: ZonedDateTime?,
                       val set: ZonedDateTime?, val alwaysUp: Boolean = false, val alwaysDown: Boolean = false)

    val PRINCIPAL = listOf(0.0 to "New Moon", 90.0 to "First Quarter", 180.0 to "Full Moon", 270.0 to "Last Quarter")

    /** A zone from an IANA name, else a fixed offset, else UTC (moon.tz_for). */
    fun zoneFor(name: String?, utcOffsetSeconds: Int? = null): ZoneId {
        if (name != null && name !in setOf("auto", "GMT", "UTC")) try { return ZoneId.of(name) } catch (e: Exception) { }
        if (utcOffsetSeconds != null) return ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        return ZoneOffset.UTC
    }

    fun moonDays(lat: Double, lon: Double, zone: ZoneId, first: LocalDate, days: Int = 7): List<MoonDay> {
        val start = first.atStartOfDay(zone).toInstant()
        val end = first.plusDays(days + 1L).atStartOfDay(zone).toInstant()
        val events = mutableListOf<Pair<ZonedDateTime, String>>()
        val t = start.minusSeconds(86400)
        for ((target, name) in PRINCIPAL) {
            var e = nextPhaseTime(t, target)
            while (e.isBefore(end)) {
                events += e.atZone(zone) to name
                e = nextPhaseTime(e.plusSeconds(2 * 86400), target)
            }
        }
        return (0 until days).map { i ->
            val dd = first.plusDays(i.toLong())
            val noon = ZonedDateTime.of(dd, LocalTime.NOON, zone).toInstant()
            val ph = phase(noon)
            var name = events.firstOrNull { it.first.toLocalDate() == dd }?.second ?: ph.name
            if (name in PRINCIPAL.map { it.second } && events.none { it.first.toLocalDate() == dd && it.second == name }) {
                name = when {
                    ph.elongation < 90 -> "Waxing Crescent"; ph.elongation < 180 -> "Waxing Gibbous"
                    ph.elongation < 270 -> "Waning Gibbous"; else -> "Waning Crescent"
                }
            }
            val (rr, ss) = riseSet(dd, lat, lon, zone)
            if (rr == null && ss == null) {
                val (alt, h0) = moonAltitude(julianDay(noon), lat, lon)
                MoonDay(dd, ph, name, null, null, alt > h0, alt <= h0)
            } else MoonDay(dd, ph, name, rr, ss)
        }
    }

    /** The next New, First Quarter, Full and Last Quarter moons, soonest first. */
    fun upcomingPhases(after: Instant, zone: ZoneId): List<Pair<String, ZonedDateTime>> =
        PRINCIPAL.map { (target, name) -> name to nextPhaseTime(after, target).atZone(zone) }.sortedBy { it.second }

    // ------------------------------------------------------- view in the sky
    /** rotation: degrees to turn a north-up picture clockwise so "up" is the observer's zenith (parallactic angle). */
    data class SkyView(val whenT: ZonedDateTime, val rotation: Double, val altitude: Double, val azimuth: Double,
                       val up: Boolean, val isNow: Boolean)

    private fun hourAngle(jd: Double, lon: Double, ra: Double) = mod(gmst(jd) + lon - ra + 180, 360.0) - 180

    data class Geometry(val q: Double, val altitude: Double, val azimuth: Double, val h0: Double)

    fun skyGeometry(jdUt: Double, lat: Double, lon: Double): Geometry {
        val p = moonPosition(jdUt)
        val h = hourAngle(jdUt, lon, p.ra)
        val q = d(atan2(sn(h), tan(r(lat)) * cs(p.dec) - sn(p.dec) * cs(h)))
        val alt = d(asin(sn(lat) * sn(p.dec) + cs(lat) * cs(p.dec) * cs(h)))
        val az = mod(d(atan2(sn(h), cs(h) * sn(lat) - tan(r(p.dec)) * cs(lat))) + 180, 360.0)
        val h0 = 0.7275 * d(asin(6378.14 / p.distKm)) - 0.5667
        return Geometry(mod(q, 360.0), alt, az, h0)
    }

    fun nextTransit(after: Instant, lon: Double): Instant {
        val jd0 = julianDay(after)
        val h0 = hourAngle(jd0, lon, moonPosition(jd0).ra)
        var jd = jd0 + mod(-h0, 360.0) / LUNAR_DAY_RATE
        for (i in 0 until 8) {
            val h = hourAngle(jd, lon, moonPosition(jd).ra)
            jd -= h / LUNAR_DAY_RATE
            if (abs(h) < 1e-4) break
        }
        if (jd < jd0 - 1e-6) return nextTransit(fromJd(jd0 + 0.05), lon)
        return fromJd(jd)
    }

    fun skyView(t: Instant, lat: Double, lon: Double, zone: ZoneId): SkyView {
        val g0 = skyGeometry(julianDay(t), lat, lon)
        if (g0.altitude > g0.h0) return SkyView(t.atZone(zone), g0.q, g0.altitude, g0.azimuth, true, true)
        val tr = nextTransit(t, lon)
        val g1 = skyGeometry(julianDay(tr), lat, lon)
        return SkyView(tr.atZone(zone), g1.q, g1.altitude, g1.azimuth, g1.altitude > g1.h0, false)
    }

    fun dayView(day: LocalDate, lat: Double, lon: Double, zone: ZoneId): SkyView {
        val tr = nextTransit(day.atStartOfDay(zone).toInstant(), lon)
        val g1 = skyGeometry(julianDay(tr), lat, lon)
        return SkyView(tr.atZone(zone), g1.q, g1.altitude, g1.azimuth, g1.altitude > g1.h0, false)
    }
}

private val LR = arrayOf(
    intArrayOf(0,0,1,0,6288774,-20905355),
    intArrayOf(2,0,-1,0,1274027,-3699111),
    intArrayOf(2,0,0,0,658314,-2955968),
    intArrayOf(0,0,2,0,213618,-569925),
    intArrayOf(0,1,0,0,-185116,48888),
    intArrayOf(0,0,0,2,-114332,-3149),
    intArrayOf(2,0,-2,0,58793,246158),
    intArrayOf(2,-1,-1,0,57066,-152138),
    intArrayOf(2,0,1,0,53322,-170733),
    intArrayOf(2,-1,0,0,45758,-204586),
    intArrayOf(0,1,-1,0,-40923,-129620),
    intArrayOf(1,0,0,0,-34720,108743),
    intArrayOf(0,1,1,0,-30383,104755),
    intArrayOf(2,0,0,-2,15327,10321),
    intArrayOf(0,0,1,2,-12528,0),
    intArrayOf(0,0,1,-2,10980,79661),
    intArrayOf(4,0,-1,0,10675,-34782),
    intArrayOf(0,0,3,0,10034,-23210),
    intArrayOf(4,0,-2,0,8548,-21636),
    intArrayOf(2,1,-1,0,-7888,24208),
    intArrayOf(2,1,0,0,-6766,30824),
    intArrayOf(1,0,-1,0,-5163,-8379),
    intArrayOf(1,1,0,0,4987,-16675),
    intArrayOf(2,-1,1,0,4036,-12831),
    intArrayOf(2,0,2,0,3994,-10445),
    intArrayOf(4,0,0,0,3861,-11650),
    intArrayOf(2,0,-3,0,3665,14403),
    intArrayOf(0,1,-2,0,-2689,-7003),
    intArrayOf(2,0,-1,2,-2602,0),
    intArrayOf(2,-1,-2,0,2390,10056),
    intArrayOf(1,0,1,0,-2348,6322),
    intArrayOf(2,-2,0,0,2236,-9884),
    intArrayOf(0,1,2,0,-2120,5751),
    intArrayOf(0,2,0,0,-2069,0),
    intArrayOf(2,-2,-1,0,2048,-4950),
    intArrayOf(2,0,1,-2,-1773,4130),
    intArrayOf(2,0,0,2,-1595,0),
    intArrayOf(4,-1,-1,0,1215,-3958),
    intArrayOf(0,0,2,2,-1110,0),
    intArrayOf(3,0,-1,0,-892,3258),
    intArrayOf(2,1,1,0,-810,2616),
    intArrayOf(4,-1,-2,0,759,-1897),
    intArrayOf(0,2,-1,0,-713,-2117),
    intArrayOf(2,2,-1,0,-700,2354),
    intArrayOf(2,1,-2,0,691,0),
    intArrayOf(2,-1,0,-2,596,0),
    intArrayOf(4,0,1,0,549,-1423),
    intArrayOf(0,0,4,0,537,-1117),
    intArrayOf(4,-1,0,0,520,-1571),
    intArrayOf(1,0,-2,0,-487,-1739),
    intArrayOf(2,1,0,-2,-399,0),
    intArrayOf(0,0,2,-2,-381,-4421),
    intArrayOf(1,1,1,0,351,0),
    intArrayOf(3,0,-2,0,-340,0),
    intArrayOf(4,0,-3,0,330,0),
    intArrayOf(2,-1,2,0,327,0),
    intArrayOf(0,2,1,0,-323,1165),
    intArrayOf(1,1,-1,0,299,0),
    intArrayOf(2,0,3,0,294,0),
    intArrayOf(2,0,-1,-2,0,8752))

private val BT = arrayOf(
    intArrayOf(0,0,0,1,5128122),
    intArrayOf(0,0,1,1,280602),
    intArrayOf(0,0,1,-1,277693),
    intArrayOf(2,0,0,-1,173237),
    intArrayOf(2,0,-1,1,55413),
    intArrayOf(2,0,-1,-1,46271),
    intArrayOf(2,0,0,1,32573),
    intArrayOf(0,0,2,1,17198),
    intArrayOf(2,0,1,-1,9266),
    intArrayOf(0,0,2,-1,8822),
    intArrayOf(2,-1,0,-1,8216),
    intArrayOf(2,0,-2,-1,4324),
    intArrayOf(2,0,1,1,4200),
    intArrayOf(2,1,0,-1,-3359),
    intArrayOf(2,-1,-1,1,2463),
    intArrayOf(2,-1,0,1,2211),
    intArrayOf(2,-1,-1,-1,2065),
    intArrayOf(0,1,-1,-1,-1870),
    intArrayOf(4,0,-1,-1,1828),
    intArrayOf(0,1,0,1,-1794),
    intArrayOf(0,0,0,3,-1749),
    intArrayOf(0,1,-1,1,-1565),
    intArrayOf(1,0,0,1,-1491),
    intArrayOf(0,1,1,1,-1475),
    intArrayOf(0,1,1,-1,-1410),
    intArrayOf(0,1,0,-1,-1344),
    intArrayOf(1,0,0,-1,-1335),
    intArrayOf(0,0,3,1,1107),
    intArrayOf(4,0,0,-1,1021),
    intArrayOf(4,0,-1,1,833),
    intArrayOf(0,0,1,-3,777),
    intArrayOf(4,0,-2,1,671),
    intArrayOf(2,0,0,-3,607),
    intArrayOf(2,0,2,-1,596),
    intArrayOf(2,-1,1,-1,491),
    intArrayOf(2,0,-2,1,-451),
    intArrayOf(0,0,3,-1,439),
    intArrayOf(2,0,2,1,422),
    intArrayOf(2,0,-3,-1,421),
    intArrayOf(2,1,-1,1,-366),
    intArrayOf(2,1,0,1,-351),
    intArrayOf(4,0,0,1,331),
    intArrayOf(2,-1,1,1,315),
    intArrayOf(2,-2,0,-1,302),
    intArrayOf(0,0,1,3,-283),
    intArrayOf(2,1,1,-1,-229),
    intArrayOf(1,1,0,-1,223),
    intArrayOf(1,1,0,1,223))
