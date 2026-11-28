// The words over the sky for a snapshot at a moment (Stage 3): what "now" is, how old it is, and
// the labels that stop old or missing weather from looking current. Plain Kotlin (unit tested).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Freshness
import io.github.shanebl030glitch.rbsbweather.data.NowSource
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The kind of status chip over the sky. */
enum class ChipKind { NONE, OLD, NO_UPDATE, EXPIRED }

data class SkyLines(
    val place: String,
    val temperature: String,
    /** Shown dimmed when the value is old (very stale or last known). */
    val dimTemperature: Boolean,
    val condition: String,
    /** "Forecast for 15:00" or "Last known · Tue 18:00"; null when "now" is current. */
    val asOf: String?,
    val highLow: String?,
    /** "Updated 12 min ago" (always shown: the data's age is never hidden). */
    val age: String,
    val chip: ChipKind,
    val chipText: String?,
    val freshness: Freshness,
    /** Active warnings, most severe level first, or null. */
    val warningCount: Int,
    val topWarning: WarningLevel?,
)

object SkyLinesBuilder {
    fun build(s: Snapshot, settings: SyncSettings, nowMs: Long, watchZone: ZoneId = ZoneId.systemDefault()): SkyLines {
        val f = WeatherView.freshness(s, nowMs)
        val n = WeatherView.now(s, nowMs)
        val zone = WeatherView.zone(s)
        val fetched = Units.clockAt(s.fetchedAt, zone, watchZone, settings.clock)
        val fetchedDay = LocalDateTime.ofInstant(Instant.ofEpochMilli(s.fetchedAt), zone)
        val today = WeatherView.localNow(s, nowMs).toLocalDate()
        val fetchedWhen = if (fetchedDay.toLocalDate() == today) fetched else "${Units.dayName(fetchedDay.dayOfWeek)} $fetched"
        val asOf = when (n.source) {
            NowSource.CURRENT -> null
            NowSource.FORECAST_HOUR -> n.asOfLocal?.let { "Forecast for ${Units.clock(it, settings.clock)}" } ?: "Forecast"
            NowSource.LAST_KNOWN -> n.asOfLocal?.let {
                val day = if (it.toLocalDate() == today) "" else "${Units.dayName(it.dayOfWeek)} "
                "Last known · $day${Units.clock(it, settings.clock)}"
            } ?: "Last known"
        }
        val (chip, chipText) = when (f) {
            Freshness.FRESH, Freshness.AGEING -> ChipKind.NONE to null
            Freshness.STALE -> ChipKind.OLD to "Old data · from $fetchedWhen"
            Freshness.VERY_STALE -> ChipKind.NO_UPDATE to "No update since $fetchedWhen"
            Freshness.EXPIRED -> ChipKind.EXPIRED to "No current data"
        }
        val today0 = WeatherView.today(s, nowMs)
        val highLow = today0?.takeIf { it.tempMax != null || it.tempMin != null }?.let {
            "H ${Units.fmtTemp(it.tempMax, settings.temperatureUnit)}  L ${Units.fmtTemp(it.tempMin, settings.temperatureUnit)}"
        }
        val warnings = WeatherView.activeWarnings(s, nowMs, settings.minWarningLevel)
        return SkyLines(
            place = s.place.name,
            temperature = Units.fmtTemp(n.temperature, settings.temperatureUnit),
            dimTemperature = f == Freshness.VERY_STALE || f == Freshness.EXPIRED || n.source == NowSource.LAST_KNOWN,
            condition = WeatherCodes.describe(n.weatherCode),
            asOf = asOf,
            highLow = highLow,
            age = WeatherView.ageText(WeatherView.ageMs(s, nowMs)),
            chip = chip,
            chipText = chipText,
            freshness = f,
            warningCount = warnings.size,
            topWarning = warnings.firstOrNull()?.level,
        )
    }

    fun warningChip(count: Int): String = if (count == 1) "⚠ 1 warning" else "⚠ $count warnings"
}
