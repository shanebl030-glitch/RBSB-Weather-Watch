// Moon parity checks shared by the Robolectric test (MoonParityTest) and the on-device emulator
// test (MoonDeviceTest): the watch's real Kotlin renderer (MoonRender) against RBSB-Weather
// Desktop's own renders at the same phase, illumination and orientation (Shane's Moon review,
// 28 Nov 2026). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.atan2

object MoonParity {
    data class Case(val name: String, val illumination: Double, val phaseAngle: Double, val brightLimb: Double, val rotation: Double)

    /** One picture's measurements against the Desktop picture of the same case and size. */
    data class Result(
        val set: String, val case: String, val size: Int,
        val meanDiff: Double, val p99Diff: Int,
        val litFraction: Double, val desktopLitFraction: Double,
        val litAngle: Double, val desktopLitAngle: Double,
        val detail: Double, val desktopDetail: Double,
    ) {
        val angleError: Double get() = abs(((litAngle - desktopLitAngle + 540) % 360) - 180)
        fun line() = String.format(
            java.util.Locale.US,
            "MOON PARITY set=%s case=%s size=%d meanDiff=%.2f p99Diff=%d litFraction=%.4f desktopLitFraction=%.4f litAngle=%.2f desktopLitAngle=%.2f angleError=%.2f detail=%.2f desktopDetail=%.2f",
            set, case, size, meanDiff, p99Diff, litFraction, desktopLitFraction, litAngle, desktopLitAngle, angleError, detail, desktopDetail,
        )
    }

    fun cases(json: String): Pair<List<Int>, List<Case>> {
        val o = JSONObject(json)
        val sizes = o.getJSONArray("sizes").let { a -> List(a.length()) { a.getInt(it) } }
        val cs = o.getJSONArray("cases").let { a ->
            List(a.length()) { i ->
                val c = a.getJSONObject(i)
                Case(c.getString("name"), c.getDouble("illumination"), c.getDouble("phaseAngle"), c.getDouble("brightLimb"), c.getDouble("rotation"))
            }
        }
        return sizes to cs
    }

    private fun field(name: String) = MoonRender::class.java.getDeclaredField(name).apply { isAccessible = true }

    /** The decoded full-size textures MoonRender is holding, or null (decision D9: always null after a render). */
    fun heldTextures(): Any? = field("full").get(null)

    /** Forget every cached texture and picture, so the next render starts from the files. */
    fun clearCaches() {
        synchronized(MoonRender) {
            (field("texCache").get(null) as MutableMap<*, *>).clear()
            field("full").set(null, null)
        }
        val img = field("imgCache").get(null) as MutableMap<*, *>
        synchronized(img) { img.clear(); (field("lastBySize").get(null) as MutableMap<*, *>).clear() }
    }

    /**
     * Makes the next render use these texture files instead of the app's own (for the Android
     * 2048 px and the old watch 512 px comparisons). The renderer code is the app's, unchanged.
     */
    fun useTextures(albedo: InputStream, height: InputStream) {
        clearCaches()
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val a = albedo.use { BitmapFactory.decodeStream(it, null, opts) }!!
        val h = height.use { BitmapFactory.decodeStream(it, null, opts) }!!
        synchronized(MoonRender) { field("full").set(null, a to h) }
    }

    private fun lum(p: Int) = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3.0

    private class Stats(val lit: Double, val angle: Double, val detail: Double)

    private fun stats(b: Bitmap): Stats {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var disc = 0; var lit = 0; var sx = 0.0; var sy = 0.0
        var lapSum = 0.0; var lapN = 0
        val c = (w - 1) / 2.0
        for (y in 0 until h) for (x in 0 until w) {
            val p = px[y * w + x]
            if ((p ushr 24) != 255) continue
            disc++
            val l = lum(p)
            if (l > 40) { lit++; sx += x - c; sy += c - y }
            if (x in 1 until w - 1 && y in 1 until h - 1 && l > 20) {
                val n = listOf(px[(y - 1) * w + x], px[(y + 1) * w + x], px[y * w + x - 1], px[y * w + x + 1])
                if (n.all { (it ushr 24) == 255 }) { lapSum += abs(4 * l - n.sumOf { lum(it) }); lapN++ }
            }
        }
        val angle = (Math.toDegrees(atan2(sx, sy)) + 360) % 360          // 0 = up, 90 = right
        return Stats(lit.toDouble() / maxOf(disc, 1), angle, if (lapN > 0) lapSum / lapN else 0.0)
    }

    fun compare(set: String, case: String, size: Int, watch: Bitmap, desktop: Bitmap): Result {
        require(watch.width == desktop.width && watch.height == desktop.height) { "sizes differ" }
        val w = watch.width; val h = watch.height
        val a = IntArray(w * h); watch.getPixels(a, 0, w, 0, 0, w, h)
        val d = IntArray(w * h); desktop.getPixels(d, 0, w, 0, 0, w, h)
        val hist = IntArray(256); var sum = 0.0; var n = 0L
        for (i in a.indices) {
            if ((a[i] ushr 24) != 255 || (d[i] ushr 24) != 255) continue
            for (sh in intArrayOf(16, 8, 0)) {
                val diff = abs(((a[i] shr sh) and 0xFF) - ((d[i] shr sh) and 0xFF))
                hist[diff]++; sum += diff; n++
            }
        }
        var acc = 0L; var p99 = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= n * 0.99) { p99 = v; break } }
        val sw = stats(watch); val sd = stats(desktop)
        return Result(set, case, size, sum / maxOf(n, 1), p99, sw.lit, sd.lit, sw.angle, sd.angle, sw.detail, sd.detail)
    }

    /** The disc's radius check: nothing drawn outside it (transparent corners). */
    fun cornersTransparent(b: Bitmap) = listOf(b.getPixel(0, 0), b.getPixel(b.width - 1, 0), b.getPixel(0, b.height - 1), b.getPixel(b.width - 1, b.height - 1)).all { (it ushr 24) == 0 }

}
