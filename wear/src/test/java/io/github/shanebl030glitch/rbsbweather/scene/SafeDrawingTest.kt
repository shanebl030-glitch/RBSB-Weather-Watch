// The phone's 0.5.0 crash ("ending radius must be > 0") can't come back: real Android refuses a
// radial gradient with radius 0. Ported from RBSB-Weather for Android v0.7.2 (cdf422d),
// SafeDrawingTest.kt; watch change: the source folder, and no Earth screen to check.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SafeDrawingTest {
    @Test fun radiusIsNeverZero() {
        for (bad in listOf(0f, -3f, Float.NaN, 0.1f)) assertEquals(MIN_GRADIENT_RADIUS, safeRadius(bad))
        assertEquals(12f, safeRadius(12f))
        assertTrue(MIN_GRADIENT_RADIUS > 0f)
    }

    /** No drawing code calls Brush.radialGradient itself: everything goes through safeRadial. */
    @Test fun allRadialGradientsAreSafe() {
        val src = File("src/main/java").takeIf { it.isDirectory } ?: File("wear/src/main/java")
        assertTrue("found the sources", src.isDirectory)
        val offenders = src.walkTopDown().filter { it.name.endsWith(".kt") && it.name != "SafeBrush.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if ("radialGradient(" in line) "${f.name}:${i + 1}" else null }
        }.toList()
        assertTrue("call safeRadial instead: $offenders", offenders.isEmpty())
        assertTrue(src.walkTopDown().any { it.name == "SkyHero.kt" && "safeRadial(" in it.readText() })   // the sky screen (Stage 3: SkyHero)
    }
}
