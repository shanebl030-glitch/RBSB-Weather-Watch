// Drawing the sky for real at watch sizes (Robolectric native graphics, Android 16 / SDK 36).
// Ported from RBSB-Weather for Android v0.7.2 (cdf422d), RenderTest.kt and PicturesTest.kt; watch
// additions: round watch sizes, every quality level, the Sun staying bright under the readability
// shade, the one-cloud-set sprite cache, and pictures of every sample at 432/480/498 px saved to
// wear/build/screens. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.sky.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class RenderTest {
    /** The watch's readability shade, as SkyScreen draws it. */
    private val shade: DrawScope.() -> Unit = {
        val r = size.minDimension * 0.5f
        drawCircle(safeRadial(0f to Color(0x66000000), 0.6f to Color(0x38000000), 1f to Color(0x00000000), center = center, radius = r), r, center)
    }

    private fun render(e: SceneEngine, w: Int = 432, h: Int = 432, drawn: Drawn? = null, underSun: (DrawScope.() -> Unit)? = null,
                       overlay: (DrawScope.() -> Unit)? = null): Bitmap {
        val img = ImageBitmap(w, h)
        e.setSize(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawScene(e, 0f, scrim = false, drawn = drawn, underSun = underSun, overlay = overlay)
        }
        return img.asAndroidBitmap()
    }

    private fun colours(b: Bitmap): Int {
        val seen = HashSet<Int>()
        for (x in 0 until b.width step 23) for (y in 0 until b.height step 23) seen += b.getPixel(x, y)
        return seen.size
    }

    @Test fun runsOnAndroid16() {
        assertEquals(36, android.os.Build.VERSION.SDK_INT)                       // Robolectric really runs SDK 36
    }

    @Test fun everyCodeDayAndNightDrawsAtEveryQuality() {
        for (q in Quality.values()) for (code in WeatherCodes.known) for (daylight in listOf(1.0, 0.0)) {
            val e = SceneEngine(seed = 5).apply { quality = q }
            val (k, i) = classify(code)
            e.setSize(432, 432)
            e.setConditions(Conditions(k, i, daylight = daylight, sunProgress = 0.4, moonProgress = 0.6))
            repeat(5) { e.advance(0.2) }
            assertTrue("code $code $q", colours(render(e)) > 3)
        }
    }

    @Test fun dayAndNightLookDifferent() {
        // As the phone test: the weather is set before the size, so nothing cross-fades.
        val day = render(SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0)); setSize(432, 432) })
        val night = render(SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0)); setSize(432, 432) })
        assertTrue("day ${topBlue(day)} vs night ${topBlue(night)}", topBlue(day) > topBlue(night) + 60)
    }

    /** Average blue over the top band (the phone test used one pixel; a star or Sun ray can land on one). */
    private fun topBlue(b: Bitmap): Double {
        var sum = 0.0; var n = 0
        for (x in 0 until b.width step 7) for (y in 0 until 60 step 5) { sum += b.getPixel(x, y) and 0xFF; n++ }
        return sum / n
    }

    @Test fun theSkyOpensAsTheSamplesOwnSkyWithoutFadingFromDay() {
        // What SkyScreen does on its first frame: conditions before size. A night sky is night at once.
        val e = SceneEngine()
        e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0))
        e.setSize(432, 432)
        assertEquals(1.0, e.cloudFade, 0.0)                                       // no fade in progress
        assertTrue("night at once: ${topBlue(render(e))}", topBlue(render(e)) < 120)
        // The order that caused the 0.8 s flash of daytime blue (kept as a check that it was the cause):
        val wrong = SceneEngine().apply { setSize(432, 432); setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0)) }
        assertEquals(0.0, wrong.cloudFade, 0.0)
    }

    @Test fun sunAndMoonFollowTheirArcs() {
        val drawn = Drawn()
        val e = SceneEngine(clock = { 0L })
        e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0, sunProgress = 0.5, moonUp = true, moonProgress = 0.25))
        render(e, drawn = drawn)
        assertNotNull(drawn.sun); assertNotNull(drawn.moon)
        assertTrue(drawn.sun!!.second < drawn.moon!!.second)
        e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0, sunProgress = 0.5, moonUp = false))
        render(e, drawn = drawn)
        assertEquals(null, drawn.moon)
    }

    @Test fun moonPictureFromTheNasaTexturesAt512px() {
        MoonRender.init(RuntimeEnvironment.getApplication().assets)
        val full = MoonRender.render(64, 0.0, 270.0)!!
        val newMoon = MoonRender.render(64, 179.0, 270.0)!!
        assertEquals(0, full.getPixel(1, 1) ushr 24)                              // transparent outside the disc
        fun bright(b: Bitmap) = (b.getPixel(32, 32) shr 8) and 0xFF
        assertTrue(bright(full) > bright(newMoon) + 40)
        val opts = android.graphics.BitmapFactory.Options()
        val tex = RuntimeEnvironment.getApplication().assets.open("textures/moon_albedo.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        assertEquals(512, tex!!.width); assertEquals(512, tex.height)
    }

    @Test fun theSunStaysBrightUnderTheReadabilityShade() {
        // Milestone 1 drew the shade over everything and dulled the Sun; now it is drawn under it.
        fun sunCentre(underSun: (DrawScope.() -> Unit)?, overlay: (DrawScope.() -> Unit)?): Int {
            val drawn = Drawn()
            val e = SceneEngine(clock = { 0L }).apply { animations = false }
            e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0, sunProgress = 0.3))
            val b = render(e, drawn = drawn, underSun = underSun, overlay = overlay)
            val (x, y) = drawn.sun!!.let { it.first.toInt() to it.second.toInt() }
            return b.getPixel(x, y)
        }
        val now = sunCentre(underSun = shade, overlay = null)
        val before = sunCentre(underSun = null, overlay = shade)
        fun lum(c: Int) = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
        assertTrue("bright sun: ${lum(now)}", lum(now) >= 235)
        assertTrue("brighter than milestone 1: ${lum(now)} vs ${lum(before)}", lum(now) > lum(before) + 40)
    }

    @Test fun oneCloudSetInMemory() {
        for (i in 0 until 200) cloudSprite(60 + i % 50, 30 + i % 20, (i % 7) / 7.0, i)
        assertTrue("cache ${spriteCacheSize()}", spriteCacheSize() <= SPRITE_CACHE_MAX)
        // a full 498 px overcast sky has fewer clouds than the cache holds, so it never thrashes
        val e = SceneEngine(seed = 7).apply { setSize(498, 498); setConditions(Conditions(Kind.OVERCAST, 1.0)) }
        assertTrue("clouds ${e.clouds.size}", e.clouds.size * 2 <= SPRITE_CACHE_MAX + 4)
    }

    @Test fun picturesOfEverySampleAtWatchSizes() {
        val app = RuntimeEnvironment.getApplication()
        MoonRender.init(app.assets)
        val dir = File(System.getProperty("rbsb.samples") ?: "../sync/samples", "snapshot/valid")
        val samples = SampleData.decode(dir.listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name.removeSuffix(".json") to it.readText() })
        val out = File("build/screens").apply { mkdirs() }
        for (px in listOf(432, 480, 498)) for (s in samples.take(10)) {
            val snap = s.snapshot
            val e = SceneEngine(seed = 7, clock = { snap.fetchedAt })
            e.setSize(px, px)
            e.setConditions(conditionsFromSnapshot(snap, snap.fetchedAt))
            repeat(30) { e.advance(0.1) }
            val b = render(e, px, px, underSun = shade)
            File(out, "sky_${px}_${s.name}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("${s.name} $px is blank", colours(b) > 10)
        }
    }
}
