// Frame-timing measurements of the live sky (approved Stage 2 decision 3: record them so the
// quality thresholds can be reviewed and tuned at Stage 7). Each 10-second window is written to
// logcat (tag RBSBSky) and to app-private storage (sky-frame-timing.csv, never more than the last 500 windows; excluded
// from backup like all app data). The latest is shown on the About page. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import io.github.shanebl030glitch.rbsbweather.scene.WindowStats
import java.io.File

object SkyStats {
    const val TAG = "RBSBSky"
    const val FILE = "sky-frame-timing.csv"
    const val KEEP = 500

    /** The latest window's one-line summary, for the About page. */
    val latest = mutableStateOf<String?>(null)

    /** Logs and stores one window. Safe to call off the main thread; never throws. */
    fun record(stats: WindowStats, dir: File?) {
        try { Log.i(TAG, "SKY FRAME WINDOW ${stats.csv()}") } catch (e: Throwable) { }
        latest.value = stats.summary()
        if (dir == null) return
        try { append(File(dir, FILE), stats.csv()) } catch (e: Exception) { }
    }

    /**
     * Appends a window, starting the file with its header. The file never holds more than [KEEP]
     * windows: as soon as an append takes it over, the oldest are removed (header + last [KEEP]).
     */
    @Synchronized fun append(f: File, line: String) {
        if (!f.exists()) f.writeText(WindowStats.CSV_HEADER + "\n")
        f.appendText(line + "\n")
        val lines = f.readLines()
        if (lines.size - 1 > KEEP) f.writeText((listOf(lines.first()) + lines.drop(1).takeLast(KEEP)).joinToString("\n", postfix = "\n"))
    }
}
