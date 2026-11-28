// The frame-timing record kept for Stage 7. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import io.github.shanebl030glitch.rbsbweather.scene.WindowStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SkyStatsTest {
    @Test fun fileStartsWithItsHeaderAndKeepsTheLatestWindows() {
        val dir = Files.createTempDirectory("skystats").toFile()
        try {
            val f = java.io.File(dir, SkyStats.FILE)
            repeat(SkyStats.KEEP + 150) { SkyStats.append(f, "line $it") }
            val lines = f.readLines()
            assertEquals(WindowStats.CSV_HEADER, lines.first())
            assertTrue("bounded: ${lines.size}", lines.size <= SkyStats.KEEP + 101)
            assertEquals("line ${SkyStats.KEEP + 149}", lines.last())
        } finally { dir.deleteRecursively() }
    }
}
