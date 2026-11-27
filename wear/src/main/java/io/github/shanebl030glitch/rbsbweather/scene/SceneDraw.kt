// Drawing the animated sky (the desktop's scene.py paint_scene, line for line where it can be):
// sky gradient, twinkling stars, the Sun with its glow and slowly turning rays, the Moon in its
// real phase (by day too, paler), volumetric cloud sprites back to front, fog, rain with
// splashes, drizzle, snow, lightning, the readability shade, then the mini forecast.
// Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/ui/scene/SceneDraw.kt. Watch changes: package name only (Stage 2 milestone 1).
package io.github.shanebl030glitch.rbsbweather.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the last frame drew (for tests): the Sun and Moon centres and their arc progress. */
data class Drawn(var sun: Triple<Float, Float, Double>? = null, var moon: Triple<Float, Float, Double>? = null)

private fun Color.a(alpha: Int) = copy(alpha = (alpha / 255f).coerceIn(0f, 1f))

// --------------------------------------------------------------- cloud sprites
private val spriteCache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 96
}

/** The latest sprite made for each cloud (any size or darkness), shown scaled while the exact one is being made. */
private val lastBySeed = HashMap<Int, ImageBitmap>()
/** The latest cloud made (guarded by spriteCache): the stand-in for a cloud never drawn before. */
private var lastMade: ImageBitmap? = null
/** Clouds made on the main thread (for the tests: at most the very first one). */
val mainThreadSprites = java.util.concurrent.atomic.AtomicInteger(0)
private val spritePending = java.util.Collections.synchronizedSet(HashSet<String>())
private val spriteWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "rbsb-clouds").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
/** Goes up when a cloud made in the background is ready, so the sky redraws. */
val sceneAssetsReady = androidx.compose.runtime.mutableIntStateOf(0)

private fun spriteKey(w: Int, h: Int, darkness: Double, seed: Int) = "$w/$h/${Math.round(darkness * 20) / 20.0}/$seed"

/**
 * For drawing on the main thread: the cloud if it's been made, else a stand-in (see below) while
 * it's made in the background. Painting a dozen new clouds in one frame
 * (after turning the phone, or as dusk darkens them) took long enough for Android to report the app
 * as not responding (found by the stability audit). Off the main thread (widgets) it just makes it.
 */
fun cloudSpriteOrLater(w: Int, h: Int, darkness: Double, seed: Int): ImageBitmap? {
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return cloudSprite(w, h, darkness, seed)
    val key = spriteKey(w, h, darkness, seed)
    // meanwhile: this cloud's latest picture; for a cloud never drawn before, the latest cloud made
    // (scaled to fit, so no cloud pops in). Only the very first cloud of all is made here and now:
    // making many on the main thread at once (the first frame has a dozen) caused an ANR.
    val standIn = synchronized(spriteCache) { spriteCache[key]?.let { return it }; lastBySeed[seed] ?: lastMade }
    if (standIn == null) { mainThreadSprites.incrementAndGet(); return cloudSprite(w, h, darkness, seed) }
    if (spritePending.add(key)) {
        try {
            spriteWorker.execute {
                try { cloudSprite(w, h, darkness, seed) } catch (e: Throwable) { } finally { spritePending.remove(key); sceneAssetsReady.intValue++ }
            }
        } catch (e: Exception) { spritePending.remove(key) }
    }
    return standIn
}

/** A soft, volumetric cloud: overlapping lit puffs with a shaded underside (scene.cloud_sprite). */
fun cloudSprite(w: Int, h: Int, darkness: Double, seed: Int): ImageBitmap {
    val dk = Math.round(darkness * 20) / 20.0
    val key = spriteKey(w, h, darkness, seed)
    synchronized(spriteCache) { spriteCache[key]?.let { return it } }
    val img = ImageBitmap(max(1, w), max(1, h))
    val rng = Random(seed.toLong())
    fun u(a: Double, b: Double) = a + (b - a) * rng.nextDouble()
    val lit = mix(Color(255, 255, 255), Color(150, 158, 170), dk)
    val body = mix(Color(236, 240, 246), Color(105, 113, 126), dk)
    val shade = mix(Color(190, 198, 212), Color(62, 68, 80), dk)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
        val baseY = h * 0.72
        val puffs = mutableListOf<Triple<Double, Double, Double>>()
        val n = 6 + rng.nextInt(4)
        for (i in 0 until n) {
            val fx = (i + 0.5) / n
            var r = h * (0.22 + 0.22 * sin(PI * fx)) * u(0.85, 1.15)
            val cx = w * (0.12 + 0.76 * fx) + u(-w * 0.03, w * 0.03)
            val cy = baseY - r * u(0.35, 0.75)
            r = minOf(r, cy - 1, cx - 1, w - cx - 1)
            puffs += Triple(cx, cy, max(2.0, r))
        }
        val base = Triple(w * 0.5, baseY - h * 0.08, w * 0.38)
        (puffs + base).sortedBy { it.third }.forEach { (cx, cy, r) ->
            val brush = safeRadial(0f to lit, 0.55f to body, 1f to body.copy(alpha = 0f),
                center = Offset((cx - r * 0.25).toFloat(), (cy - r * 0.35).toFloat()), radius = (r * 1.25).toFloat())
            if (cx == base.first && cy == base.second && r == base.third) {
                drawOval(brush, Offset((cx - r).toFloat(), (cy - h * 0.16).toFloat()), Size((2 * r).toFloat(), (h * 0.32).toFloat()))
            } else drawCircle(brush, r.toFloat(), Offset(cx.toFloat(), cy.toFloat()))
        }
        drawRect(Brush.verticalGradient(0f to shade.copy(alpha = 0f), 1f to shade.a(200), startY = h * 0.35f, endY = h.toFloat()),
            blendMode = BlendMode.SrcAtop)
    }
    synchronized(spriteCache) {
        spriteCache[key] = img
        lastBySeed[seed] = img
        lastMade = img
        if (lastBySeed.size > 200) lastBySeed.clear()
    }
    return img
}

// ------------------------------------------------------------------ the scene
/**
 * Draws the whole scene into the current DrawScope (its size). rounded: corner radius in px.
 * moonImage: the real Moon picture at a size, or null to draw a simple one.
 */
fun DrawScope.drawScene(e: SceneEngine, rounded: Float = 0f, scrim: Boolean = true, drawn: Drawn? = null,
                        moonImage: ((Int, Conditions) -> ImageBitmap?)? = ::defaultMoonImage,
                        overlay: (DrawScope.() -> Unit)? = null) {
    val w = size.width; val h = size.height
    if (w < 2 || h < 2) return
    val clip = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(rounded))) }
    sceneAssetsReady.intValue                                     // redraw when a cloud made in the background is ready
    clipPath(clip) {
        val c = e.cond
        val (top, bot) = e.shownSkyColors()
        drawRect(Brush.verticalGradient(listOf(top, bot)))
        val amount = e.shownCloudAmount()
        if (c.daylight < 0.6 && amount < 0.95) {
            val vis = (1 - c.daylight / 0.6) * (1 - amount * 0.8)
            for (s in e.stars) {
                val a = vis * (0.55 + 0.45 * sin(e.t * 1.7 + s.phase))
                drawCircle(Color(255, 255, 240).a((220 * max(0.0, a)).toInt()), (s.size * 0.6).toFloat(), Offset(s.x.toFloat(), s.y.toFloat()))
            }
        }
        drawn?.sun = null; drawn?.moon = null
        if (amount < 0.97) {
            val veil = if (amount > 0.4) 1 - max(0.0, amount - 0.4) / 0.6 else 1.0
            if (c.daylight > 0.05) drawSun(e, veil * min(1.0, c.daylight * 1.5), drawn)
            drawMoon(e, veil * (1.0 - DAY_MOON_FADE * c.daylight), drawn, moonImage)
        }
        val layers = e.fading.map { Triple(it.first, it.second, it.third) } + Triple(e.clouds.toList(), c.darkness, e.cloudFade)
        for ((clouds, darkness, alpha) in layers) {
            for (cl in clouds.sortedBy { it.layer }) {
                var dk = darkness * (0.75 + 0.12 * cl.layer)
                if (c.daylight < 0.5) dk = min(1.0, dk + 0.5 * (1 - c.daylight * 2))
                val spr = cloudSpriteOrLater(cl.w, cl.h, dk, cl.seed) ?: continue
                val al = (cl.opacity * (if (c.daylight > 0.2) 0.9 else 0.8) * alpha).toFloat().coerceIn(0f, 1f)
                if (spr.width == max(1, cl.w) && spr.height == max(1, cl.h)) drawImage(spr, Offset(cl.x.toFloat(), cl.y.toFloat()), alpha = al)
                else drawImage(spr, IntOffset.Zero, IntSize(spr.width, spr.height),            // a stand-in, scaled, until the exact one is ready
                    IntOffset(cl.x.roundToInt(), cl.y.roundToInt()), IntSize(max(1, cl.w), max(1, cl.h)), alpha = al)
            }
        }
        for (old in e.oldWeather) drawWeather(e, old, old.alpha)
        drawWeather(e, e.weather, e.cloudFade)
        val bolt = e.bolt
        if (c.kind == Kind.THUNDER && e.flash > 0) {
            if (bolt != null && e.flash > 0.45) {
                val p = Path().apply {
                    moveTo(bolt[0].first.toFloat(), bolt[0].second.toFloat())
                    bolt.drop(1).forEach { lineTo(it.first.toFloat(), it.second.toFloat()) }
                }
                drawPath(p, Color(180, 200, 255, 90), style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, Color(255, 255, 255, 240), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val flicker = e.flash * (0.6 + 0.4 * abs(sin(e.flash * 25)))
            drawRect(Color(235, 240, 255).a((120 * flicker).toInt()))
        }
        if (scrim) drawRect(Brush.horizontalGradient(0f to Color(0, 0, 0, 105), 1f to Color(0, 0, 0, 0), startX = 0f, endX = w * 0.7f))
        overlay?.invoke(this)
    }
}

private fun DrawScope.drawWeather(e: SceneEngine, wx: Weather, opacity: Double) {
    if (opacity <= 0.0) return
    val c = wx.cond
    val w = size.width; val h = size.height
    val op = opacity.toFloat().coerceIn(0f, 1f)
    if (wx.fog.isNotEmpty()) {
        val fogCol = mix(Color(70, 78, 88), Color(235, 238, 240), c.daylight)
        for (f in wx.fog) {
            val ctr = Offset(f.x.toFloat(), f.y.toFloat())
            val brush = safeRadial(listOf(fogCol.copy(alpha = (f.alpha * c.intensity).toFloat().coerceIn(0f, 1f)), fogCol.copy(alpha = 0f)), ctr, w * 0.55f)
            drawOval(brush, Offset(ctr.x - w * 0.55f, ctr.y - h * 0.16f), Size(w * 1.1f, h * 0.32f), alpha = op)
        }
    }
    if (wx.drops.isNotEmpty()) {
        val heavy = c.kind != Kind.DRIZZLE
        val slant = (e.precipDirection() * min(0.45, 0.06 + c.windKmh / 120)).toFloat()
        val length = h * (if (heavy) 0.075f else 0.035f)
        val base = mix(Color(150, 170, 200), Color(205, 222, 245), c.daylight)
        for ((band, alpha) in listOf(0.85 to 110, 1.0 to 170)) {
            val col = base.a(if (heavy) alpha else (alpha * 0.7).toInt())
            val stroke = if (band == 1.0) 1.4f else 1.0f
            for (d in wx.drops) {
                if ((d.speed > 0.88) != (band == 1.0)) continue
                val x = d.x.toFloat(); val y = d.y.toFloat(); val sp = d.speed.toFloat()
                drawLine(col, Offset(x, y), Offset(x - slant * length * sp, y - length * sp), stroke, StrokeCap.Round, alpha = op)
            }
        }
        for (s in wx.splashes) {
            val k = (s.age / 0.25).toFloat()
            val rw = 2 + 7 * k
            drawArc(base.a((150 * (1 - k)).toInt()), 180f, 180f, false, Offset(s.x.toFloat() - rw, s.y.toFloat() - rw * 0.35f),
                Size(2 * rw, rw * 0.7f), alpha = op, style = Stroke(1f))
        }
    }
    for (f in wx.flakes) drawCircle(Color(255, 255, 255).a((150 + 30 * f.size).toInt()), f.size.toFloat(), Offset(f.x.toFloat(), f.y.toFloat()), alpha = op)
}

private fun DrawScope.drawSun(e: SceneEngine, alpha: Double, drawn: Drawn?) {
    if (alpha <= 0.01) return
    val c = e.cond
    val w = size.width; val h = size.height
    val rad = min(w, h) * 0.11f
    val prog = c.sunProgressAt(e.clock())
    val pt = arcPoint(prog, 0f, 0f, w, h, rad) ?: return
    val (cx, cy) = pt
    drawn?.sun = Triple(cx, cy, prog)
    val warm = max(c.twilight, 1 - sin(PI * prog) * 1.6)
    val core = mix(Color(255, 248, 215), Color(255, 190, 110), warm.coerceIn(0.0, 1.0))
    val a = alpha.toFloat().coerceIn(0f, 1f)
    val ctr = Offset(cx, cy)
    drawCircle(safeRadial(0f to core.a(150), 0.35f to core.a(55), 1f to core.a(0), center = ctr, radius = rad * 4.2f), rad * 4.2f, ctr, alpha = a)
    translate(cx, cy) {
        rotate((e.t * 6).toFloat() % 360f, Offset.Zero) {
            val ray = core.a(40)
            for (i in 0 until 12) {
                rotate(30f * (i + 1), Offset.Zero) {
                    val p = Path().apply {
                        moveTo(-rad * 0.18f, 0f)
                        lineTo(0f, -rad * (2.4f + 0.25f * sin(e.t * 2 + i).toFloat()))
                        lineTo(rad * 0.18f, 0f); close()
                    }
                    drawPath(p, ray, alpha = a)
                }
            }
        }
    }
    drawCircle(safeRadial(listOf(Color(255, 255, 245), core), Offset(cx - rad * 0.3f, cy - rad * 0.3f), rad * 1.3f), rad, ctr, alpha = a)
}

fun defaultMoonImage(px: Int, c: Conditions): ImageBitmap? {
    if (!MoonRender.available || c.moonPhaseAngle == null) return null
    // on the main thread never wait for it (see MoonRender.renderLater); widgets draw on their own thread and can wait
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        MoonRender.ready.intValue                                   // redraw when it's ready
        return MoonRender.renderLater(px, c.moonPhaseAngle, c.moonLimb, c.southern, c.moonRotation)?.asImageBitmap()
    }
    return MoonRender.render(px, c.moonPhaseAngle, c.moonLimb, c.southern, c.moonRotation)?.asImageBitmap()
}

private fun DrawScope.drawMoon(e: SceneEngine, alpha: Double, drawn: Drawn?, moonImage: ((Int, Conditions) -> ImageBitmap?)?) {
    val c = e.cond
    if (alpha <= 0.01 || !c.moonUp) return
    val w = size.width; val h = size.height
    val rad = min(w, h) * 0.09f
    val prog = c.moonProgressAt(e.clock())
    val pt = arcPoint(prog, 0f, 0f, w, h, rad) ?: return
    val (cx, cy) = pt
    drawn?.moon = Triple(cx, cy, prog!!)
    val day = c.daylight.coerceIn(0.0, 1.0)
    val a = alpha.toFloat().coerceIn(0f, 1f)
    val ctr = Offset(cx, cy)
    drawCircle(safeRadial(listOf(Color(220, 230, 255).a((70 * (1 - day)).toInt()), Color(220, 230, 255, 0)), ctr, rad * 3), rad * 3, ctr, alpha = a)
    val px = max(16, ((rad * 2 / 8).roundToInt()) * 8)
    val img = try { moonImage?.invoke(px, c) } catch (ex: Exception) { null }
    if (img != null) {
        val dst = IntSize((2 * rad).roundToInt(), (2 * rad).roundToInt())
        val off = IntOffset((cx - rad).roundToInt(), (cy - rad).roundToInt())
        if (day < 1.0) drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), off, dst, alpha = (a * (1 - day)).toFloat())
        if (day > 0.0) drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), off, dst, alpha = (a * day).toFloat(), blendMode = BlendMode.Screen)
        return
    }
    // the simple drawn Moon (no textures): disc, maria, then the phase
    val pale = Color(238, 238, 226)
    drawCircle(pale, rad, ctr, alpha = a)
    drawOval(Color(200, 200, 190, 120), Offset(cx - rad * 0.3f - rad * 0.28f, cy - rad * 0.2f - rad * 0.22f), Size(rad * 0.56f, rad * 0.44f), alpha = a)
    drawOval(Color(200, 200, 190, 120), Offset(cx + rad * 0.25f - rad * 0.2f, cy + rad * 0.25f - rad * 0.16f), Size(rad * 0.4f, rad * 0.32f), alpha = a)
    val age = c.moonAge
    val illum = (1 - cos(age * 2 * PI)) / 2
    if (illum < 0.985) {
        val litRight = (age < 0.5) != c.southern
        val disc = Rect(cx - rad, cy - rad, cx + rad, cy + rad)
        val half = Path().apply {
            moveTo(cx, cy - rad)
            arcTo(disc, -90f, if (litRight) 180f else -180f, false)
            close()
        }
        val tw = rad * abs(1 - 2 * illum).toFloat()
        val ell = Path().apply { addOval(Rect(cx - tw, cy - rad, cx + tw, cy + rad)) }
        val lit = Path().apply { op(half, ell, if (illum >= 0.5) PathOperation.Union else PathOperation.Difference) }
        drawCircle(mix(e.shownSkyColors().first, Color(70, 72, 80), 0.35), rad * 1.01f, ctr, alpha = a)
        drawPath(lit, pale, alpha = a)
    }
}
