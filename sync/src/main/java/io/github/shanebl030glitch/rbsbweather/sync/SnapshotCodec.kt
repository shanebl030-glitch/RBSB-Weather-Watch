// RBSB-Weather sync format v1: writing and checking the weather snapshot.
// The phone calls encode(), which refuses to produce anything the watch would reject. The watch
// calls decode(), which checks the size, schema version, every value's type and range, and the
// consistency rules, and rejects the whole snapshot (keeping its cached copy) if anything fails.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object SnapshotCodec {

    /** Compressed bytes for the Data Layer. Throws IllegalArgumentException if [s] breaks the format. */
    fun encode(s: Snapshot): ByteArray {
        val text = toJsonText(s)
        when (val back = parse(text, nowMs = null)) {
            is Decoded.Rejected -> throw IllegalArgumentException("snapshot breaks sync format v1: ${back.reason} at ${back.path}: ${back.detail}")
            is Decoded.Ok -> Unit
        }
        val bytes = PayloadGzip.compress(text)
        require(bytes.size <= Limits.MAX_COMPRESSED_BYTES) { "snapshot is ${bytes.size} bytes compressed (limit ${Limits.MAX_COMPRESSED_BYTES})" }
        return bytes
    }

    /** Checks and reads a received payload. Never throws. [nowMs] is this device's clock. */
    fun decode(bytes: ByteArray, nowMs: Long): Decoded<Snapshot> =
        when (val text = PayloadGzip.decompress(bytes)) {
            is Decoded.Rejected -> text
            is Decoded.Ok -> parse(text.value, nowMs)
        }

    /** Checks and reads snapshot JSON text. With [nowMs] null the future-date check is skipped. */
    fun parse(text: String, nowMs: Long?): Decoded<Snapshot> = SyncJson.decode(text) { read(it, nowMs) }

    fun toJsonText(s: Snapshot): String = try {
        toJson(s).toString()
    } catch (e: JSONException) {
        throw IllegalArgumentException("snapshot cannot be written as JSON: ${e.message}")
    }

    // ------------------------------------------------------------------------------- writing

    fun toJson(s: Snapshot): JSONObject = JSONObject()
        .put("kind", SyncFormat.KIND_SNAPSHOT)
        .put("schema", SyncFormat.SCHEMA)
        .put("provider", s.provider)
        .put("fetchedAt", s.fetchedAt)
        .put("sentAt", s.sentAt)
        .put("sequence", s.sequence)
        .put("sourceId", s.sourceId)
        .put("place", JSONObject()
            .put("name", s.place.name).put("latitude", s.place.latitude).put("longitude", s.place.longitude)
            .put("timezone", s.place.timezone).put("utcOffsetSeconds", s.place.utcOffsetSeconds)
            .apply { if (s.place.country.isNotEmpty()) put("country", s.place.country) }
            .apply { if (s.place.admin1.isNotEmpty()) put("admin1", s.place.admin1) })
        .put("current", s.current.let { c ->
            JSONObject().put("time", c.time).put("isDay", c.isDay)
                .putOpt("temperature", c.temperature).putOpt("apparentTemperature", c.apparentTemperature)
                .putOpt("humidity", c.humidity).putOpt("pressure", c.pressure).putOpt("cloudCover", c.cloudCover)
                .putOpt("precipitation", c.precipitation).putOpt("weatherCode", c.weatherCode)
                .putOpt("wind", windJson(c.wind))
        })
        .put("hourly", JSONArray(s.hourly.map { h ->
            JSONObject().put("time", h.time).putOpt("temperature", h.temperature)
                .putOpt("precipitationProbability", h.precipitationProbability).putOpt("precipitation", h.precipitation)
                .putOpt("weatherCode", h.weatherCode).putOpt("wind", h.wind?.let { windJson(it) })
        }))
        .put("daily", JSONArray(s.daily.map { d ->
            JSONObject().put("date", d.date).putOpt("weatherCode", d.weatherCode)
                .putOpt("tempMax", d.tempMax).putOpt("tempMin", d.tempMin).putOpt("precipitationSum", d.precipitationSum)
                .putOpt("precipitationProbabilityMax", d.precipitationProbabilityMax).putOpt("windSpeedMax", d.windSpeedMax)
                .putOpt("gustMax", d.gustMax).putOpt("uvIndexMax", d.uvIndexMax)
                .putOpt("sunrise", d.sunrise).putOpt("sunset", d.sunset)
        }))
        .put("warnings", JSONArray(s.warnings.map { w ->
            JSONObject().put("id", w.id).put("level", w.level.wire).put("title", w.title).put("official", w.official)
                .put("area", w.area).putOpt("start", w.start).putOpt("end", w.end).put("summary", w.summary)
                .put("source", w.source).put("hazard", w.hazard)
        }))
        .put("sun", JSONObject().putOpt("rise", s.sun.rise).putOpt("set", s.sun.set))
        .put("moon", s.moon.let { m ->
            JSONObject().put("at", m.at).put("phase", m.phase).put("phaseName", m.phaseName)
                .put("illumination", m.illumination).put("brightLimb", m.brightLimb).put("tilt", m.tilt)
                .putOpt("rise", m.rise).putOpt("set", m.set)
                .put("alwaysUp", m.alwaysUp).put("alwaysDown", m.alwaysDown)
        })

    private fun windJson(w: Wind): JSONObject? = if (!w.hasAny) null else
        JSONObject().putOpt("speed", w.speed).putOpt("gusts", w.gusts).putOpt("direction", w.direction)

    // ------------------------------------------------------------------------------- reading

    private fun read(o: In, nowMs: Long?): Snapshot {
        SyncJson.header(o, SyncFormat.KIND_SNAPSHOT)
        val provider = o.string("provider", Limits.PROVIDER)
        val fetchedAt = o.long("fetchedAt", Ranges.TIME_MS)
        val sentAt = o.long("sentAt", Ranges.TIME_MS)
        val sequence = o.long("sequence", Ranges.SEQUENCE)
        val sourceId = SyncJson.checkSourceId(o.p("sourceId"), o.string("sourceId", Int.MAX_VALUE, 0))
        val place = readPlace(o.obj("place"))
        val current = readCurrent(o.obj("current"))
        val hourly = o.objects("hourly", Limits.MAX_HOURS).map { readHour(it) }
        val daily = o.objects("daily", Limits.MAX_DAYS).map { readDay(it) }
        val warnings = o.objects("warnings", Limits.MAX_WARNINGS).map { readWarning(it) }
        val sun = o.obj("sun").let { SunTimes(it.nullableLong("rise", Ranges.TIME_MS), it.nullableLong("set", Ranges.TIME_MS)) }
        val moon = readMoon(o.obj("moon"))

        // Consistency rules.
        if (sentAt < fetchedAt) throw Invalid(Reason.INCONSISTENT, "sentAt", "sent before it was fetched")
        for (i in 1 until hourly.size)
            if (hourly[i].time <= hourly[i - 1].time) throw Invalid(Reason.INCONSISTENT, "hourly[$i].time", "hours out of order or repeated")
        for (i in 1 until daily.size)
            if (daily[i].date <= daily[i - 1].date) throw Invalid(Reason.INCONSISTENT, "daily[$i].date", "days out of order or repeated")
        SyncJson.futureCheck("fetchedAt", fetchedAt, nowMs)
        SyncJson.futureCheck("sentAt", sentAt, nowMs)

        return Snapshot(provider, fetchedAt, sentAt, sequence, sourceId, place, current, hourly, daily, warnings, sun, moon)
    }

    private fun readPlace(o: In): SnapPlace {
        val name = o.string("name", Limits.PLACE_NAME)
        val lat = o.num("latitude", Ranges.LATITUDE)
        val lon = o.num("longitude", Ranges.LONGITUDE)
        if (!Coordinates.isRounded3(lat)) throw Invalid(Reason.UNROUNDED_COORDINATES, o.p("latitude"), "more than 3 decimals")
        if (!Coordinates.isRounded3(lon)) throw Invalid(Reason.UNROUNDED_COORDINATES, o.p("longitude"), "more than 3 decimals")
        val tz = SyncJson.checkTimezone(o.p("timezone"), o.string("timezone", Limits.TIMEZONE))
        val offset = o.long("utcOffsetSeconds", Ranges.UTC_OFFSET_S).toInt()
        return SnapPlace(name, lat, lon, tz, offset, o.stringOr("country", Limits.COUNTRY, ""), o.stringOr("admin1", Limits.ADMIN1, ""))
    }

    /** An empty wind object ({} or all values null) reads the same as no wind. */
    private fun readWind(o: In?): Wind? = o?.let {
        Wind(it.nullableNum("speed", Ranges.WIND_KMH), it.nullableNum("gusts", Ranges.GUST_KMH), it.nullableNum("direction", Ranges.DIRECTION_DEG))
    }?.takeIf { it.hasAny }

    private fun readCurrent(o: In) = SnapCurrent(
        time = SyncJson.checkLocalTime(o.p("time"), o.string("time", Int.MAX_VALUE, 0)),
        isDay = o.bool("isDay"),
        temperature = o.nullableNum("temperature", Ranges.TEMPERATURE),
        apparentTemperature = o.nullableNum("apparentTemperature", Ranges.APPARENT_TEMPERATURE),
        humidity = o.nullableNum("humidity", Ranges.PERCENT),
        pressure = o.nullableNum("pressure", Ranges.PRESSURE_HPA),
        cloudCover = o.nullableNum("cloudCover", Ranges.PERCENT),
        precipitation = o.nullableNum("precipitation", Ranges.PRECIPITATION_MM),
        weatherCode = o.nullableInt("weatherCode", Ranges.WEATHER_CODE),
        wind = readWind(o.nullableObj("wind")) ?: Wind.NONE,
    )

    private fun readHour(o: In) = SnapHour(
        time = SyncJson.checkLocalTime(o.p("time"), o.string("time", Int.MAX_VALUE, 0)),
        temperature = o.nullableNum("temperature", Ranges.TEMPERATURE),
        precipitationProbability = o.nullableNum("precipitationProbability", Ranges.PERCENT),
        precipitation = o.nullableNum("precipitation", Ranges.PRECIPITATION_MM),
        weatherCode = o.nullableInt("weatherCode", Ranges.WEATHER_CODE),
        wind = readWind(o.nullableObj("wind")),
    )

    private fun readDay(o: In): SnapDay {
        val d = SnapDay(
            date = SyncJson.checkLocalDate(o.p("date"), o.string("date", Int.MAX_VALUE, 0)),
            weatherCode = o.nullableInt("weatherCode", Ranges.WEATHER_CODE),
            tempMax = o.nullableNum("tempMax", Ranges.TEMPERATURE),
            tempMin = o.nullableNum("tempMin", Ranges.TEMPERATURE),
            precipitationSum = o.nullableNum("precipitationSum", Ranges.PRECIPITATION_SUM_MM),
            precipitationProbabilityMax = o.nullableNum("precipitationProbabilityMax", Ranges.PERCENT),
            windSpeedMax = o.nullableNum("windSpeedMax", Ranges.WIND_KMH),
            gustMax = o.nullableNum("gustMax", Ranges.GUST_KMH),
            uvIndexMax = o.nullableNum("uvIndexMax", Ranges.UV_INDEX),
            sunrise = o.nullableString("sunrise")?.let { SyncJson.checkLocalTime(o.p("sunrise"), it) },
            sunset = o.nullableString("sunset")?.let { SyncJson.checkLocalTime(o.p("sunset"), it) },
        )
        if (d.tempMax != null && d.tempMin != null && d.tempMax < d.tempMin)
            throw Invalid(Reason.INCONSISTENT, o.p("tempMax"), "maximum below minimum")
        return d
    }

    private fun readWarning(o: In): SnapWarning {
        val id = o.string("id", Limits.WARNING_ID)
        val levelText = o.string("level", Int.MAX_VALUE, 0)
        val level = WarningLevel.fromWire(levelText) ?: throw Invalid(Reason.BAD_VALUE, o.p("level"), "unknown level $levelText")
        val w = SnapWarning(
            id = id,
            level = level,
            title = o.string("title", Limits.WARNING_TITLE),
            official = o.bool("official"),
            area = o.stringOr("area", Limits.WARNING_AREA, ""),
            start = o.nullableLong("start", Ranges.TIME_MS),
            end = o.nullableLong("end", Ranges.TIME_MS),
            summary = o.stringOr("summary", Limits.WARNING_SUMMARY, ""),
            source = o.stringOr("source", Limits.WARNING_SOURCE, ""),
            hazard = o.stringOr("hazard", Limits.WARNING_HAZARD, "other"),
        )
        if (w.start != null && w.end != null && w.end < w.start) throw Invalid(Reason.INCONSISTENT, o.p("end"), "ends before it starts")
        return w
    }

    private fun readMoon(o: In): MoonState {
        val m = MoonState(
            at = o.long("at", Ranges.TIME_MS),
            phase = o.num("phase", Ranges.MOON_PHASE_DEG),
            phaseName = o.string("phaseName", Limits.MOON_PHASE_NAME),
            illumination = o.num("illumination", Ranges.ILLUMINATION),
            brightLimb = o.num("brightLimb", Ranges.BRIGHT_LIMB_DEG),
            tilt = o.num("tilt", Ranges.TILT_DEG),
            rise = o.nullableLong("rise", Ranges.TIME_MS),
            set = o.nullableLong("set", Ranges.TIME_MS),
            alwaysUp = o.boolOr("alwaysUp", false),
            alwaysDown = o.boolOr("alwaysDown", false),
        )
        if (m.alwaysUp && m.alwaysDown) throw Invalid(Reason.INCONSISTENT, o.p("alwaysUp"), "cannot be always up and always down")
        return m
    }
}
