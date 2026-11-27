// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import io.github.shanebl030glitch.rbsbweather.sky.SkyScreen

/**
 * Two pages: the sky (first, approved Stage 2 decision 7) and About. Swipe sideways between them.
 * Optional launch extras, used by the emulator tests: "sample" (a sample name) and "page" (0 or 1).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sampleName = intent?.getStringExtra(EXTRA_SAMPLE)
        val page = (intent?.getIntExtra(EXTRA_PAGE, 0) ?: 0).coerceIn(0, 1)
        setContent { WatchApp(BuildConfig.VERSION_NAME, sampleName, page) }
    }

    companion object {
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_PAGE = "page"
    }
}

@Composable
fun WatchApp(versionName: String, sampleName: String?, startPage: Int) {
    val ctx = LocalContext.current
    val samples = remember { SampleData.load(ctx.assets) }
    val start = remember { samples.indexOfFirst { it.name == sampleName }.coerceAtLeast(0) }
    MaterialTheme {
        val pager = rememberPagerState(initialPage = startPage) { 2 }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Scaffold(timeText = { TimeText() }) { SkyScreen(samples, start) }
                else -> AboutScreen(versionName)
            }
        }
    }
}

@Composable
fun AboutScreen(versionName: String) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            AboutInfo.lines(versionName).forEachIndexed { i, line ->
                item {
                    Text(
                        text = line,
                        textAlign = TextAlign.Center,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        style = if (i == 0) MaterialTheme.typography.title3
                            else if (line == AboutInfo.MOON_CREDIT) MaterialTheme.typography.caption3
                            else MaterialTheme.typography.body2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
