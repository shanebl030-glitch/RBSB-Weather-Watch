// Rotary input (bezel or crown) for the sky screen. Compose's onRotaryScrollEvent only receives
// events when the screen holds input focus; the activity also passes on any rotary event no view
// used (MainActivity.onGenericMotionEvent), so turning the bezel always works.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import kotlinx.coroutines.channels.Channel

object RotaryInput {
    /** Scroll amounts in pixels (positive = clockwise / down). */
    val events = Channel<Float>(Channel.UNLIMITED)

    fun send(pixels: Float) {
        try { android.util.Log.i("RBSBInput", "ROTARY activity $pixels px") } catch (e: Throwable) { }
        events.trySend(pixels)
    }

    /** A rotary event seen by the sky screen itself (Compose focus path); logged for the emulator test. */
    fun seen(pixels: Float) {
        try { android.util.Log.i("RBSBInput", "ROTARY compose $pixels px") } catch (e: Throwable) { }
    }

    /** Pixels of rotation that move one sample. */
    const val STEP_PIXELS = 60f
}
