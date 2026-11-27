// RBSB-Weather sync format v1: names, versions and limits shared by the phone and the watch.
// Plain Kotlin, no Android dependencies. The full description is docs/SYNC_FORMAT.md; the
// language-neutral definition is sync/contract/*.schema.json.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

/** Data Layer paths, capability names and the format version. */
object SyncFormat {
    /** The schema version written in every payload. A watch rejects any other version. */
    const val SCHEMA = 1

    /** Data item, phone to watch: the latest weather snapshot. */
    const val PATH_SNAPSHOT = "/rbsb/v1/snapshot"
    /** Data item, phone to watch: the six display settings. */
    const val PATH_SETTINGS = "/rbsb/v1/settings"
    /** Message, watch to phone: "please fetch now". */
    const val PATH_REFRESH = "/rbsb/v1/refresh"
    /** Every RBSB path starts with this; anything else is ignored. */
    const val PATH_PREFIX = "/rbsb/"

    /** Capability the watch app declares, so the phone can find it. */
    const val CAPABILITY_WATCH = "rbsb_watch"
    /** Capability the phone app declares, so the watch can find it. */
    const val CAPABILITY_PHONE = "rbsb_phone"
    /** The protocol version, also declared as its own capability: "rbsb_watch_v1", "rbsb_phone_v1". */
    const val PROTOCOL = 1

    fun protocolCapability(base: String, protocol: Int = PROTOCOL): String = "${base}_v$protocol"

    const val KIND_SNAPSHOT = "rbsb.snapshot"
    const val KIND_SETTINGS = "rbsb.settings"
    const val KIND_REFRESH = "rbsb.refresh"

    /** True for a path this app should handle; every other path is ignored. */
    fun isRbsbPath(path: String?): Boolean = path != null && path.startsWith(PATH_PREFIX)
}

/** Size, count, length and time limits. Values are checked on decode and before encode. */
object Limits {
    /** A Data Layer item holds about 100 KB; a payload over this is never sent or accepted. */
    const val MAX_COMPRESSED_BYTES = 100_000
    /** The JSON after decompression may not exceed this (protects against a "zip bomb"). */
    const val MAX_JSON_BYTES = 262_144
    /** What a full snapshot is expected to stay under, compressed (the plan's target). */
    const val TARGET_COMPRESSED_BYTES = 20_000

    const val MAX_HOURS = 48
    const val MAX_DAYS = 10
    const val MAX_WARNINGS = 20

    /**
     * How far in the future (by the watch's clock) fetchedAt or sentAt may be. Phone and watch
     * both take their time from the network, so they normally agree within seconds; anything
     * later than this is treated as a bad clock or a bad payload and rejected.
     */
    const val FUTURE_TOLERANCE_MS = 10 * 60_000L

    /** Earliest and latest accepted timestamps (unix ms): 2020-01-01 and 2100-01-01 UTC. */
    const val EARLIEST_MS = 1_577_836_800_000L
    const val LATEST_MS = 4_102_444_800_000L

    // Text lengths, in characters (Unicode code points).
    const val PROVIDER = 40
    const val SOURCE_ID_MIN = 8
    const val SOURCE_ID_MAX = 64
    const val PLACE_NAME = 100
    const val COUNTRY = 100
    const val ADMIN1 = 100
    const val TIMEZONE = 64
    const val WARNING_ID = 200
    const val WARNING_TITLE = 120
    const val WARNING_AREA = 200
    const val WARNING_SUMMARY = 500
    const val WARNING_SOURCE = 60
    const val WARNING_HAZARD = 32
    const val MOON_PHASE_NAME = 40
}

/**
 * Accepted value ranges. All weather values are in the phone app's metric base units
 * (°C, km/h, mm, hPa, %); the watch converts them for display using the settings item.
 */
object Ranges {
    val LATITUDE = -90.0..90.0
    val LONGITUDE = -180.0..180.0
    val UTC_OFFSET_S = -64_800L..64_800L
    val TEMPERATURE = -90.0..60.0
    val APPARENT_TEMPERATURE = -100.0..80.0
    val PERCENT = 0.0..100.0
    val PRESSURE_HPA = 850.0..1100.0
    val PRECIPITATION_MM = 0.0..500.0
    val PRECIPITATION_SUM_MM = 0.0..2000.0
    val WIND_KMH = 0.0..500.0
    val GUST_KMH = 0.0..600.0
    val DIRECTION_DEG = 0.0..360.0
    val UV_INDEX = 0.0..25.0
    val WEATHER_CODE = 0L..99L
    val SEQUENCE = 1L..9_007_199_254_740_991L
    val TIME_MS = Limits.EARLIEST_MS..Limits.LATEST_MS
    val MOON_PHASE_DEG = 0.0..360.0
    val ILLUMINATION = 0.0..1.0
    val BRIGHT_LIMB_DEG = 0.0..360.0
    val TILT_DEG = -180.0..180.0
}

/** Helpers the phone uses to keep what it sends inside the limits. */
object SyncText {
    /** Shortens [s] to at most [max] characters (code points), ending with "…" if it was cut. */
    fun clip(s: String, max: Int): String {
        val t = s.trim()
        if (t.codePointCount(0, t.length) <= max) return t
        val end = t.offsetByCodePoints(0, max - 1)
        return t.substring(0, end).trimEnd() + "…"
    }

    /** Length in characters (code points), as the limits count it. */
    fun length(s: String): Int = s.codePointCount(0, s.length)
}

/** Coordinates are sent rounded to 3 decimals (about 100 m), never more precisely. */
object Coordinates {
    fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0

    /** True when [v] has at most 3 decimals (allowing for binary floating-point noise). */
    fun isRounded3(v: Double): Boolean {
        val scaled = v * 1000.0
        return kotlin.math.abs(scaled - Math.rint(scaled)) < 1e-6
    }
}
