// TEST-ONLY reference mapping from RBSB-Weather for Android 0.7.2's data to sync format v1.
// It shows how the phone (0.8.0, if approved) can fill a snapshot from what it already has, and
// lets the tests check the result. It is not phone code and changes nothing in the Android app.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync.phone072

import io.github.shanebl030glitch.rbsbweather.sync.Coordinates
import io.github.shanebl030glitch.rbsbweather.sync.Limits
import io.github.shanebl030glitch.rbsbweather.sync.MoonState
import io.github.shanebl030glitch.rbsbweather.sync.SnapCurrent
import io.github.shanebl030glitch.rbsbweather.sync.SnapDay
import io.github.shanebl030glitch.rbsbweather.sync.SnapHour
import io.github.shanebl030glitch.rbsbweather.sync.SnapPlace
import io.github.shanebl030glitch.rbsbweather.sync.SnapWarning
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SunTimes
import io.github.shanebl030glitch.rbsbweather.sync.SyncText
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel
import io.github.shanebl030glitch.rbsbweather.sync.Wind

object Phone072Mapper {
    /**
     * [shown] is what the phone's Watch.alertsFor() returns: the warnings it currently shows after
     * its own filters, including the user's own limits and excluding muted ones (Stage 1 decision 6).
     * [sun] and [moon] come from the phone's own Sun and Moon calculations.
     */
    fun toSnapshot(
        fc: Forecast, shown: List<Alert>, sun: SunTimes, moon: MoonState,
        sequence: Long, sourceId: String, sentAt: Long,
    ): Snapshot {
        val c = fc.current
        val currentHour = c.time.take(13) + ":00"            // "2026-11-28T10:15" -> "2026-11-28T10:00"
        return Snapshot(
            provider = SyncText.clip(fc.source, Limits.PROVIDER),
            fetchedAt = fc.fetchedAt,
            sentAt = sentAt,
            sequence = sequence,
            sourceId = sourceId,
            place = SnapPlace(
                name = SyncText.clip(fc.place.name, Limits.PLACE_NAME),
                latitude = Coordinates.round3(fc.place.latitude),
                longitude = Coordinates.round3(fc.place.longitude),
                timezone = fc.timezone,
                utcOffsetSeconds = fc.utcOffsetSeconds,
                country = SyncText.clip(fc.place.country, Limits.COUNTRY),
                admin1 = SyncText.clip(fc.place.admin1, Limits.ADMIN1),
            ),
            current = SnapCurrent(
                time = c.time, isDay = c.isDay, temperature = c.temperature,
                apparentTemperature = c.apparentTemperature, humidity = c.humidity, pressure = c.pressure,
                cloudCover = c.cloudCover, precipitation = c.precipitation, weatherCode = c.weatherCode,
                wind = Wind(c.windSpeed, c.windGusts, c.windDirection),
            ),
            // 0.7.2 has no hourly wind, so it is left out (the watch falls back to the current wind).
            hourly = fc.hourly.filter { it.time >= currentHour }.take(Limits.MAX_HOURS).map { h ->
                SnapHour(h.time, h.temperature, h.precipitationProbability, h.precipitation, h.weatherCode, wind = null)
            },
            daily = fc.daily.take(Limits.MAX_DAYS).map { d ->
                SnapDay(d.date, d.weatherCode, d.tempMax, d.tempMin, d.precipitationSum, d.precipitationProbabilityMax,
                    d.windSpeedMax, d.gustMax, d.uvIndexMax, d.sunrise, d.sunset)
            },
            warnings = shown.take(Limits.MAX_WARNINGS).map { a ->
                SnapWarning(
                    id = SyncText.clip(a.id, Limits.WARNING_ID),
                    level = WarningLevel.fromWire(a.level) ?: WarningLevel.MINOR,
                    title = SyncText.clip(a.title(), Limits.WARNING_TITLE),
                    official = a.official,
                    area = SyncText.clip(a.area, Limits.WARNING_AREA),
                    start = a.onset?.let { (it * 1000).toLong() },
                    end = a.expires?.let { (it * 1000).toLong() },
                    summary = SyncText.clip(a.description.ifBlank { a.headline }, Limits.WARNING_SUMMARY),
                    source = SyncText.clip(a.source, Limits.WARNING_SOURCE),
                    hazard = SyncText.clip(a.hazard, Limits.WARNING_HAZARD),
                )
            },
            sun = sun,
            moon = moon,
        )
    }
}
