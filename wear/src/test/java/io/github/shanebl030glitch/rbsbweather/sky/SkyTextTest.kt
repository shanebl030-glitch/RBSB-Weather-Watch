// The words over the sky. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SkyTextTest {
    private val samples = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid").let { d ->
        SampleData.decode(d.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() })
    }.associateBy { it.name }

    @Test fun temperatureIsRoundedWithADegreeSign() {
        assertEquals("19°", SkyText.temperature(19.0))
        assertEquals("-4°", SkyText.temperature(-3.5))
        assertEquals("--°", SkyText.temperature(null))
        assertEquals("--°", SkyText.temperature(Double.NaN))
    }

    @Test fun conditionsAndHighLowComeFromTheSnapshot() {
        val s = samples.getValue("rain-night-warnings").snapshot
        assertEquals("Heavy rain", SkyText.condition(s))
        assertEquals("H 19°  L 9°", SkyText.highLow(s))
        assertEquals("Clear sky", SkyText.condition(samples.getValue("clear-day").snapshot))
    }

    @Test fun missingValuesNeverShowMadeUpNumbers() {
        assertNull(SkyText.highLow(samples.getValue("minimal").snapshot))
        assertNull(SkyText.highLow(samples.getValue("explicit-nulls").snapshot))
        assertEquals("--°", SkyText.temperature(samples.getValue("explicit-nulls").snapshot.current.temperature))
        assertEquals("Unknown", SkyText.condition(samples.getValue("explicit-nulls").snapshot))
    }

    @Test fun sampleLabelAndLine() {
        assertEquals("SAMPLE DATA", SkyText.SAMPLE_LABEL)
        assertEquals("3/18 · overcast", SkyText.sampleLine(2, 18, "overcast"))
    }
}
