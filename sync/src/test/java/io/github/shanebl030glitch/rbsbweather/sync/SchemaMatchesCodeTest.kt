// The language-neutral JSON Schemas (sync/contract) and the Kotlin decoder must enforce the same
// rules. This test walks every field of each schema and checks the decoder against it:
//   required fields  -> removing one gives MISSING_FIELD; optional fields -> removing one is fine
//   minimum/maximum  -> the bound itself is not OUT_OF_RANGE; just past it is OUT_OF_RANGE
//   maxLength        -> at the limit is fine; one more character is BAD_LENGTH (minLength 1: "" too)
//   maxItems         -> one more entry is TOO_MANY
//   enum             -> every listed name is accepted; anything else is BAD_VALUE
// So a change to either side that is not made to the other fails the build.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SchemaMatchesCodeTest {
    private val contract = File(SampleFiles.dir.parentFile, "contract")
    private var checks = 0
    private object Remove

    private fun schema(name: String) = JSONObject(File(contract, name).readText(Charsets.UTF_8))

    private fun types(node: JSONObject): Set<String> = when (val t = node.opt("type")) {
        is String -> setOf(t)
        is JSONArray -> (0 until t.length()).map { t.getString(it) }.toSet()
        else -> emptySet()
    }

    private fun navigate(root: JSONObject, path: List<Any>): JSONObject {
        var cur: Any = root
        for (p in path) cur = if (p is Int) (cur as JSONArray).get(p) else (cur as JSONObject).get(p as String)
        return cur as JSONObject
    }

    private fun mutated(root: JSONObject, path: List<Any>, key: String, value: Any): String {
        val copy = JSONObject(root.toString())
        val parent = navigate(copy, path)
        if (value === Remove) parent.remove(key) else parent.put(key, value)
        return copy.toString()
    }

    private fun reasonOf(d: Decoded<*>): Reason? = (d as? Decoded.Rejected)?.reason

    private fun expect(where: String, want: Reason?, d: Decoded<*>) {
        checks++
        assertEquals("$where -> $d", want, reasonOf(d))
    }

    private fun expectNot(where: String, notWant: Reason, d: Decoded<*>) {
        checks++
        assertNotEquals("$where -> $d", notWant, reasonOf(d))
    }

    private fun walk(node: JSONObject, root: JSONObject, path: List<Any>, parse: (String) -> Decoded<*>) {
        val props = node.optJSONObject("properties") ?: return
        val required = node.optJSONArray("required")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val parent = navigate(root, path)
        for (key in props.keySet().sorted()) {
            val p = props.getJSONObject(key)
            val where = (path + key).joinToString(".")
            fun set(v: Any) = parse(mutated(root, path, key, v))

            if (key in required) expect("$where removed", Reason.MISSING_FIELD, set(Remove))
            else expect("$where removed", null, set(Remove))

            if (p.has("minimum") || p.has("maximum")) {
                val integer = "integer" in types(p)
                if (p.has("maximum")) {
                    if (integer) {
                        expectNot("$where = max", Reason.OUT_OF_RANGE, set(p.getLong("maximum")))
                        expect("$where = max + 1", Reason.OUT_OF_RANGE, set(p.getLong("maximum") + 1))
                    } else {
                        expectNot("$where = max", Reason.OUT_OF_RANGE, set(p.getDouble("maximum")))
                        expect("$where = max + 0.5", Reason.OUT_OF_RANGE, set(p.getDouble("maximum") + 0.5))
                    }
                }
                if (p.has("minimum")) {
                    if (integer) {
                        expectNot("$where = min", Reason.OUT_OF_RANGE, set(p.getLong("minimum")))
                        expect("$where = min - 1", Reason.OUT_OF_RANGE, set(p.getLong("minimum") - 1))
                    } else {
                        expectNot("$where = min", Reason.OUT_OF_RANGE, set(p.getDouble("minimum")))
                        expect("$where = min - 0.5", Reason.OUT_OF_RANGE, set(p.getDouble("minimum") - 0.5))
                    }
                }
            }
            if (p.has("maxLength")) {
                val max = p.getInt("maxLength")
                expectNot("$where length max", Reason.BAD_LENGTH, set("x".repeat(max)))
                expect("$where length max + 1", Reason.BAD_LENGTH, set("x".repeat(max + 1)))
            }
            if (p.optInt("minLength", 0) >= 1) expect("$where empty", Reason.BAD_LENGTH, set(""))
            if (p.has("enum")) {
                val names = p.getJSONArray("enum")
                for (i in 0 until names.length()) expect("$where = ${names.get(i)}", null, set(names.get(i)))
                expect("$where = zzz", Reason.BAD_VALUE, set("zzz"))
            }
            val child = parent.opt(key)
            if (p.has("maxItems")) {
                val arr = child as JSONArray
                assertTrue("$where needs entries in the sample", arr.length() > 0)
                val padded = JSONArray(arr.toString())
                while (padded.length() <= p.getInt("maxItems")) padded.put(JSONObject(arr.getJSONObject(arr.length() - 1).toString()))
                expect("$where with ${padded.length()} entries", Reason.TOO_MANY, set(padded))
            }
            if (child is JSONObject && "object" in types(p)) walk(p, root, path + key, parse)
            if (child is JSONArray && p.has("items") && child.length() > 0) walk(p.getJSONObject("items"), root, path + key + 0, parse)
        }
    }

    @Test fun snapshotSchemaAndDecoderAgreeOnEveryField() {
        // The largest sample has every list filled, hourly wind, warnings and Moon times.
        val root = JSONObject(SnapshotCodec.toJsonText(SampleFiles.snapshot("largest")))
        walk(schema("snapshot-v1.schema.json"), root, emptyList()) { SnapshotCodec.parse(it, nowMs = null) }
        println("SCHEMA MATCH snapshot: $checks checks")
        assertTrue("checked $checks rules", checks > 250)
    }

    @Test fun settingsSchemaAndDecoderAgreeOnEveryField() {
        val root = SettingsCodec.toJson(SyncSettings(sentAt = SampleFiles.now))
        walk(schema("settings-v1.schema.json"), root, emptyList()) { SettingsCodec.parse(it, nowMs = null) }
        println("SCHEMA MATCH settings: $checks checks")
        assertTrue(checks > 25)
    }

    @Test fun refreshSchemaAndDecoderAgreeOnEveryField() {
        val root = RefreshCodec.toJson(RefreshRequest(sentAt = SampleFiles.now, haveFetchedAt = SampleFiles.now - 3600_000L))
        walk(schema("refresh-v1.schema.json"), root, emptyList()) { RefreshCodec.parse(it, nowMs = null) }
        println("SCHEMA MATCH refresh: $checks checks")
        assertTrue(checks > 8)
    }

    @Test fun schemaConstantsMatchTheCode() {
        val s = schema("snapshot-v1.schema.json").getJSONObject("properties")
        assertEquals(SyncFormat.KIND_SNAPSHOT, s.getJSONObject("kind").getString("const"))
        assertEquals(SyncFormat.SCHEMA, s.getJSONObject("schema").getInt("const"))
        assertEquals(SyncFormat.KIND_SETTINGS, schema("settings-v1.schema.json").getJSONObject("properties").getJSONObject("kind").getString("const"))
        assertEquals(SyncFormat.KIND_REFRESH, schema("refresh-v1.schema.json").getJSONObject("properties").getJSONObject("kind").getString("const"))
        assertEquals(Limits.MAX_HOURS, s.getJSONObject("hourly").getInt("maxItems"))
        assertEquals(Limits.MAX_DAYS, s.getJSONObject("daily").getInt("maxItems"))
        assertEquals(Limits.MAX_WARNINGS, s.getJSONObject("warnings").getInt("maxItems"))
        assertEquals(SampleFiles.expected.getLong("toleranceMs"), Limits.FUTURE_TOLERANCE_MS)
    }
}
