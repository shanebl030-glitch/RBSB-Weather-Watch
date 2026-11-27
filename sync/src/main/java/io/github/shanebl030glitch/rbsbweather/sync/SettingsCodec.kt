// RBSB-Weather sync format v1: writing and checking the settings item and the refresh request.
// Same rules as the snapshot: encode() refuses bad output, decode() never throws.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONObject

object SettingsCodec {
    fun encode(s: SyncSettings): ByteArray {
        val text = toJson(s).toString()
        val back = parse(text, nowMs = null)
        require(back is Decoded.Ok) { "settings break sync format v1: $back" }
        return PayloadGzip.compress(text)
    }

    fun decode(bytes: ByteArray, nowMs: Long): Decoded<SyncSettings> =
        when (val text = PayloadGzip.decompress(bytes)) {
            is Decoded.Rejected -> text
            is Decoded.Ok -> parse(text.value, nowMs)
        }

    fun parse(text: String, nowMs: Long?): Decoded<SyncSettings> = SyncJson.decode(text) { o ->
        SyncJson.header(o, SyncFormat.KIND_SETTINGS)
        val sentAt = o.long("sentAt", Ranges.TIME_MS)
        val s = SyncSettings(
            sentAt = sentAt,
            temperatureUnit = enumValue(o, "temperatureUnit", TemperatureUnit.values()),
            windUnit = enumValue(o, "windUnit", WindUnit.values()),
            precipitationUnit = enumValue(o, "precipitationUnit", PrecipitationUnit.values()),
            clock = enumValue(o, "clock", ClockFormat.values()),
            animations = o.bool("animations"),
            minWarningLevel = o.string("minWarningLevel", Int.MAX_VALUE, 0).let {
                MinWarningLevel.fromWire(it) ?: throw Invalid(Reason.BAD_VALUE, o.p("minWarningLevel"), "unknown level $it")
            },
        )
        SyncJson.futureCheck("sentAt", sentAt, nowMs)
        s
    }

    fun toJson(s: SyncSettings): JSONObject = JSONObject()
        .put("kind", SyncFormat.KIND_SETTINGS)
        .put("schema", SyncFormat.SCHEMA)
        .put("sentAt", s.sentAt)
        .put("temperatureUnit", s.temperatureUnit.name)
        .put("windUnit", s.windUnit.name)
        .put("precipitationUnit", s.precipitationUnit.name)
        .put("clock", s.clock.name)
        .put("animations", s.animations)
        .put("minWarningLevel", s.minWarningLevel.wire)

    private fun <E : Enum<E>> enumValue(o: In, k: String, values: Array<E>): E {
        val text = o.string(k, Int.MAX_VALUE, 0)
        return values.firstOrNull { it.name == text } ?: throw Invalid(Reason.BAD_VALUE, o.p(k), "unknown value $text")
    }
}

object RefreshCodec {
    fun encode(r: RefreshRequest): ByteArray {
        val text = toJson(r).toString()
        val back = parse(text, nowMs = null)
        require(back is Decoded.Ok) { "refresh request breaks sync format v1: $back" }
        return PayloadGzip.compress(text)
    }

    fun decode(bytes: ByteArray, nowMs: Long): Decoded<RefreshRequest> =
        when (val text = PayloadGzip.decompress(bytes)) {
            is Decoded.Rejected -> text
            is Decoded.Ok -> parse(text.value, nowMs)
        }

    fun parse(text: String, nowMs: Long?): Decoded<RefreshRequest> = SyncJson.decode(text) { o ->
        SyncJson.header(o, SyncFormat.KIND_REFRESH)
        val r = RefreshRequest(o.long("sentAt", Ranges.TIME_MS), o.nullableLong("haveFetchedAt", Ranges.TIME_MS))
        SyncJson.futureCheck("sentAt", r.sentAt, nowMs)
        r
    }

    fun toJson(r: RefreshRequest): JSONObject = JSONObject()
        .put("kind", SyncFormat.KIND_REFRESH)
        .put("schema", SyncFormat.SCHEMA)
        .put("sentAt", r.sentAt)
        .putOpt("haveFetchedAt", r.haveFetchedAt)
}
