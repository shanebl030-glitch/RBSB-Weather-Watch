// Unit conversion and display on the watch. Weather arrives in °C, km/h, mm and hPa (sync format
// v1) and is converted only when shown, with the phone's six settings (Stage 1 decision 5).
// Number formats follow RBSB-Weather for Android v0.7.2 (data/Units.kt, commit cdf422d), so the
// phone and the watch show the same numbers. Missing values show as "–", never as a made-up value.
// Times are shown in the place's own time zone, with a short zone label when it differs from the
// watch's (Stage 3 decision D3). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.ClockFormat
import io.github.shanebl030glitch.rbsbweather.sync.PrecipitationUnit
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import io.github.shanebl030glitch.rbsbweather.sync.TemperatureUnit
import io.github.shanebl030glitch.rbsbweather.sync.WindUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

object Units {
    const val MISSING = "–"

    fun temp(c: Double?, u: TemperatureUnit): Double? = c?.takeIf { it.isFinite() }?.let { if (u == TemperatureUnit.F) it * 9.0 / 5.0 + 32.0 else it }

    /** "17°" (the phone rounds with roundToInt, so -3.5 shows as -3°). */
    fun fmtTemp(c: Double?, u: TemperatureUnit): String = temp(c, u)?.let { "${it.roundToInt()}°" } ?: "--°"

    fun windValue(kmh: Double?, u: WindUnit): Double? = kmh?.takeIf { it.isFinite() }?.let {
        when (u) {
            WindUnit.KMH -> it
            WindUnit.MS -> it / 3.6
            WindUnit.MPH -> it / 1.609344
            WindUnit.KN -> it / 1.852
        }
    }

    fun windLabel(u: WindUnit): String = when (u) { WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.MPH -> "mph"; WindUnit.KN -> "kn" }

    /** "24" (the number only, in the chosen unit). */
    fun fmtWindNumber(kmh: Double?, u: WindUnit): String = windValue(kmh, u)?.roundToInt()?.toString() ?: MISSING

    /** "24 km/h". */
    fun fmtWind(kmh: Double?, u: WindUnit): String = windValue(kmh, u)?.let { "${it.roundToInt()} ${windLabel(u)}" } ?: MISSING

    fun fmtPrecip(mm: Double?, u: PrecipitationUnit): String {
        val v = mm?.takeIf { it.isFinite() } ?: return MISSING
        return if (u == PrecipitationUnit.IN) String.format(Locale.US, "%.2f in", v / 25.4) else String.format(Locale.US, "%.1f mm", v)
    }

    fun fmtPercent(p: Double?): String = p?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}%" } ?: MISSING

    fun fmtPressure(hpa: Double?): String = hpa?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} hPa" } ?: MISSING

    fun fmtUv(uv: Double?): String = uv?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.0f", it) } ?: MISSING

    private val COMPASS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    /** 16-point compass name for a direction in degrees (where the wind blows FROM), "" if unknown. */
    fun compass(degrees: Double?): String {
        val d = degrees?.takeIf { it.isFinite() } ?: return ""
        return COMPASS[(((d % 360 + 360) % 360) / 22.5 + 0.5).toInt() % 16]
    }

    fun clock(h: Int, m: Int, f: ClockFormat): String =
        if (f == ClockFormat.H12) "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "am" else "pm"}"
        else "%02d:%02d".format(h, m)

    /** "14:05" / "2:05 pm" from a local time at the place. */
    fun clock(t: LocalDateTime, f: ClockFormat): String = clock(t.hour, t.minute, f)

    /** Short hour label for forecast rows: "15:00" / "3 pm". */
    fun hourLabel(t: LocalDateTime, f: ClockFormat): String =
        if (f == ClockFormat.H12) "${if (t.hour % 12 == 0) 12 else t.hour % 12} ${if (t.hour < 12) "am" else "pm"}"
        else "%02d:00".format(t.hour)

    /** "Today", "Tmrw", or the short day name ("Mon"), relative to the place's own date (as Desktop's forecast strip). */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tmrw"
        else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }

    fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /**
     * A short zone label ("NZDT") for an instant at the place, or "" when the watch shows the same
     * UTC offset at that moment, so labels appear only when they help (decision D3).
     */
    fun zoneLabel(place: ZoneId, watch: ZoneId, at: Instant): String {
        val offset = place.rules.getOffset(at)
        if (offset == watch.rules.getOffset(at)) return ""
        val name = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(at.atZone(place))
        // Where the platform has no short name it prints "GMT+13:00"; show the shorter "UTC+13".
        if (name.isEmpty() || name.startsWith("GMT") || name.startsWith("UTC") || name.contains(':')) return offsetLabel(offset.totalSeconds)
        return name
    }

    /** "UTC+13", "UTC-3:30", "UTC" for a UTC offset in seconds. */
    fun offsetLabel(totalSeconds: Int): String {
        if (totalSeconds == 0) return "UTC"
        val sign = if (totalSeconds < 0) "-" else "+"
        val m = kotlin.math.abs(totalSeconds) / 60
        return if (m % 60 == 0) "UTC$sign${m / 60}" else "UTC$sign${m / 60}:${"%02d".format(m % 60)}"
    }

    /** "14:05" or "14:05 NZDT": a moment shown in the place's time, labelled when the watch differs. */
    fun clockAt(epochMs: Long, place: ZoneId, watch: ZoneId, f: ClockFormat): String {
        val at = Instant.ofEpochMilli(epochMs)
        val label = zoneLabel(place, watch, at)
        val t = LocalDateTime.ofInstant(at, place)
        return if (label.isEmpty()) clock(t, f) else "${clock(t, f)} $label"
    }

    /** The defaults until the phone's settings arrive (Stage 1 defaults: °C, km/h, mm, 24 h). */
    val DEFAULT_SETTINGS = SyncSettings(sentAt = 0L)
}
