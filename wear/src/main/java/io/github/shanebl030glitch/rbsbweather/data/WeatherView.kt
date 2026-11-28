// What the watch shows from a snapshot at a given moment (Stage 3): how old the data is, what
// "now" is, which forecast hours and days are still ahead, and which warnings are still active.
// Plain Kotlin, unit tested on the JVM. The rule throughout: old or missing weather is never
// presented as current (approved plan section 6; Shane's Stage 3 requirement).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.MinWarningLevel
import io.github.shanebl030glitch.rbsbweather.sync.SnapDay
import io.github.shanebl030glitch.rbsbweather.sync.SnapHour
import io.github.shanebl030glitch.rbsbweather.sync.SnapWarning
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import io.github.shanebl030glitch.rbsbweather.sync.Wind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** How old the weather is (plan section 6). Age is measured from fetchedAt, not from arrival. */
enum class Freshness { FRESH, AGEING, STALE, VERY_STALE, EXPIRED }

/** Where the "now" values come from. */
enum class NowSource {
    /** The phone's current conditions (data under 3 h old). */
    CURRENT,
    /** The cached hourly forecast for the hour that is now (data 3 h old or more). */
    FORECAST_HOUR,
    /** Nothing covers now: the last known conditions, always labelled as such. */
    LAST_KNOWN,
}

/** The values shown as "now", with where they came from. Missing values are null, never guessed. */
data class NowView(
    val source: NowSource,
    /** The forecast hour used (FORECAST_HOUR) or the time of the last known conditions (LAST_KNOWN), local at the place. */
    val asOfLocal: LocalDateTime?,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val precipitation: Double?,
    val weatherCode: Int?,
    val wind: Wind?,
)

object WeatherView {
    const val AGEING_MS = 45 * 60_000L
    const val STALE_MS = 3 * 3_600_000L
    const val VERY_STALE_MS = 12 * 3_600_000L
    private const val HOUR_MS = 3_600_000L

    /** The place's time zone (validated as a real IANA zone by the sync format), or its fixed offset. */
    fun zone(s: Snapshot): ZoneId = try { ZoneId.of(s.place.timezone) } catch (e: Exception) {
        ZoneOffset.ofTotalSeconds(s.place.utcOffsetSeconds.coerceIn(-18 * 3600, 18 * 3600))
    }

    fun localNow(s: Snapshot, nowMs: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone(s))

    /** A forecast hour's start as unix ms, or null for an unreadable time. */
    fun hourStart(s: Snapshot, h: SnapHour): Long? = try {
        LocalDateTime.parse(h.time).atZone(zone(s)).toInstant().toEpochMilli()
    } catch (e: Exception) { null }

    /**
     * When the cached forecast runs out: the end of the last forecast hour, or the end of the last
     * forecast day when there are no hours; with no forecast at all, 12 hours after the fetch.
     */
    fun forecastEnd(s: Snapshot): Long {
        s.hourly.mapNotNull { hourStart(s, it) }.maxOrNull()?.let { return it + HOUR_MS }
        s.daily.lastOrNull()?.let { d ->
            try { return LocalDate.parse(d.date).plusDays(1).atStartOfDay(zone(s)).toInstant().toEpochMilli() } catch (e: Exception) { }
        }
        return s.fetchedAt + VERY_STALE_MS
    }

    fun ageMs(s: Snapshot, nowMs: Long): Long = (nowMs - s.fetchedAt).coerceAtLeast(0)

    fun freshness(s: Snapshot, nowMs: Long): Freshness {
        if (nowMs >= forecastEnd(s)) return Freshness.EXPIRED
        val age = ageMs(s, nowMs)
        return when {
            age < AGEING_MS -> Freshness.FRESH
            age < STALE_MS -> Freshness.AGEING
            age < VERY_STALE_MS -> Freshness.STALE
            else -> Freshness.VERY_STALE
        }
    }

    /** "Updated just now", "Updated 12 min ago", "Updated 1 h 20 min ago", "Updated 5 h ago", "Updated 2 days ago". */
    fun ageText(ageMs: Long): String {
        val min = ageMs / 60_000
        return when {
            min < 1 -> "Updated just now"
            min < 60 -> "Updated $min min ago"
            min < 180 -> if (min % 60 == 0L) "Updated ${min / 60} h ago" else "Updated ${min / 60} h ${min % 60} min ago"
            min < 24 * 60 -> "Updated ${min / 60} h ago"
            min < 48 * 60 -> "Updated 1 day ago"
            else -> "Updated ${min / (24 * 60)} days ago"
        }
    }

    /** The forecast hour that contains [nowMs], or null. */
    fun hourAt(s: Snapshot, nowMs: Long): SnapHour? =
        s.hourly.firstOrNull { h -> hourStart(s, h)?.let { nowMs >= it && nowMs < it + HOUR_MS } == true }

    /**
     * "Now". Fresh or ageing data: the phone's current conditions. Stale or very stale: the cached
     * forecast for this hour (values the hourly forecast doesn't carry stay empty rather than show
     * the old reading as current). Expired, or no forecast hour for now: the last known conditions.
     */
    fun now(s: Snapshot, nowMs: Long): NowView {
        val c = s.current
        val f = freshness(s, nowMs)
        if (f == Freshness.FRESH || f == Freshness.AGEING) {
            return NowView(NowSource.CURRENT, null, c.temperature, c.apparentTemperature, c.humidity, c.pressure,
                c.precipitation, c.weatherCode, c.wind.takeIf { it.hasAny })
        }
        if (f != Freshness.EXPIRED) hourAt(s, nowMs)?.let { h ->
            return NowView(NowSource.FORECAST_HOUR, LocalDateTime.parse(h.time), h.temperature, null, null, null,
                h.precipitation, h.weatherCode, h.wind?.takeIf { it.hasAny })
        }
        val asOf = try { LocalDateTime.parse(c.time) } catch (e: Exception) { null }
        return NowView(NowSource.LAST_KNOWN, asOf, c.temperature, c.apparentTemperature, c.humidity, c.pressure,
            c.precipitation, c.weatherCode, c.wind.takeIf { it.hasAny })
    }

    /** Forecast hours from the one that is now onwards (past hours drop off as time passes). */
    fun hoursAhead(s: Snapshot, nowMs: Long, max: Int = 48): List<SnapHour> =
        s.hourly.filter { h -> hourStart(s, h)?.let { it + HOUR_MS > nowMs } == true }.take(max)

    /** Forecast days from today at the place onwards. */
    fun daysAhead(s: Snapshot, nowMs: Long): List<SnapDay> {
        val today = localNow(s, nowMs).toLocalDate()
        return s.daily.filter { d -> try { !LocalDate.parse(d.date).isBefore(today) } catch (e: Exception) { false } }
    }

    /** Today's forecast day at the place, or null when the forecast no longer covers today. */
    fun today(s: Snapshot, nowMs: Long): SnapDay? {
        val today = localNow(s, nowMs).toLocalDate().toString()
        return s.daily.firstOrNull { it.date == today }
    }

    /** Most severe first; the user's own limits after the official levels. */
    private val SEVERITY = listOf(WarningLevel.RED, WarningLevel.ORANGE, WarningLevel.YELLOW, WarningLevel.WATCH, WarningLevel.MINOR, WarningLevel.LIMIT)

    private fun shown(level: WarningLevel, min: MinWarningLevel): Boolean = when (level) {
        WarningLevel.LIMIT -> true                                   // the user's own forecast limits
        WarningLevel.RED -> true
        WarningLevel.ORANGE -> min != MinWarningLevel.RED
        WarningLevel.YELLOW, WarningLevel.WATCH -> min == MinWarningLevel.ALL || min == MinWarningLevel.WATCH
        WarningLevel.MINOR -> min == MinWarningLevel.ALL
    }

    /**
     * Warnings still in force at [nowMs] (each one drops off at its own end time, even offline),
     * at or above the user's minimum level, most severe first. The phone already applied the same
     * filter, so this never hides a warning the phone shows; it keeps old cached data consistent.
     */
    fun activeWarnings(s: Snapshot, nowMs: Long, min: MinWarningLevel = MinWarningLevel.ALL): List<SnapWarning> =
        s.warnings.filter { w -> (w.end?.let { it > nowMs } ?: true) && shown(w.level, min) }
            .sortedWith(compareBy<SnapWarning> { SEVERITY.indexOf(it.level) }.thenBy { it.start ?: Long.MIN_VALUE })
}
