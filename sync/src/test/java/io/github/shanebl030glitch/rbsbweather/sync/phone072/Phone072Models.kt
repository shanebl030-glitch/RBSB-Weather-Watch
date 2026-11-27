// TEST-ONLY copies of RBSB-Weather for Android 0.7.2's weather data classes.
// Source: shanebl030-glitch/RBSB-Weather-Android, tag v0.7.2, commit cdf422d94e78cfc1020db5ac3407c765dd6b6738,
// files app/src/main/java/io/github/shanebl030glitch/rbsbweather/data/Models.kt and Alerts.kt.
// The constructor fields are copied verbatim (and Alert.title(), which the mapping uses); the other
// methods are left out. The Android repository was only read, never changed.
// They let the tests prove that real 0.7.2 data fits sync format v1 before any phone code exists.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync.phone072

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = "",
    val timezone: String = "auto",
    val current: Boolean = false,
    val detail: Int = 0,
)

data class Current(
    val time: String,                 // local time at the place, "2026-11-22T10:15"
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val windSpeed: Double?,
    val windDirection: Double?,
    val windGusts: Double?,
    val pressure: Double?,
    val cloudCover: Double?,
    val precipitation: Double?,
)

data class Hour(
    val time: String,                 // local, "2026-11-22T13:00"
    val temperature: Double?,
    val precipitationProbability: Double?,
    val precipitation: Double?,
    val weatherCode: Int?,
)

data class Day(
    val date: String,                 // "2026-11-22"
    val weatherCode: Int?,
    val tempMax: Double?,
    val tempMin: Double?,
    val precipitationSum: Double?,
    val precipitationProbabilityMax: Double?,
    val windSpeedMax: Double?,
    val sunrise: String?,
    val sunset: String?,
    val uvIndexMax: Double?,
    val gustMax: Double? = null,       // km/h (Open-Meteo gives it for every day; others only today's current gust)
)

data class Forecast(
    val place: Place,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val current: Current,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val source: String,               // "Open-Meteo" or "MET Norway"
    val fetchedAt: Long = 0L,         // unix ms
    val fromCache: Boolean = false,
)

data class Alert(
    val id: String,
    val source: String,
    val level: String,                 // red | orange | yellow | watch | minor | limit
    val event: String,
    val headline: String = "",
    val description: String = "",
    val instruction: String = "",
    val area: String = "",
    val onset: Double? = null,         // unix seconds
    val expires: Double? = null,
    val issued: Double? = null,
    val url: String = "",
    val official: Boolean = true,
    val hazard: String = "other",
) {
    /** "Orange Heavy Rain Warning", "Strong Wind Watch", "My limit: rain over 50 mm". */
    fun title(): String {
        if (!official) return event
        val ev = event.trim()
        if (level in listOf("red", "orange", "yellow") && !ev.lowercase().startsWith(level)) return level.replaceFirstChar { it.uppercase() } + " " + ev
        return ev
    }
}
