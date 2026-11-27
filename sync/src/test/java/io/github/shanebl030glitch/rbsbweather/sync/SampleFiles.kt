// Test helper: the shared sample files (also checked by scripts/check-samples.py).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONObject
import java.io.File

object SampleFiles {
    val dir: File = File(System.getProperty("rbsb.samples") ?: "samples").absoluteFile

    fun text(rel: String): String = File(dir, rel).readText(Charsets.UTF_8)

    val expected: JSONObject by lazy { JSONObject(text("expected.json")) }

    /** The clock the samples were made for (both checkers use it). */
    val now: Long get() = expected.getLong("now")

    fun valid(kind: String): List<String> {
        val a = expected.getJSONObject(kind).getJSONArray("valid")
        return (0 until a.length()).map { a.getString(it) }
    }

    fun invalid(kind: String): Map<String, Reason> {
        val o = expected.getJSONObject(kind).getJSONObject("invalid")
        return o.keySet().sorted().associateWith { Reason.valueOf(o.getString(it)) }
    }

    fun onDisk(kind: String): Set<String> =
        File(dir, kind).walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    /** A valid snapshot to make variants from in tests. */
    fun snapshot(name: String = "clear-day"): Snapshot =
        (SnapshotCodec.parse(text("snapshot/valid/$name.json"), now) as Decoded.Ok).value
}
