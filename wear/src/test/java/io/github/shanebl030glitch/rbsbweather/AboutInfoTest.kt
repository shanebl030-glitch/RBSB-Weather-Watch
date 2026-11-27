// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutInfoTest {
    @Test
    fun creditIsSpelledExactly() {
        // The capital R in BlackbuRn is deliberate.
        assertEquals("Created By Shane BlackbuRn", AboutInfo.CREDIT)
    }

    @Test
    fun aboutScreenShowsTitleVersionAndCredit() {
        val lines = AboutInfo.lines("0.1.0-dev")
        assertEquals("RBSB-Weather Watch", lines[0])
        assertEquals("Version 0.1.0-dev", lines[1])
        assertTrue(lines.contains("Created By Shane BlackbuRn"))
    }

    @Test
    fun aboutCreditsNasaForTheMoonImagery() {
        // Approved Stage 2 decision 5: the Moon textures come from NASA's CGI Moon Kit.
        assertTrue(AboutInfo.lines("0.1.0-dev").contains(AboutInfo.MOON_CREDIT))
        assertTrue(AboutInfo.MOON_CREDIT.contains("NASA"))
    }

    @Test
    fun buildIsTheStage0WatchVersion() {
        // Watch version codes use their own range (1_000_000 + major*10_000 + minor*100 + patch).
        assertEquals(1_000_100, BuildConfig.VERSION_CODE)
        assertTrue(BuildConfig.VERSION_NAME.startsWith("0.1.0-dev"))
    }
}
