// RBSB-Weather sync format v1: the settings item (/rbsb/v1/settings, phone to watch) and the
// refresh request (/rbsb/v1/refresh, watch to phone). Names match the phone app's own settings.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

enum class TemperatureUnit { C, F }
enum class WindUnit { KMH, MS, MPH, KN }
enum class PrecipitationUnit { MM, IN }
enum class ClockFormat { H24, H12 }

/** The phone app's "minimum warning level" choices, with the same names. */
enum class MinWarningLevel(val wire: String) {
    ALL("all"), WATCH("watch"), ORANGE("orange"), RED("red");

    companion object {
        fun fromWire(s: String): MinWarningLevel? = values().firstOrNull { it.wire == s }
    }
}

/**
 * The six settings the watch follows (approved for Stage 1). Weather values always travel in
 * metric; the watch converts them for display with these. The warnings in a snapshot are already
 * filtered by the phone; [minWarningLevel] tells the watch which level the user chose.
 */
data class SyncSettings(
    val sentAt: Long,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.C,
    val windUnit: WindUnit = WindUnit.KMH,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val clock: ClockFormat = ClockFormat.H24,
    val animations: Boolean = true,
    val minWarningLevel: MinWarningLevel = MinWarningLevel.WATCH,
)

/**
 * "Please fetch now", sent by the watch when it opens with data over 45 minutes old (at most once
 * every 10 minutes; Stage 3). [haveFetchedAt] is the fetchedAt of the snapshot the watch has, if any.
 */
data class RefreshRequest(val sentAt: Long, val haveFetchedAt: Long? = null)
