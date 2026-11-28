// The watch Moon against RBSB-Weather Desktop, drawn by the watch's real Kotlin renderer on
// Android 16 graphics (Robolectric native graphics, SDK 36). Same phase, illumination and
// orientation as the Desktop reference pictures (25%, 50% and 78% lit; 240 px Moon screen and
// 480 px full screen). Also renders Android 0.7.2's own 2048 px textures and the old watch 512 px
// textures through the same code, for comparison. Pictures and results go to build/moon-parity.
// Shane's Moon review, 28 Nov 2026. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonParityTest {
    private fun res(path: String) = javaClass.classLoader!!.getResourceAsStream("moon_parity/$path") ?: error("missing test resource moon_parity/$path")
    private val out = File("build/moon-parity").apply { mkdirs() }
    private val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = false }

    private fun desktop(case: String, size: Int) = res("desktop_${case}_$size.png").use { BitmapFactory.decodeStream(it, null, opts) }!!

    private fun save(b: Bitmap, name: String) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Renders every case and size with the texture set chosen by [prepare]; returns the results. */
    private fun run(set: String, prepare: () -> Unit): List<MoonParity.Result> {
        val (sizes, cases) = MoonParity.cases(res("cases.json").bufferedReader().readText())
        val results = mutableListOf<MoonParity.Result>()
        for (c in cases) for (size in sizes) {
            prepare()
            val b = MoonRender.render(size, c.phaseAngle, c.brightLimb, rotation = c.rotation) ?: error("no Moon picture ($set ${c.name} $size)")
            assertEquals(size, b.width)
            assertTrue("transparent outside the disc", MoonParity.cornersTransparent(b))
            assertNull("decision D9: the full-size textures are released after scaling ($set ${c.name} $size)", MoonParity.heldTextures())
            save(b, "${set}_${c.name}_$size")
            results += MoonParity.compare(set, c.name, size, b, desktop(c.name, size))
        }
        return results
    }

    @Test fun watchMoonMatchesDesktopAndAndroid() {
        MoonRender.init(RuntimeEnvironment.getApplication().assets)
        val watch = run("watch1024") { MoonParity.clearCaches() }                                        // the app's own 1024 px textures
        val android = run("android2048") { MoonParity.useTextures(res("android2048/moon_albedo.jpg"), res("android2048/moon_height.jpg")) }
        val old = run("watch512") { MoonParity.useTextures(res("watch512/moon_albedo.jpg"), res("watch512/moon_height.jpg")) }
        MoonParity.clearCaches()
        val lines = (watch + android + old).map { it.line() }
        File(out, "results.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        lines.forEach(::println)

        // Pass marks from the Stage 3 proposal, section 2a: average difference from Desktop no more
        // than 4 levels out of 255; the same lit fraction (phase and illumination) and the same lit
        // direction (orientation and tilt) as Desktop.
        for (r in watch) {
            assertTrue("${r.case} ${r.size}: mean difference ${r.meanDiff}", r.meanDiff <= 4.0)
            assertTrue("${r.case} ${r.size}: lit fraction ${r.litFraction} vs ${r.desktopLitFraction}", kotlin.math.abs(r.litFraction - r.desktopLitFraction) <= 0.01)
            assertTrue("${r.case} ${r.size}: lit direction off by ${r.angleError}°", r.angleError <= 1.0)
        }
    }
}
