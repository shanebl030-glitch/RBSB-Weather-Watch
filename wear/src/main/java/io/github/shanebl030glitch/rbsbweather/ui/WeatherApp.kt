// The app's screens and how they connect (Stage 3): the first screen scrolls vertically from the
// sky (decision D1); details open on top and swipe right goes back (Wear OS navigation). Picks the
// data to show: the cached phone snapshot, else (debug builds only, decision D2) a labelled Stage 1
// sample, else the "waiting for your phone" screen. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private data class Shown(val snapshot: Snapshot?, val settings: SyncSettings, val nowMs: Long, val sampleLine: String?)

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
    )

    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        LaunchedEffect(debug.route) { debug.route?.let { r -> try { nav.navigate(r) } catch (e: Exception) { } } }
        if (BuildConfig.DEBUG) LaunchedEffect(nav) {
            nav.currentBackStackEntryFlow.collect { e -> android.util.Log.i("RBSBInput", "SCREEN ${e.destination.route}") }
        }
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                val s = shown.snapshot
                if (s == null) {
                    NoDataHero(debug.noData ?: when (phone) {
                        PhoneStatus.NO_APP -> NoDataReason.PHONE_APP_NEEDED
                        PhoneStatus.NOT_CONNECTED -> NoDataReason.NO_PHONE
                        else -> NoDataReason.WAITING
                    }, onRetry = null)
                } else {
                    HomeScreen(
                        snapshot = s, settings = shown.settings, nowMs = shown.nowMs, sampleLine = shown.sampleLine,
                        aboutLines = AboutInfo.lines(versionName, statusLines(s, shown.settings, shown.nowMs, phone, sample?.name)),
                        onSkyTap = if (sample != null) ({
                            android.util.Log.i("RBSBInput", "SKY TAP: next sample")
                            sampleIndex = (sampleIndex + 1) % samples.size
                        }) else null,
                        onOpen = { route -> nav.navigate(route) },
                        scrollTo = debug.scrollTo,
                    )
                }
            }
            composable("hours") { shown.snapshot?.let { HoursScreen(it, shown.settings, shown.nowMs) } }
            composable("day/{date}") { e ->
                val date = e.arguments?.getString("date") ?: ""
                shown.snapshot?.let { DayScreen(it, shown.settings, shown.nowMs, resolveDay(it, shown.nowMs, date)) }
            }
            composable("warning/{id}") { e ->
                val id = e.arguments?.getString("id") ?: ""
                shown.snapshot?.let { WarningScreen(it, shown.settings, shown.nowMs, resolveWarning(it, shown, id)) }
            }
            composable("moon") { shown.snapshot?.let { MoonScreen(it, shown.settings, shown.nowMs, onFullScreen = { nav.navigate("moonFull") }) } }
            composable("moonFull") { shown.snapshot?.let { MoonFullScreen(it, shown.settings, shown.nowMs, onClose = { nav.popBackStack() }) } }
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
