// Stage 2 sample data: every Stage 1 valid snapshot loads, in the intended order.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleDataTest {
    private val dir = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid")
    private fun files() = dir.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() }

    @Test fun everyValidStage1SampleLoads() {
        val files = files()
        assertTrue("found the Stage 1 samples in $dir", files.size >= 18)
        val samples = SampleData.decode(files)
        assertEquals("every valid sample decodes", files.keys, samples.map { it.name }.toSet())
    }

    @Test fun weatherSamplesComeFirstInViewingOrder() {
        val names = SampleData.decode(files()).map { it.name }
        assertEquals(listOf("clear-day", "partly-cloudy-day", "overcast", "drizzle", "rain-night-warnings", "thunder-warning",
            "fog", "snow-northern", "clear-night", "polar-night"), names.take(10))
    }

    @Test fun aBrokenSampleIsLeftOutNotShown() {
        val samples = SampleData.decode(mapOf("good" to files().getValue("clear-day"), "bad" to "{not json"))
        assertEquals(listOf("good"), samples.map { it.name })
    }
}
