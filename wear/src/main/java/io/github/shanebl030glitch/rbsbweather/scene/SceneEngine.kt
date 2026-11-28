// The animated sky's moving parts, as on the desktop (scene.py WeatherScene): three cloud layers
// (and a lower deck in heavy cloud) drifting at the forecast's wind, easing to a new wind over a
// few seconds; rain slanted by the wind with splashes, drizzle, swaying snow, rolling fog,
// lightning strikes; stars; and cross-fades when the weather changes. Plain Kotlin: it is moved
// on by advance(dt) and drawn by SceneDraw.kt, so the app, playback and the home-screen widget
// all share it. Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/ui/scene/SceneEngine.kt. Watch changes:
//  - package name;
//  - quality levels (Quality.kt): [quality] scales the numbers of clouds, drops, flakes, stars and
//    splashes (HIGH = the phone's own detail), with minimums so every effect stays at every level;
//    changing the level rebuilds the scene.
package io.github.shanebl030glitch.rbsbweather.scene

import androidx.compose.ui.graphics.Color
import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun mix(a: Color, b: Color, t: Double): Color {
    val f = t.coerceIn(0.0, 1.0).toFloat()
    return Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f,
        a.blue + (b.blue - a.blue) * f, a.alpha + (b.alpha - a.alpha) * f)
}


class Cloud(var x: Double, val y: Double, val w: Int, val h: Int, val layer: Int, val speed: Double, val opacity: Double, val seed: Int)
class Drop(var x: Double, var y: Double, val speed: Double, var splash: Double)
class Splash(val x: Double, val y: Double, var age: Double)
class Flake(var x: Double, var y: Double, val size: Double, var phase: Double, val speed: Double)
class FogBand(var x: Double, val y: Double, val speed: Double, val alpha: Double)
class Star(val x: Double, val y: Double, val size: Double, val phase: Double)

/** Rain, snow and fog of one kind of weather (current, or earlier weather fading out). */
class Weather(var cond: Conditions, val drops: MutableList<Drop> = mutableListOf(), var splashes: MutableList<Splash> = mutableListOf(),
              val flakes: MutableList<Flake> = mutableListOf(), val fog: MutableList<FogBand> = mutableListOf(), var alpha: Double = 1.0)

class SceneEngine(private val seed: Int = 7, var clock: () -> Long = System::currentTimeMillis) {
    var cond = Conditions(); private set
    var t = 0.0; private set
    var width = 0; private set
    var height = 0; private set
    /** False: a still picture (fades are instant, nothing moves). */
    var animations = true

    /** Detail level (watch change). Changing it rebuilds clouds and particles for the new detail. */
    var quality: Quality = Quality.HIGH
        set(value) {
            if (value == field) return
            field = value
            endFades()
            if (width > 1) rebuild()
        }

    private fun particles(n: Int): Int = if (n <= 0) 0 else max(Quality.MIN_PARTICLES, Math.round(n * quality.particles).toInt())

    private val rng = Random(seed.toLong())
    val clouds = mutableListOf<Cloud>()
    var weather = Weather(cond); private set
    val stars = mutableListOf<Star>()
    var flash = 0.0; private set
    var bolt: List<Pair<Double, Double>>? = null; private set
    private var nextStrike = 3.0
    var driftEast: Double? = null; private set
    var driftSpeed: Double? = null; private set
    private var realWindShown = false
    /** Earlier cloud layouts fading out: (clouds, darkness, opacity). */
    val fading = mutableListOf<Triple<List<Cloud>, Double, Double>>()
    var cloudFade = 1.0; private set
    val oldWeather = mutableListOf<Weather>()
    private var skyFrom: Pair<Color, Color>? = null
    private var amountFrom: Double? = null

    fun setSize(w: Int, h: Int) {
        if (w == width && h == height) return
        endFades()
        width = w; height = h
        rebuild()
    }

    fun setConditions(c: Conditions) {
        val key = { x: Conditions -> Triple(x.kind, (x.intensity * 100).roundToInt(), (x.darkness * 100).roundToInt()) }
        val changed = key(c) != key(cond)
        val oldDark = cond.darkness
        val fade = changed && width > 1 && animations
        if (fade) {
            skyFrom = shownSkyColors()
            amountFrom = shownCloudAmount()
        }
        cond = c
        if (driftEast == null || (c.fromForecast && !realWindShown)) {
            driftEast = c.windEast; driftSpeed = cloudSpeed(c.windKmh)
        }
        realWindShown = realWindShown || c.fromForecast
        if (fade) {
            if (clouds.isNotEmpty()) {
                fading += Triple(clouds.toList(), oldDark, cloudFade)
                if (fading.size > 3) { fading.sortBy { it.third }; fading.removeAt(0) }
            }
            if (weather.drops.isNotEmpty() || weather.flakes.isNotEmpty() || weather.fog.isNotEmpty()) {
                weather.alpha = cloudFade
                oldWeather += weather
                if (oldWeather.size > 2) { oldWeather.sortBy { it.alpha }; oldWeather.removeAt(0) }
            }
            cloudFade = 0.0
        }
        if (changed || clouds.isEmpty()) rebuild() else weather.cond = c
    }

    private fun endFades() {
        fading.clear(); oldWeather.clear(); cloudFade = 1.0
        skyFrom = null; amountFrom = null
    }

    private fun uniform(r: Random, a: Double, b: Double) = a + (b - a) * r.nextDouble()

    /** (Re)create clouds, stars, rain, snow and fog for the current size and weather. */
    private fun rebuild() {
        val w = max(1, width); val h = max(1, height)
        val r = Random(seed.toLong())
        val c = cond
        val area = (w.toDouble() * h) / (600 * 220)
        stars.clear()
        repeat(particles((70 * area).toInt() + 10)) { stars += Star(r.nextDouble() * w, r.nextDouble() * h * 0.75, uniform(r, 0.6, 1.6), uniform(r, 0.0, Math.PI * 2)) }
        val amount = c.cloudAmount
        clouds.clear()
        val layers = listOf(Triple(0.55, 0.35, 0.6), Triple(0.8, 0.6, 0.85), Triple(1.0, 1.0, 1.0))
        val perLayer = if (amount <= 0.01) 0 else max(1, Math.round((1 + amount * 4 * max(1.0, w / 500.0)) * quality.clouds).toInt())
        layers.forEachIndexed { li, (scale, speed, opacity) ->
            if (amount < 0.3 && li == 0) return@forEachIndexed
            for (i in 0 until perLayer) {
                val cw = (w * uniform(r, 0.32, 0.5) * scale * (0.8 + amount * 0.5)).toInt()
                val ch = (cw * uniform(r, 0.42, 0.55)).toInt()
                val x = (i + r.nextDouble() * 0.6) / perLayer * (w + cw) - cw * 0.5
                val ytop = if (amount > 0.8) -ch * 0.25 else h * 0.02
                val y = uniform(r, ytop, h * (0.18 + 0.25 * (1 - amount))) - ch * 0.15 * li
                clouds += Cloud(x, y, max(1, cw), max(1, ch), li, speed, opacity * (0.75 + 0.25 * amount), seed * 100 + li * 10 + i)
            }
        }
        if (amount >= 0.8) {
            val nDeck = max(2, (3 * max(1.0, w / 400.0) * quality.clouds).toInt())
            for (i in 0 until nDeck) {
                val cw = (w * uniform(r, 0.45, 0.65)).toInt()
                val ch = (cw * uniform(r, 0.38, 0.48)).toInt()
                val x = (i + r.nextDouble() * 0.5) / nDeck * (w + cw) - cw * 0.5
                val y = uniform(r, h * 0.12, h * 0.32)
                clouds += Cloud(x, y, max(1, cw), max(1, ch), -1, 0.25, 0.55 + 0.3 * c.darkness, seed * 100 + 90 + i)
            }
        }
        val wx = Weather(c)
        if (c.kind == Kind.RAIN || c.kind == Kind.THUNDER || c.kind == Kind.DRIZZLE) {
            val heavy = c.kind != Kind.DRIZZLE
            val n = particles(if (heavy) ((60 + 200 * c.intensity) * area).toInt() else ((90 + 140 * c.intensity) * area).toInt())
            repeat(n) { wx.drops += Drop(r.nextDouble() * w, r.nextDouble() * h, uniform(r, 0.75, 1.0), r.nextDouble()) }
        }
        if (c.kind == Kind.SNOW) {
            repeat(particles(((40 + 160 * c.intensity) * area).toInt())) {
                wx.flakes += Flake(r.nextDouble() * w, r.nextDouble() * h, uniform(r, 1.0, 3.2), uniform(r, 0.0, Math.PI * 2), uniform(r, 0.6, 1.2))
            }
        }
        if (c.kind == Kind.FOG || (c.kind == Kind.DRIZZLE && c.intensity > 0.6)) {
            for (i in 0 until 5) wx.fog += FogBand(r.nextDouble() * w, h * (0.3 + 0.14 * i), uniform(r, 8.0, 18.0), uniform(r, 0.25, 0.5))
        }
        weather = wx
    }

    // ------------------------------------------------------------------ motion
    private fun easeWind(dt: Double) {
        val east = cond.windEast; val speed = cloudSpeed(cond.windKmh)
        if (driftEast == null) { driftEast = east; driftSpeed = speed; return }
        val a = 1.0 - exp(-max(0.0, dt) / WIND_EASE_S)
        driftEast = driftEast!! + (east - driftEast!!) * a
        driftSpeed = driftSpeed!! + (speed - driftSpeed!!) * a
    }

    /** -1..+1: the side the shown wind blows to (passes through 0 while the wind turns). */
    fun precipDirection(): Double = ((driftEast ?: cond.windEast) / MIN_EAST_WEST).coerceIn(-1.0, 1.0)

    fun advance(dt: Double) {
        t += dt
        val w = width; val h = height
        if (w <= 1) return
        easeWind(dt)
        val vx = (driftEast ?: 1.0) * (driftSpeed ?: 0.0) * w
        if (cloudFade < 1.0 || fading.isNotEmpty() || oldWeather.isNotEmpty()) {
            val step = dt / CLOUD_FADE_S
            cloudFade = min(1.0, cloudFade + step)
            val f2 = fading.map { Triple(it.first, it.second, it.third - step) }.filter { it.third > 0.0 }
            fading.clear(); fading += f2
            oldWeather.forEach { it.alpha -= step }
            oldWeather.removeAll { it.alpha <= 0.0 }
            if (cloudFade >= 1.0) { skyFrom = null; amountFrom = null }
        }
        for (cl in clouds + fading.flatMap { it.first }) {
            cl.x += vx * cl.speed * dt
            if (cl.x > w + 10) cl.x = -cl.w - 10.0 else if (cl.x < -cl.w - 10) cl.x = w + 10.0
        }
        advanceWeather(weather, dt)
        oldWeather.forEach { advanceWeather(it, dt) }
        if (cond.kind == Kind.THUNDER) {
            flash = max(0.0, flash - dt * 2.6)
            nextStrike -= dt
            if (nextStrike <= 0) {
                strike()
                nextStrike = uniform(rng, 2.5, 7.0) / (0.6 + 0.6 * cond.intensity)
            }
        }
    }

    private fun advanceWeather(wx: Weather, dt: Double) {
        val w = width.toDouble(); val h = height.toDouble()
        val c = wx.cond
        val wind = 8 + min(c.windKmh, 80.0) * 0.9
        val sign = precipDirection()
        val slant = sign * min(0.45, 0.06 + c.windKmh / 120)
        if (wx.drops.isNotEmpty()) {
            val heavy = c.kind != Kind.DRIZZLE
            val fall = h * (if (heavy) 2.6 else 1.3)
            for (d in wx.drops) {
                d.y += fall * d.speed * dt
                d.x += fall * d.speed * slant * dt
                if (d.y > h) {
                    if (heavy && d.splash < 0.5 && wx.splashes.size < quality.maxSplashes) wx.splashes.add(Splash(d.x, h - uniform(rng, 2.0, 14.0), 0.0))
                    d.y -= h + 20
                    d.x = rng.nextDouble() * w
                    d.splash = rng.nextDouble()
                }
                if (d.x > w + 20) d.x -= w + 40 else if (d.x < -20) d.x += w + 40
            }
            wx.splashes.forEach { it.age += dt }
            wx.splashes.removeAll { it.age >= 0.25 }
        }
        for (f in wx.flakes) {
            f.y += (14 + 22 * f.size) * f.speed * dt
            f.phase += dt * 1.4
            f.x += (kotlin.math.sin(f.phase) * 14 + sign * wind * 0.4) * dt
            if (f.y > h + 4) { f.y = -4.0; f.x = rng.nextDouble() * w }
            if (f.x > w + 6) f.x = -6.0 else if (f.x < -6) f.x = w + 6
        }
        for (fg in wx.fog) {
            fg.x += sign * fg.speed * dt
            if (fg.x > w * 1.5) fg.x = -w * 0.5 else if (fg.x < -w * 0.5) fg.x = w * 1.5
        }
    }

    private fun strike() {
        val w = width.toDouble(); val h = height.toDouble()
        var x = uniform(rng, w * 0.35, w * 0.9)
        var y = 0.0
        val pts = mutableListOf(x to y)
        val stop = h * uniform(rng, 0.55, 0.85)
        while (y < stop) {
            y += uniform(rng, 8.0, 20.0)
            x += uniform(rng, -14.0, 14.0)
            pts += x to y
        }
        bolt = pts
        flash = 1.0
    }

    // ------------------------------------------------------------------- colours
    fun shownCloudAmount(): Double {
        val a = cond.cloudAmount
        val from = amountFrom
        return if (from != null && cloudFade < 1.0) from + (a - from) * cloudFade else a
    }

    fun shownSkyColors(): Pair<Color, Color> {
        val (top, bot) = skyColors()
        val from = skyFrom
        return if (from != null && cloudFade < 1.0) mix(from.first, top, cloudFade) to mix(from.second, bot, cloudFade) else top to bot
    }

    fun skyColors(): Pair<Color, Color> {
        val c = cond
        var top = mix(SKY_NIGHT_TOP, SKY_DAY_TOP, c.daylight)
        var bot = mix(SKY_NIGHT_BOT, SKY_DAY_BOT, c.daylight)
        if (c.twilight > 0) {
            top = mix(top, SKY_TW_TOP, c.twilight * 0.7)
            bot = mix(bot, SKY_TW_BOT, c.twilight * 0.85)
        }
        var grey = if (c.cloudAmount > 0.5) min(1.0, c.cloudAmount * 0.75 + c.darkness * 0.35) else 0.0
        var gTop = mix(Color(0xFF1B2129), Color(0xFF7C8998), c.daylight)
        var gBot = mix(Color(0xFF39424F), Color(0xFFBAC4CD), c.daylight)
        if (c.kind == Kind.THUNDER) {
            gTop = mix(Color(0xFF14181E), Color(0xFF2C333C), c.daylight); gBot = mix(Color(0xFF2A3038), Color(0xFF5B6570), c.daylight)
        }
        if (c.kind == Kind.FOG) {
            gTop = mix(Color(0xFF2A3038), Color(0xFFA9B2BA), c.daylight); gBot = mix(Color(0xFF3C434C), Color(0xFFD3D8DC), c.daylight)
            grey = 0.85
        }
        return mix(top, gTop, grey) to mix(bot, gBot, grey)
    }

    companion object {
        val SKY_DAY_TOP = Color(0xFF2F7FD6); val SKY_DAY_BOT = Color(0xFF9FD2F6)
        val SKY_NIGHT_TOP = Color(0xFF0A1430); val SKY_NIGHT_BOT = Color(0xFF24365C)
        val SKY_TW_TOP = Color(0xFF40548F); val SKY_TW_BOT = Color(0xFFF2A15C)
    }
}
