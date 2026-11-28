// Stage 2 sample data: the Stage 1 valid snapshots (sync/samples/snapshot/valid), copied into the
// app's assets at build time, decoded with the shared sync module exactly as real phone data will
// be in Stage 3. Shown in debug and release builds until Stage 3, always labelled "Sample data"
// (approved Stage 2 decision 2). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import io.github.shanebl030glitch.rbsbweather.sync.Decoded
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SnapshotCodec

data class Sample(val name: String, val snapshot: Snapshot)

object SampleData {
    const val ASSET_DIR = "samples"

    /** The weather samples first, in a sensible viewing order; any others follow by name. */
    val PREFERRED_ORDER = listOf(
        "clear-day", "partly-cloudy-day", "overcast", "drizzle", "rain-night-warnings", "thunder-warning",
        "fog", "snow-northern", "clear-night", "polar-night", "hourly-wind-mixed", "utc-timezone",
        "times-with-seconds", "future-within-tolerance", "unknown-fields", "explicit-nulls", "minimal", "largest",
    )

    /**
     * Decodes sample files (name to JSON text). Samples carry fixed 2026 dates, so the future-date
     * check is skipped (nowMs = null); every other sync format check applies. A sample that fails
     * is left out rather than shown.
     */
    fun decode(files: Map<String, String>): List<Sample> {
        val ok = files.mapNotNull { (name, text) ->
            when (val d = SnapshotCodec.parse(text, nowMs = null)) {
                is Decoded.Ok -> Sample(name, d.value)
                is Decoded.Rejected -> null
            }
        }
        return ok.sortedWith(compareBy<Sample> { PREFERRED_ORDER.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.name })
    }

    /** Reads every samples/x.json in the app's assets. */
    fun load(assets: android.content.res.AssetManager): List<Sample> {
        val names = try { assets.list(ASSET_DIR)?.filter { it.endsWith(".json") } ?: emptyList() } catch (e: Exception) { emptyList() }
        val files = names.associate { n ->
            n.removeSuffix(".json") to try { assets.open("$ASSET_DIR/$n").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
        }
        return decode(files)
    }
}
