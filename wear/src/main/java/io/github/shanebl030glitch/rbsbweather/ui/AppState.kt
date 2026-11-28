// Where the screens get their data (Stage 3): the cached phone snapshot and settings, the phone's
// status, and the clock. Debug builds only: the Stage 1 samples (labelled SAMPLE DATA) when no
// real data has arrived, and launch options for the emulator screenshots (decision D2).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.content.Context
import android.content.Intent
import io.github.shanebl030glitch.rbsbweather.BuildConfig
import io.github.shanebl030glitch.rbsbweather.data.WeatherStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The phone link as the watch last saw it (set by the Data Layer code; UNKNOWN until checked). */
enum class PhoneStatus { UNKNOWN, CONNECTED, NO_APP, NOT_CONNECTED }

/** The app's single cache and change signal. */
object WeatherRepository {
    @Volatile private var store: WeatherStore? = null

    /** Goes up whenever the cache changes, so open screens redraw. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    private val _phone = MutableStateFlow(PhoneStatus.UNKNOWN)
    val phone: StateFlow<PhoneStatus> = _phone

    /** "Phone not reachable" after a refresh request found no phone; cleared when data arrives. */
    private val _refreshNote = MutableStateFlow<String?>(null)
    val refreshNote: StateFlow<String?> = _refreshNote

    @Synchronized fun store(ctx: Context): WeatherStore =
        store ?: WeatherStore(File(ctx.applicationContext.filesDir, "weather")).also { store = it }

    fun changed() { _version.value = _version.value + 1; _refreshNote.value = null }
    fun setPhone(s: PhoneStatus) { _phone.value = s }
    fun setRefreshNote(note: String?) { _refreshNote.value = note }
}

/**
 * Debug-only launch options for the emulator tests and screenshots. Release builds ignore them all.
 *  - sample: show this Stage 1 sample (when there is no real data)
 *  - ageMinutes: pretend the data is this many minutes old (sample mode only)
 *  - phone: "waiting", "noapp" or "nophone" to show the no-data screens instead of a sample
 *  - route: open a screen ("hours", "day/0", "warning/0", "moon", "moonFull")
 *  - scrollTo: on the first screen, scroll to a section ("details", "hourly", "daily", "wind", "warnings", "moon", "about")
 *  - clock: "12" to show the 12-hour clock (the phone's setting, before real settings arrive)
 */
data class DebugOptions(
    val sample: String? = null,
    val ageMinutes: Long = 0,
    val noData: NoDataReason? = null,
    val route: String? = null,
    val scrollTo: String? = null,
    val clock12: Boolean = false,
) {
    companion object {
        fun from(intent: Intent?): DebugOptions {
            if (!BuildConfig.DEBUG || intent == null) return DebugOptions()
            return DebugOptions(
                sample = intent.getStringExtra("sample"),
                ageMinutes = intent.getLongExtra("ageMinutes", intent.getIntExtra("ageMinutes", 0).toLong()).coerceIn(0, 60L * 24 * 30),
                noData = when (intent.getStringExtra("phone")) {
                    "waiting" -> NoDataReason.WAITING; "noapp" -> NoDataReason.PHONE_APP_NEEDED; "nophone" -> NoDataReason.NO_PHONE; else -> null
                },
                route = intent.getStringExtra("route"),
                scrollTo = intent.getStringExtra("scrollTo"),
                clock12 = intent.getStringExtra("clock") == "12",
            )
        }
    }
}
