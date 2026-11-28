// The app's screens and how they connect (Stage 3): the first screen scrolls vertically from the
// sky (decision D1); details open on top and swipe right goes back (Wear OS navigation). Picks the
// data to show: the cached phone snapshot, else (debug builds only, decision D2) a labelled Stage 1
// sample, else the "waiting for your phone" screen. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.shanebl030glitch.rbsbweather.AboutInfo
import io.github.shanebl030glitch.rbsbweather.BuildConfig
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherView
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import kotlinx.coroutines.delay
import java.time.ZoneId

/** What the screens show right now. */
private data class Shown(val snapshot: Snapshot?, val settings: SyncSettings, val nowMs: Long, val sampleLine: String?, val sampleName: String?)

@Composable
fun WatchApp(versionName: String, debug: DebugOptions) {
    val ctx = LocalContext.current
    val store = remember { WeatherRepository.store(ctx) }
    val version by WeatherRepository.version.collectAsState()
    val phone by WeatherRepository.phone.collectAsState()
    val real = remember(version) { store.snapshot() }
    val settings = remember(version) { store.settings() ?: Units.DEFAULT_SETTINGS }

    // Debug builds only: the Stage 1 samples when no real data has arrived (always labelled).
    val samples = remember { if (BuildConfig.DEBUG) SampleData.load(ctx.assets) else emptyList() }
    var sampleIndex by remember { mutableIntStateOf(samples.indexOfFirst { it.name == debug.sample }.coerceAtLeast(0)) }
    val useSample = real == null && samples.isNotEmpty() && debug.noData == null

    // The clock: real time, or for a sample its own moment moving on (plus the debug "age").
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick = System.currentTimeMillis() } }
    val openedAt = remember(sampleIndex) { System.currentTimeMillis() }
    val sample = if (useSample) samples[sampleIndex] else null
    val shown = Shown(
        snapshot = real ?: sample?.snapshot,
        settings = settings,
        nowMs = sample?.let { it.snapshot.fetchedAt + debug.ageMinutes * 60_000L + (tick - openedAt).coerceAtLeast(0) } ?: tick,
        sampleLine = sample?.let { "${sampleIndex + 1}/${samples.size}" },
        sampleName = sample?.name,
    )

    // The navigation graph is built once and its screens keep the values they were built with, so
    // every screen reads the current state through this (found on the emulators: without it, a new
    // sample, the clock moving on or new phone data would not reach an open screen).
    val current by rememberUpdatedState(shown)
    val currentPhone by rememberUpdatedState(phone)
    // The watch's Back button goes straight to back handling (close a detail screen, or leave the
    // app from the first screen). Found on the Wear OS 4 emulator: otherwise the first press only
    // cleared the scrolling list's input focus and did nothing visible. Previewed at the root, so it
    // is seen before the focus system takes it.
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    MaterialTheme {
      Box(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
          if (e.key != Key.Back) return@onPreviewKeyEvent false
          if (BuildConfig.DEBUG) android.util.Log.i("RBSBInput", "KEY back ${e.type}")
          if (e.type == KeyEventType.KeyUp) back?.onBackPressed()
          true
      }) {
        val nav = rememberSwipeDismissableNavController()
        LaunchedEffect(debug.route) { debug.route?.let { r -> try { nav.navigate(r) } catch (e: Exception) { } } }
        if (BuildConfig.DEBUG) LaunchedEffect(nav) {
            nav.currentBackStackEntryFlow.collect { e -> android.util.Log.i("RBSBInput", "SCREEN ${e.destination.route}") }
        }
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                val s = current.snapshot
                if (s == null) {
                    NoDataHero(debug.noData ?: when (currentPhone) {
                        PhoneStatus.NO_APP -> NoDataReason.PHONE_APP_NEEDED
                        PhoneStatus.NOT_CONNECTED -> NoDataReason.NO_PHONE
                        else -> NoDataReason.WAITING
                    }, onRetry = null)
                } else {
                    HomeScreen(
                        snapshot = s, settings = current.settings, nowMs = current.nowMs, sampleLine = current.sampleLine,
                        aboutLines = AboutInfo.lines(versionName, statusLines(s, current.settings, current.nowMs, currentPhone, current.sampleName)),
                        onSkyTap = if (current.sampleName != null) ({
                            android.util.Log.i("RBSBInput", "SKY TAP: next sample")
                            sampleIndex = (sampleIndex + 1) % samples.size
                        }) else null,
                        onOpen = { route -> nav.navigate(route) },
                        scrollTo = debug.scrollTo,
                    )
                }
            }
            composable("hours") { current.snapshot?.let { HoursScreen(it, current.settings, current.nowMs) } }
            composable("day/{date}") { e ->
                val date = e.arguments?.getString("date") ?: ""
                current.snapshot?.let { DayScreen(it, current.settings, current.nowMs, resolveDay(it, current.nowMs, date)) }
            }
            composable("warning/{id}") { e ->
                val id = e.arguments?.getString("id") ?: ""
                current.snapshot?.let { WarningScreen(it, current.settings, current.nowMs, resolveWarning(it, current, id)) }
            }
            composable("moon") { current.snapshot?.let { MoonScreen(it, current.settings, current.nowMs, onFullScreen = { nav.navigate("moonFull") }) } }
            composable("moonFull") { current.snapshot?.let { MoonFullScreen(it, current.settings, current.nowMs, onClose = { nav.popBackStack() }) } }
        }
      }
    }
}

/** "day/0" (debug: the first forecast day) or a real date. */
private fun resolveDay(s: Snapshot, nowMs: Long, date: String): String =
    date.toIntOrNull()?.let { i -> WeatherView.daysAhead(s, nowMs).getOrNull(i)?.date } ?: date

/** "warning/0" (debug: the first active warning) or a real id. */
private fun resolveWarning(s: Snapshot, shown: Shown, id: String): String =
    id.toIntOrNull()?.let { i -> WeatherView.activeWarnings(s, shown.nowMs, shown.settings.minWarningLevel).getOrNull(i)?.id } ?: id

/** The data's status for the About section. */
private fun statusLines(s: Snapshot, st: SyncSettings, nowMs: Long, phone: PhoneStatus, sampleName: String?): List<String> {
    val place = listOf(s.place.name, s.place.admin1, s.place.country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    val zone = WeatherView.zone(s)
    val fetched = Units.clockAt(s.fetchedAt, zone, ZoneId.systemDefault(), st.clock)
    return listOfNotNull(
        sampleName?.let { "Sample data (debug build): $it" },
        place,
        "${s.provider} · fetched $fetched",
        WeatherView.ageText(WeatherView.ageMs(s, nowMs)),
        when (phone) {
            PhoneStatus.CONNECTED -> "Phone: connected"
            PhoneStatus.NO_APP -> "Phone: RBSB-Weather 0.8.0 needed"
            PhoneStatus.NOT_CONNECTED -> "Phone: not connected"
            PhoneStatus.UNKNOWN -> null
        },
    )
}
