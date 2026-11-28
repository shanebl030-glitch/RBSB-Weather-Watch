// The words over the sky that don't depend on the data's age (Stage 2, kept for Stage 3): the
// rounded temperature, the condition, today's high and low, and the debug sample label.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import kotlin.math.roundToInt

/** The words shown over the sky (kept separate so they can be unit-tested on the JVM). */
object SkyText {
    const val SAMPLE_LABEL = "SAMPLE DATA"

    /** Below this width (dp) the sample names are shortened, so they stay inside a 432 px circle. */
    const val NARROW_DP = 230

    private val SHORT = mapOf(
        "partly-cloudy-day" to "partly cloudy", "rain-night-warnings" to "rain + warnings", "thunder-warning" to "thunder",
        "snow-northern" to "snow", "hourly-wind-mixed" to "hourly wind", "times-with-seconds" to "seconds",
        "future-within-tolerance" to "future time", "unknown-fields" to "extra fields", "explicit-nulls" to "no values",
        "utc-timezone" to "UTC", "polar-night" to "polar night", "clear-night" to "clear night", "clear-day" to "clear day",
    )

    fun temperature(c: Double?): String = if (c == null || !c.isFinite()) "--°" else "${c.roundToInt()}°"

    fun condition(s: Snapshot): String = WeatherCodes.describe(s.current.weatherCode)

    /** Today's high and low, or null when the snapshot has no daily forecast for today. */
    fun highLow(s: Snapshot): String? {
        val today = s.daily.firstOrNull { it.date == s.current.time.take(10) } ?: s.daily.firstOrNull() ?: return null
        if (today.tempMax == null && today.tempMin == null) return null
        return "H ${temperature(today.tempMax)}  L ${temperature(today.tempMin)}"
    }

    fun shortName(name: String): String = SHORT[name] ?: name

    fun sampleLine(index: Int, count: Int, name: String, narrow: Boolean = false): String =
        "${index + 1}/$count · ${if (narrow) shortName(name) else name}"
}
