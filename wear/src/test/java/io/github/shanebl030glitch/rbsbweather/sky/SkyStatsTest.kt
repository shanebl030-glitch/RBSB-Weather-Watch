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
            repeat(SkyStats.KEEP + 150) { i ->
                SkyStats.append(f, "line $i")
                val windows = f.readLines().size - 1
                assertTrue("after append $i the file holds $windows windows", windows <= SkyStats.KEEP)  // never more than 500
                assertEquals(minOf(i + 1, SkyStats.KEEP), windows)
            }
            val lines = f.readLines()
            assertEquals(WindowStats.CSV_HEADER, lines.first())
            assertEquals(SkyStats.KEEP + 1, lines.size)                                 // header + exactly the last 500
            assertEquals("line 150", lines[1])                                          // the oldest 150 were removed
            assertEquals("line ${SkyStats.KEEP + 149}", lines.last())
        } finally { dir.deleteRecursively() }
    }
}
