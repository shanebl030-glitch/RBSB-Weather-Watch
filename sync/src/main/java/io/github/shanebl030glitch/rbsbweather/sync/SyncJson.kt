// RBSB-Weather sync format v1: strict reading of untrusted payloads, and compression.
// Every payload from the other device is treated as untrusted: decoding never throws, and a bad
// payload comes back as Decoded.Rejected with a reason, so the caller can keep its cached copy.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Why a payload was rejected. The same names are used by scripts/check-samples.py. */
enum class Reason {
    /** Compressed payload over 100 KB, or over 256 KB once decompressed. */
    TOO_LARGE,
    /** Not valid gzip, or not valid UTF-8 text. */
    CORRUPT,
    /** Not a single JSON object. */
    BAD_JSON,
    /** The "kind" field names a different payload. */
    WRONG_KIND,
    /** A schema version this watch does not understand. */
    UNSUPPORTED_SCHEMA,
    MISSING_FIELD,
    /** A value of the wrong JSON type (text where a number belongs, null where a value is required). */
    WRONG_TYPE,
    OUT_OF_RANGE,
    /** Text too long, or empty where text is required. */
    BAD_LENGTH,
    /** More hours, days or warnings than allowed. */
    TOO_MANY,
    /** A time, date, id or time zone in the wrong format. */
    BAD_FORMAT,
    /** A name outside its fixed list (warning level, unit). */
    BAD_VALUE,
    /** Coordinates with more than 3 decimals. */
    UNROUNDED_COORDINATES,
    /** Values that contradict each other (sent before fetched, end before start, out of order). */
    INCONSISTENT,
    /** fetchedAt or sentAt later than the watch's clock plus the tolerance. */
    FUTURE_DATED,
    /** Should never happen; reported instead of crashing. */
    UNEXPECTED,
}

sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>
    data class Rejected(val reason: Reason, val path: String, val detail: String) : Decoded<Nothing>
}

/** Thrown inside the readers only; always turned into Decoded.Rejected before it leaves. */
internal class Invalid(val reason: Reason, val path: String, detail: String) :
    Exception(detail, null, false, false)

/** gzip and size limits for every payload. */
object PayloadGzip {
    fun compress(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decompress(bytes: ByteArray): Decoded<String> {
        if (bytes.size > Limits.MAX_COMPRESSED_BYTES)
            return Decoded.Rejected(Reason.TOO_LARGE, "", "${bytes.size} bytes compressed (limit ${Limits.MAX_COMPRESSED_BYTES})")
        if (bytes.isEmpty()) return Decoded.Rejected(Reason.CORRUPT, "", "empty payload")
        val out = ByteArrayOutputStream()
        try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { zin ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = zin.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > Limits.MAX_JSON_BYTES)
                        return Decoded.Rejected(Reason.TOO_LARGE, "", "over ${Limits.MAX_JSON_BYTES} bytes once decompressed")
                }
            }
        } catch (e: IOException) {                     // ZipException and EOFException are IOExceptions
            return Decoded.Rejected(Reason.CORRUPT, "", "not valid gzip: ${e.message}")
        }
        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(out.toByteArray())).toString()
            Decoded.Ok(text)
        } catch (e: CharacterCodingException) {
            Decoded.Rejected(Reason.CORRUPT, "", "not valid UTF-8")
        }
    }
}

internal object SyncJson {
    private val LOCAL_TIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$""")
    private val LOCAL_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val TIMEZONE = Regex("""^[A-Za-z0-9_+\-/]{1,64}$""")
    private val SOURCE_ID = Regex("""^[A-Za-z0-9-]{8,64}$""")

    /**
     * Parses [text] as exactly one JSON object and runs [read] on it. Every failure, including
     * ones that should be impossible, becomes Decoded.Rejected.
     */
    fun <T> decode(text: String, read: (In) -> T): Decoded<T> = try {
        val tok = JSONTokener(text)
        val root = try { tok.nextValue() } catch (e: JSONException) { throw Invalid(Reason.BAD_JSON, "", "not JSON: ${e.message}") }
        if (root !is JSONObject) throw Invalid(Reason.BAD_JSON, "", "not a JSON object")
        if (tok.nextClean() != 0.toChar()) throw Invalid(Reason.BAD_JSON, "", "text after the JSON object")
        Decoded.Ok(read(In(root, "")))
    } catch (e: Invalid) {
        Decoded.Rejected(e.reason, e.path, e.message ?: "")
    } catch (e: StackOverflowError) {                 // absurdly deep nesting
        Decoded.Rejected(Reason.BAD_JSON, "", "nested too deeply")
    } catch (e: JSONException) {
        Decoded.Rejected(Reason.BAD_JSON, "", e.message ?: "bad JSON")
    } catch (e: RuntimeException) {
        Decoded.Rejected(Reason.UNEXPECTED, "", e.toString())
    }

    fun checkLocalTime(path: String, s: String): String {
        if (!LOCAL_TIME.matches(s)) throw Invalid(Reason.BAD_FORMAT, path, "expected local time like 2026-11-28T14:00")
        try { LocalDateTime.parse(s) } catch (e: Exception) { throw Invalid(Reason.BAD_FORMAT, path, "not a real time: $s") }
        return s
    }

    fun checkLocalDate(path: String, s: String): String {
        if (!LOCAL_DATE.matches(s)) throw Invalid(Reason.BAD_FORMAT, path, "expected a date like 2026-11-28")
        try { LocalDate.parse(s) } catch (e: Exception) { throw Invalid(Reason.BAD_FORMAT, path, "not a real date: $s") }
        return s
    }

    /** Wrong characters: BAD_FORMAT. Well-formed but not a real IANA time zone (e.g. "auto"): BAD_VALUE. */
    fun checkTimezone(path: String, s: String): String {
        if (!TIMEZONE.matches(s)) throw Invalid(Reason.BAD_FORMAT, path, "not a time zone name")
        if (!TimeZones.isValid(s)) throw Invalid(Reason.BAD_VALUE, path, "not a known IANA time zone: $s")
        return s
    }

    fun checkSourceId(path: String, s: String): String {
        if (!SOURCE_ID.matches(s)) throw Invalid(Reason.BAD_FORMAT, path, "sourceId must be 8 to 64 letters, digits or hyphens")
        return s
    }

    /** The kind and schema fields every payload starts with. */
    fun header(o: In, kind: String) {
        val k = o.string("kind", Int.MAX_VALUE, 0)
        if (k != kind) throw Invalid(Reason.WRONG_KIND, "kind", "expected $kind, got $k")
        val schema = o.long("schema", Long.MIN_VALUE..Long.MAX_VALUE)
        if (schema != SyncFormat.SCHEMA.toLong()) throw Invalid(Reason.UNSUPPORTED_SCHEMA, "schema", "schema $schema (this app reads ${SyncFormat.SCHEMA})")
    }

    fun futureCheck(path: String, t: Long, nowMs: Long?) {
        if (nowMs != null && t > nowMs + Limits.FUTURE_TOLERANCE_MS)
            throw Invalid(Reason.FUTURE_DATED, path, "${(t - nowMs) / 1000} s ahead of this device's clock")
    }
}

/** A JSON object being read, with its path for error messages ("hourly[3].temperature"). */
internal class In(private val o: JSONObject, private val path: String) {
    fun p(k: String) = if (path.isEmpty()) k else "$path.$k"

    private fun required(k: String): Any {
        if (!o.has(k)) throw Invalid(Reason.MISSING_FIELD, p(k), "missing")
        if (o.isNull(k)) throw Invalid(Reason.WRONG_TYPE, p(k), "null where a value is required")
        return o.get(k)
    }

    private fun optional(k: String): Any? = if (!o.has(k) || o.isNull(k)) null else o.get(k)

    private fun wrongType(k: String, want: String) = Invalid(Reason.WRONG_TYPE, p(k), "expected $want")

    private fun length(k: String, s: String, min: Int, max: Int): String {
        val n = SyncText.length(s)
        if (n < min || n > max) throw Invalid(Reason.BAD_LENGTH, p(k), "length $n (allowed $min to $max)")
        return s
    }

    private fun asDouble(k: String, v: Any): Double {
        if (v !is Number) throw wrongType(k, "a number")
        val d = v.toDouble()
        if (d.isNaN() || d.isInfinite()) throw Invalid(Reason.OUT_OF_RANGE, p(k), "not a finite number")
        return d
    }

    private fun asLong(k: String, v: Any): Long {
        if (v is Int || v is Long || v is Short || v is Byte) return (v as Number).toLong()
        if (v is BigInteger) {
            if (v.bitLength() > 63) throw Invalid(Reason.OUT_OF_RANGE, p(k), "number too large")
            return v.toLong()
        }
        val d = asDouble(k, v)
        if (d != Math.rint(d)) throw wrongType(k, "a whole number")
        if (d < -9.0e18 || d > 9.0e18) throw Invalid(Reason.OUT_OF_RANGE, p(k), "number too large")
        return d.toLong()
    }

    private fun inRange(k: String, d: Double, r: ClosedFloatingPointRange<Double>): Double {
        if (d !in r) throw Invalid(Reason.OUT_OF_RANGE, p(k), "$d outside ${r.start} to ${r.endInclusive}")
        return d
    }

    private fun inRange(k: String, n: Long, r: LongRange): Long {
        if (n !in r) throw Invalid(Reason.OUT_OF_RANGE, p(k), "$n outside ${r.first} to ${r.last}")
        return n
    }

    fun string(k: String, max: Int, min: Int = 1): String =
        length(k, required(k) as? String ?: throw wrongType(k, "text"), min, max)

    fun stringOr(k: String, max: Int, default: String): String {
        if (!o.has(k)) return default
        if (o.isNull(k)) throw wrongType(k, "text")
        return length(k, o.get(k) as? String ?: throw wrongType(k, "text"), 0, max)
    }

    fun nullableString(k: String): String? {
        val v = optional(k) ?: return null
        return v as? String ?: throw wrongType(k, "text or null")
    }

    fun bool(k: String): Boolean = required(k) as? Boolean ?: throw wrongType(k, "true or false")

    fun boolOr(k: String, default: Boolean): Boolean {
        if (!o.has(k)) return default
        if (o.isNull(k)) throw wrongType(k, "true or false")
        return o.get(k) as? Boolean ?: throw wrongType(k, "true or false")
    }

    fun long(k: String, r: LongRange): Long = inRange(k, asLong(k, required(k)), r)
    fun nullableLong(k: String, r: LongRange): Long? = optional(k)?.let { inRange(k, asLong(k, it), r) }
    fun nullableInt(k: String, r: LongRange): Int? = nullableLong(k, r)?.toInt()
    fun num(k: String, r: ClosedFloatingPointRange<Double>): Double = inRange(k, asDouble(k, required(k)), r)
    fun nullableNum(k: String, r: ClosedFloatingPointRange<Double>): Double? = optional(k)?.let { inRange(k, asDouble(k, it), r) }

    fun obj(k: String): In = In(required(k) as? JSONObject ?: throw wrongType(k, "an object"), p(k))
    fun nullableObj(k: String): In? = optional(k)?.let { In(it as? JSONObject ?: throw wrongType(k, "an object or null"), p(k)) }

    /** A required array of objects with at most [max] entries. */
    fun objects(k: String, max: Int): List<In> {
        val a = required(k) as? JSONArray ?: throw wrongType(k, "a list")
        if (a.length() > max) throw Invalid(Reason.TOO_MANY, p(k), "${a.length()} entries (limit $max)")
        return (0 until a.length()).map { i ->
            val v = a.get(i)
            In(v as? JSONObject ?: throw Invalid(Reason.WRONG_TYPE, "${p(k)}[$i]", "expected an object"), "${p(k)}[$i]")
        }
    }
}
