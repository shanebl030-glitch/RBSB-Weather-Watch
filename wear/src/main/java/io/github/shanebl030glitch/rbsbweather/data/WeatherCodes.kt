// WMO weather interpretation codes (as used by Open-Meteo).
// Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/data/WeatherCodes.kt. Watch changes: none.
// Stage 2 uses only describe() (the condition text); the symbols and icons belong to Stage 3.
package io.github.shanebl030glitch.rbsbweather.data

object WeatherCodes {
    private data class Entry(val text: String, val day: String, val night: String)

    private val codes = mapOf(
        0 to Entry("Clear sky", "☀️", "🌙"),
        1 to Entry("Mainly clear", "🌤️", "🌙"),
        2 to Entry("Partly cloudy", "⛅", "☁️"),
        3 to Entry("Overcast", "☁️", "☁️"),
        45 to Entry("Fog", "🌫️", "🌫️"),
        48 to Entry("Depositing rime fog", "🌫️", "🌫️"),
        51 to Entry("Light drizzle", "🌦️", "🌧️"),
        53 to Entry("Drizzle", "🌦️", "🌧️"),
        55 to Entry("Dense drizzle", "🌧️", "🌧️"),
        56 to Entry("Light freezing drizzle", "🌧️", "🌧️"),
        57 to Entry("Freezing drizzle", "🌧️", "🌧️"),
        61 to Entry("Light rain", "🌦️", "🌧️"),
        63 to Entry("Rain", "🌧️", "🌧️"),
        65 to Entry("Heavy rain", "🌧️", "🌧️"),
        66 to Entry("Light freezing rain", "🌧️", "🌧️"),
        67 to Entry("Freezing rain", "🌧️", "🌧️"),
        71 to Entry("Light snow", "🌨️", "🌨️"),
        73 to Entry("Snow", "🌨️", "🌨️"),
        75 to Entry("Heavy snow", "❄️", "❄️"),
        77 to Entry("Snow grains", "🌨️", "🌨️"),
        80 to Entry("Light showers", "🌦️", "🌧️"),
        81 to Entry("Showers", "🌧️", "🌧️"),
        82 to Entry("Violent showers", "⛈️", "⛈️"),
        85 to Entry("Light snow showers", "🌨️", "🌨️"),
        86 to Entry("Snow showers", "❄️", "❄️"),
        95 to Entry("Thunderstorm", "⛈️", "⛈️"),
        96 to Entry("Thunderstorm with hail", "⛈️", "⛈️"),
        99 to Entry("Severe thunderstorm with hail", "⛈️", "⛈️"),
    )
    private val unknown = Entry("Unknown", "❔", "❔")

    val known: Set<Int> get() = codes.keys

    fun describe(code: Int?): String = (code?.let { codes[it] } ?: unknown).text

    fun symbol(code: Int?, isDay: Boolean = true): String =
        (code?.let { codes[it] } ?: unknown).let { if (isDay) it.day else it.night }

    // what the animated sky needs to know
    fun isRain(code: Int?) = code != null && (code in 51..67 || code in 80..82 || code >= 95)
    fun isSnow(code: Int?) = code != null && (code in 71..77 || code in 85..86)
    fun isThunder(code: Int?) = code != null && code >= 95
    fun isFog(code: Int?) = code == 45 || code == 48

    /** 0 (clear) .. 1 (overcast): how cloudy the sky looks for a code. */
    fun cloudiness(code: Int?): Float = when {
        code == null -> 0.3f
        code == 0 -> 0f
        code == 1 -> 0.2f
        code == 2 -> 0.5f
        else -> 0.95f
    }

    /**
     * MET Norway symbol code ("lightrainshowers_day") → WMO code (80).
     * Sleet is shown as snow, the closest WMO kind.
     */
    fun fromMetSymbol(symbol: String?): Int? {
        if (symbol.isNullOrBlank()) return null
        val base = symbol.substringBefore('_')
        when (base) {
            "clearsky" -> return 0
            "fair" -> return 1
            "partlycloudy" -> return 2
            "cloudy" -> return 3
            "fog" -> return 45
        }
        val level = when {
            base.startsWith("light") -> 0
            base.startsWith("heavy") -> 2
            else -> 1
        }
        if ("thunder" in base) return 95
        val showers = "showers" in base
        if ("snow" in base || "sleet" in base) return if (showers) intArrayOf(85, 85, 86)[level] else intArrayOf(71, 73, 75)[level]
        if ("rain" in base) return if (showers) intArrayOf(80, 81, 82)[level] else intArrayOf(61, 63, 65)[level]
        return null
    }
}
