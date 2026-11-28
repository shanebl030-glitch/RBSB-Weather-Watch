// Rotary input (bezel or crown) for the sky screen. Compose's onRotaryScrollEvent only receives
// events when the screen holds input focus; the activity also passes on any rotary event no view
// used (MainActivity.onGenericMotionEvent), so turning the bezel always works.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sky

import kotlinx.coroutines.channels.Channel

object RotaryInput {
    /** Scroll amounts in pixels (positive = clockwise / down). */
    val events = Channel<Float>(Channel.UNLIMITED)

    fun send(pixels: Float) { events.trySend(pixels) }

    /** Pixels of rotation that move one sample. */
    const val STEP_PIXELS = 60f
}
