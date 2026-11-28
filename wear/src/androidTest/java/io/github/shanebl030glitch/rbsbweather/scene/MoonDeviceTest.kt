// The watch Moon on a real Wear OS system image (emulator): the app's own Kotlin renderer and
// 1024 px textures on the device's Android graphics, compared with RBSB-Weather Desktop at 25%,
// 50% and 78% lit (240 px Moon screen, 480 px full screen), plus memory: what the decoded
// full-size textures cost and proof they are released after downscaling (decision D9).
// Results: logcat tag RBSBMoon and files/moon-parity in the app's private storage.
// Shane's Moon review, 28 Nov 2026. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MoonDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val out = File(app.filesDir, "moon-parity").apply { deleteRecursively(); mkdirs() }
    private val lines = mutableListOf<String>()

    private fun log(s: String) { Log.i("RBSBMoon", s); lines += s }

    /** Native heap (where bitmap pixels live) plus the Java heap, after a few garbage collections. */
    private fun usedBytes(): Long {
        repeat(3) { Runtime.getRuntime().gc(); System.runFinalization(); SystemClock.sleep(150) }
        val rt = Runtime.getRuntime()
        return Debug.getNativeHeapAllocatedSize() + (rt.totalMemory() - rt.freeMemory())
    }

    private fun mb(b: Long) = String.format(Locale.US, "%.2f MB", b / 1048576.0)

    @Test fun moonOnTheDevice() {
        MoonRender.init(app.assets)
        MoonParity.clearCaches()
        val m0 = usedBytes()

        // What holding the decoded 1024 px textures would cost (the phone's behaviour on a watch).
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val a = app.assets.open("textures/moon_albedo.jpg").use { BitmapFactory.decodeStream(it, null, opts) }!!
        val h = app.assets.open("textures/moon_height.jpg").use { BitmapFactory.decodeStream(it, null, opts) }!!
        val held = usedBytes() - m0
        log("MOON MEMORY decodedTextures=${a.width}x${a.height}+${h.width}x${h.height} cost=${mb(held)} (bitmap bytes ${mb((a.allocationByteCount + h.allocationByteCount).toLong())})")
        a.recycle(); h.recycle()
        val m1 = usedBytes()
        log("MOON MEMORY afterRecycle=${mb(m1 - m0)}")

        // The sky Moon (88 px on a 480 px watch): render, then check the full-size textures are gone.
        var t = SystemClock.elapsedRealtimeNanos()
        val sky = MoonRender.render(88, 90.0, 270.0, rotation = 180.0)
        val skyMs = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
        assertTrue("sky Moon drawn", sky != null)
        assertNull("decision D9: full-size textures released after the sky Moon", MoonParity.heldTextures())
        val m2 = usedBytes()
        log(String.format(Locale.US, "MOON MEMORY afterSkyMoon88=%s heldTextures=%s firstRenderMs=%.1f", mb(m2 - m0), MoonParity.heldTextures() != null, skyMs))

        // The comparison pictures (240 px Moon screen, 480 px full screen).
        val (sizes, cases) = MoonParity.cases(testAssets.open("moon_parity/cases.json").bufferedReader().readText())
        val dopts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = false }
        val results = mutableListOf<MoonParity.Result>()
        for (c in cases) for (size in sizes) {
            t = SystemClock.elapsedRealtimeNanos()
            val b = MoonRender.render(size, c.phaseAngle, c.brightLimb, rotation = c.rotation)!!
            val ms = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
            assertNull("decision D9 after ${c.name} $size", MoonParity.heldTextures())
            File(out, "device1024_${c.name}_$size.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val d = testAssets.open("moon_parity/desktop_${c.name}_$size.png").use { BitmapFactory.decodeStream(it, null, dopts) }!!
            val r = MoonParity.compare("device1024", c.name, size, b, d)
            results += r
            log(r.line() + String.format(Locale.US, " renderMs=%.1f", ms))
        }
        val m3 = usedBytes()
        log("MOON MEMORY afterAllPictures=${mb(m3 - m0)} heldTextures=${MoonParity.heldTextures() != null} (includes the renderer's own picture and scaled-texture caches)")
        MoonParity.clearCaches()
        val m4 = usedBytes()
        log("MOON MEMORY afterClearingCaches=${mb(m4 - m0)}")
        File(out, "results.txt").writeText(lines.joinToString("\n", postfix = "\n"))

        for (r in results) {
            assertTrue("${r.case} ${r.size}: mean difference ${r.meanDiff}", r.meanDiff <= 4.0)
            assertTrue("${r.case} ${r.size}: lit fraction ${r.litFraction} vs ${r.desktopLitFraction}", kotlin.math.abs(r.litFraction - r.desktopLitFraction) <= 0.01)
            assertTrue("${r.case} ${r.size}: lit direction off by ${r.angleError}°", r.angleError <= 1.0)
        }
    }
}
