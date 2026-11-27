// RBSB-Weather sync format v1: the weather snapshot the phone sends (data item /rbsb/v1/snapshot).
// Values are in metric base units (°C, km/h, mm, hPa, %). Forecast times are local times at the
// place ("2026-11-28T14:00"), as the phone app keeps them; fetchedAt, sentAt and every other
// timestamp are unix milliseconds (UTC). A missing weather value is null, never a made-up number.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

data class Snapshot(
    /** Weather provider the phone used, e.g. "Open-Meteo" or "MET Norway". */
    val provider: String,
    /** When the phone fetched this weather (unix ms). Data age is measured from this. */
    val fetchedAt: Long,
    /** When the phone sent this snapshot (unix ms). Never earlier than fetchedAt. */
    val sentAt: Long,
    /** Counts up by one with every snapshot this phone install publishes, starting at 1. */
    val sequence: Long,
    /**
     * A random id the phone app creates once per install. A reinstalled phone app gets a new id
     * and its sequence starts again at 1; see [Ordering] for how the watch handles that.
     */
    val sourceId: String,
    val place: SnapPlace,
    val current: SnapCurrent,
    /** Up to 48 hours from the current hour, in time order. */
    val hourly: List<SnapHour>,
    /** Up to 10 days from today, in date order. */
    val daily: List<SnapDay>,
    /** The warnings the phone currently shows, after its own filters (muted ones are not sent). */
    val warnings: List<SnapWarning>,
    val sun: SunTimes,
    val moon: MoonState,
) {
    /**
     * The wind to use for an hour: that hour's own wind when the phone sent it, otherwise the
     * current wind. Hourly wind is optional in v1 (RBSB-Weather for Android 0.7.2 does not fetch it).
     */
    fun windAt(hour: SnapHour): Wind = hour.wind?.takeIf { it.hasAny } ?: current.wind
}

/** The one place synced in version 1: the phone's selected location. */
data class SnapPlace(
    val name: String,
    /** Rounded to 3 decimals (about 100 m). */
    val latitude: Double,
    val longitude: Double,
    /** IANA time zone of the place, e.g. "Pacific/Auckland". */
    val timezone: String,
    /** The place's UTC offset when the weather was fetched, in seconds. */
    val utcOffsetSeconds: Int,
    val country: String = "",
    val admin1: String = "",
)

/** Wind in km/h; direction is where it blows FROM, in degrees (0 = north). */
data class Wind(val speed: Double? = null, val gusts: Double? = null, val direction: Double? = null) {
    val hasAny: Boolean get() = speed != null || gusts != null || direction != null

    companion object {
        val NONE = Wind()
    }
}

data class SnapCurrent(
    /** Local time at the place, "2026-11-28T10:15". */
    val time: String,
    val isDay: Boolean,
    val temperature: Double? = null,
    val apparentTemperature: Double? = null,
    val humidity: Double? = null,
    /** Sea-level pressure, hPa. */
    val pressure: Double? = null,
    val cloudCover: Double? = null,
    val precipitation: Double? = null,
    /** WMO weather code (0 to 99), as the phone app uses. */
    val weatherCode: Int? = null,
    val wind: Wind = Wind.NONE,
)

data class SnapHour(
    /** Local time at the place, "2026-11-28T13:00". */
    val time: String,
    val temperature: Double? = null,
    val precipitationProbability: Double? = null,
    val precipitation: Double? = null,
    val weatherCode: Int? = null,
    /** Optional in v1; when null the watch uses the current wind ([Snapshot.windAt]). */
    val wind: Wind? = null,
)

data class SnapDay(
    /** Local date at the place, "2026-11-28". */
    val date: String,
    val weatherCode: Int? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val precipitationSum: Double? = null,
    val precipitationProbabilityMax: Double? = null,
    val windSpeedMax: Double? = null,
    val gustMax: Double? = null,
    val uvIndexMax: Double? = null,
    /** Local times at the place, or null (e.g. polar day or night). */
    val sunrise: String? = null,
    val sunset: String? = null,
)

/** The phone app's warning levels; "limit" is one of the user's own forecast limits. */
enum class WarningLevel(val wire: String) {
    RED("red"), ORANGE("orange"), YELLOW("yellow"), WATCH("watch"), MINOR("minor"), LIMIT("limit");

    companion object {
        fun fromWire(s: String): WarningLevel? = values().firstOrNull { it.wire == s }
    }
}

data class SnapWarning(
    val id: String,
    val level: WarningLevel,
    /** As the phone shows it, e.g. "Orange Heavy Rain Warning" or "My limit: rain over 50 mm". */
    val title: String,
    /** False for the user's own forecast limits. */
    val official: Boolean,
    val area: String = "",
    /** Start and end (unix ms), or null when not given. Expired warnings are removed at [end]. */
    val start: Long? = null,
    val end: Long? = null,
    /** Short summary, at most 500 characters. */
    val summary: String = "",
    /** Who issued it, e.g. "MetService". */
    val source: String = "",
    val hazard: String = "other",
)

/** Today's sunrise and sunset at the place (unix ms), or null when the Sun does not rise or set. */
data class SunTimes(val rise: Long? = null, val set: Long? = null)

/**
 * The Moon as the phone calculated it (approved plan v1.1, section 5).
 * Phase, illumination, bright limb and tilt are the values at [at] (normally fetchedAt).
 */
data class MoonState(
    /** When phase, illumination, brightLimb and tilt were calculated (unix ms). */
    val at: Long,
    /** Elongation in degrees: 0 = new, 90 = first quarter, 180 = full, 270 = last quarter. */
    val phase: Double,
    /** e.g. "Waxing Gibbous", as the phone app names it. */
    val phaseName: String,
    /** Lit fraction, 0 to 1. */
    val illumination: Double,
    /** Position angle of the bright limb, degrees from north through east (0 to 360). */
    val brightLimb: Double,
    /** Degrees to turn a north-up Moon picture clockwise to match the sky (parallactic angle). */
    val tilt: Double,
    /** Next moonrise and moonset (unix ms), or null when there is none in the coming day. */
    val rise: Long? = null,
    val set: Long? = null,
    val alwaysUp: Boolean = false,
    val alwaysDown: Boolean = false,
)
