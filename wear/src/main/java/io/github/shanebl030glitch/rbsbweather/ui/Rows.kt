// The text of the forecast, wind, warning and detail rows (Stage 3), built from a snapshot at a
// moment with the phone's settings. Plain Kotlin (unit tested): what the screens draw is decided
// here, so the rules are checked without a device. Missing values show as "–".
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Freshness
import io.github.shanebl030glitch.rbsbweather.data.NowSource
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.sync.SnapDay
import io.github.shanebl030glitch.rbsbweather.sync.SnapWarning
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class HourRow(val time: String, val code: Int?, val isDay: Boolean, val temp: String, val rainChance: String, val rainAmount: String?, val wind: String?)
data class DayRow(val date: String, val label: String, val code: Int?, val high: String, val low: String, val rainChance: String)
data class Detail(val label: String, val value: String)
data class WindInfo(
    val speed: String, val unit: String, val gusts: String?, val from: String,
    /** Degrees the wind blows FROM, or null. The arrow points the way it blows to. */
    val directionFrom: Double?,
    /** The next hours' speeds, only when the snapshot carries hourly wind (0.7.2 doesn't). */
    val nextHours: List<Pair<String, String>>,
    /** "Forecast for 15:00" etc. when the wind isn't current, or null. */
    val note: String?,
)
data class WarningCard(val id: String, val level: WarningLevel, val levelLabel: String, val title: String, val area: String,
                       val span: String, val source: String, val summary: String)

object Rows {
    /** Day or night for a local hour at the place: that day's sunrise and sunset, else 06:00 to 18:00. */
    fun isDayAt(s: Snapshot, t: LocalDateTime): Boolean {
        val d = s.daily.firstOrNull { it.date == t.toLocalDate().toString() }
        val rise = d?.sunrise?.let { try { LocalDateTime.parse(it) } catch (e: Exception) { null } }
        val set = d?.sunset?.let { try { LocalDateTime.parse(it) } catch (e: Exception) { null } }
        if (rise != null && set != null) return !t.isBefore(rise) && t.isBefore(set)
        if (d != null && d.sunrise == null && d.sunset == null) {
            // polar day or night: the snapshot's isDay for the fetch day is the best evidence
            return s.current.isDay
        }
        return t.hour in 6..17
    }

    fun hours(s: Snapshot, st: SyncSettings, nowMs: Long, max: Int = 48): List<HourRow> {
        val now = WeatherView.localNow(s, nowMs)
        return WeatherView.hoursAhead(s, nowMs, max).mapNotNull { h ->
            val t = try { LocalDateTime.parse(h.time) } catch (e: Exception) { return@mapNotNull null }
            val isNowHour = t.toLocalDate() == now.toLocalDate() && t.hour == now.hour
            HourRow(
                time = if (isNowHour) "Now" else Units.hourLabel(t, st.clock),
                code = h.weatherCode, isDay = isDayAt(s, t),
                temp = Units.fmtTemp(h.temperature, st.temperatureUnit),
                rainChance = Units.fmtPercent(h.precipitationProbability),
                rainAmount = h.precipitation?.takeIf { it > 0.0 }?.let { Units.fmtPrecip(it, st.precipitationUnit) },
                wind = h.wind?.speed?.let { Units.fmtWind(it, st.windUnit) },
            )
        }
    }

    fun days(s: Snapshot, st: SyncSettings, nowMs: Long): List<DayRow> {
        val today = WeatherView.localNow(s, nowMs).toLocalDate()
        return WeatherView.daysAhead(s, nowMs).mapNotNull { d ->
            val date = try { LocalDate.parse(d.date) } catch (e: Exception) { return@mapNotNull null }
            DayRow(d.date, Units.dayLabel(date, today), d.weatherCode,
                Units.fmtTemp(d.tempMax, st.temperatureUnit), Units.fmtTemp(d.tempMin, st.temperatureUnit),
                Units.fmtPercent(d.precipitationProbabilityMax))
        }
    }

    /** The "Now" details: only values that exist for now (stale data has no current humidity or pressure). */
    fun nowDetails(s: Snapshot, st: SyncSettings, nowMs: Long): List<Detail> {
        val n = WeatherView.now(s, nowMs)
        return buildList {
            n.apparentTemperature?.let { add(Detail("Feels like", Units.fmtTemp(it, st.temperatureUnit))) }
            n.humidity?.let { add(Detail("Humidity", Units.fmtPercent(it))) }
            n.pressure?.let { add(Detail("Pressure", Units.fmtPressure(it))) }
            n.precipitation?.let { add(Detail(if (n.source == NowSource.FORECAST_HOUR) "Rain this hour" else "Rain now", Units.fmtPrecip(it, st.precipitationUnit))) }
            n.wind?.speed?.let { add(Detail("Wind", "${Units.fmtWind(it, st.windUnit)} ${Units.compass(n.wind.direction)}".trim())) }
        }
    }

    fun nowTitle(s: Snapshot, st: SyncSettings, nowMs: Long): String {
        val n = WeatherView.now(s, nowMs)
        return when (n.source) {
            NowSource.CURRENT -> "Now"
            NowSource.FORECAST_HOUR -> n.asOfLocal?.let { "Forecast for ${Units.clock(it, st.clock)}" } ?: "Forecast"
            NowSource.LAST_KNOWN -> "Last known"
        }
    }

    fun wind(s: Snapshot, st: SyncSettings, nowMs: Long): WindInfo {
        val n = WeatherView.now(s, nowMs)
        val w = n.wind
        val hasHourlyWind = s.hourly.any { it.wind?.hasAny == true }
        val next = if (!hasHourlyWind) emptyList() else WeatherView.hoursAhead(s, nowMs).drop(1).take(5).mapNotNull { h ->
            val sp = h.wind?.speed ?: return@mapNotNull null
            val t = try { LocalDateTime.parse(h.time) } catch (e: Exception) { return@mapNotNull null }
            Units.hourLabel(t, st.clock) to Units.fmtWindNumber(sp, st.windUnit)
        }
        val note = when (n.source) {
            NowSource.CURRENT -> null
            NowSource.FORECAST_HOUR -> n.asOfLocal?.let { "Forecast for ${Units.clock(it, st.clock)}" }
            NowSource.LAST_KNOWN -> "Last known"
        }
        return WindInfo(
            speed = Units.fmtWindNumber(w?.speed, st.windUnit), unit = Units.windLabel(st.windUnit),
            gusts = w?.gusts?.let { Units.fmtWindNumber(it, st.windUnit) },
            from = Units.compass(w?.direction), directionFrom = w?.direction?.takeIf { it.isFinite() },
            nextHours = next, note = note,
        )
    }

    fun warnings(s: Snapshot, st: SyncSettings, nowMs: Long, watchZone: ZoneId = ZoneId.systemDefault()): List<WarningCard> {
        val zone = WeatherView.zone(s)
        return WeatherView.activeWarnings(s, nowMs, st.minWarningLevel).map { w -> card(w, zone, watchZone, st, nowMs) }
    }

    private fun card(w: SnapWarning, zone: ZoneId, watchZone: ZoneId, st: SyncSettings, nowMs: Long): WarningCard {
        fun at(ms: Long): String {
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
            val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone).toLocalDate()
            val day = if (t.toLocalDate() == today) "" else "${Units.dayName(t.dayOfWeek)} "
            return day + Units.clockAt(ms, zone, watchZone, st.clock)
        }
        val start = w.start; val end = w.end
        val span = when {
            start != null && end != null -> if (start <= nowMs) "Until ${at(end)}" else "${at(start)} – ${at(end)}"
            end != null -> "Until ${at(end)}"
            start != null -> "From ${at(start)}"
            else -> ""
        }
        val source = listOf(w.source, if (w.official) "official" else "your own limit").filter { it.isNotBlank() }.joinToString(" · ")
        return WarningCard(w.id, w.level, levelLabel(w.level), w.title, w.area, span, source, w.summary)
    }

    /** "As of 14:05 · may be out of date" when warnings come from data 3 h old or more; else null. */
    fun warningsNote(s: Snapshot, st: SyncSettings, nowMs: Long, watchZone: ZoneId = ZoneId.systemDefault()): String? {
        val f = WeatherView.freshness(s, nowMs)
        if (f == Freshness.FRESH || f == Freshness.AGEING) return null
        return "As of ${Units.clockAt(s.fetchedAt, WeatherView.zone(s), watchZone, st.clock)} · may be out of date"
    }

    fun dayDetails(s: Snapshot, st: SyncSettings, day: SnapDay): List<Detail> = buildList {
        add(Detail("Rain chance", Units.fmtPercent(day.precipitationProbabilityMax)))
        add(Detail("Rain total", Units.fmtPrecip(day.precipitationSum, st.precipitationUnit)))
        add(Detail("Wind up to", Units.fmtWind(day.windSpeedMax, st.windUnit)))
        add(Detail("Gusts up to", Units.fmtWind(day.gustMax, st.windUnit)))
        add(Detail("UV index", Units.fmtUv(day.uvIndexMax)))
        fun t(x: String?) = x?.let { try { Units.clock(LocalDateTime.parse(it), st.clock) } catch (e: Exception) { null } } ?: "–"
        add(Detail("Sunrise", t(day.sunrise)))
        add(Detail("Sunset", t(day.sunset)))
    }

    fun condition(code: Int?): String = WeatherCodes.describe(code)

    fun levelLabel(level: WarningLevel): String = when (level) {
        WarningLevel.RED -> "RED"; WarningLevel.ORANGE -> "ORANGE"; WarningLevel.YELLOW -> "YELLOW"
        WarningLevel.WATCH -> "WATCH"; WarningLevel.MINOR -> "MINOR"; WarningLevel.LIMIT -> "YOUR LIMIT"
    }
}
