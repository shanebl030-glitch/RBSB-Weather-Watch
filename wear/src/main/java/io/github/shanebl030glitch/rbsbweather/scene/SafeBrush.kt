// Gradients that can't crash the app. Real Android refuses a radial gradient with a radius of 0
// ("ending radius must be > 0"), which happens on the first frame before a view is measured; that
// closed the app when Shane tapped Earth Globe (0.5.0). Every radial gradient in the app goes
// through here (a test checks no other code calls Brush.radialGradient directly).
// Created By Shane BlackbuRn
// Copied from RBSB-Weather for Android v0.7.2 (commit cdf422d94e78cfc1020db5ac3407c765dd6b6738),
// app/src/main/java/io/github/shanebl030glitch/rbsbweather/ui/scene/SafeBrush.kt. Watch changes: package name only.
package io.github.shanebl030glitch.rbsbweather.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The smallest radius handed to Android; anything smaller (0, negative or NaN) becomes this. */
const val MIN_GRADIENT_RADIUS = 0.5f

fun safeRadius(r: Float): Float = if (r.isNaN() || r < MIN_GRADIENT_RADIUS) MIN_GRADIENT_RADIUS else r

fun safeRadial(vararg stops: Pair<Float, Color>, center: Offset, radius: Float): Brush =
    Brush.radialGradient(*stops, center = center, radius = safeRadius(radius))

fun safeRadial(colors: List<Color>, center: Offset, radius: Float): Brush =
    Brush.radialGradient(colors, center, safeRadius(radius))
