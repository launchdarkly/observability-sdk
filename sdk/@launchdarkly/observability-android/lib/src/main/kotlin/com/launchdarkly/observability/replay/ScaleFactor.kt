package com.launchdarkly.observability.replay

import android.view.View
import kotlin.math.roundToInt

fun calculateScaleFactor(scale: Float?, view: View): Float {
    if (scale == null) return 1f
    val density = view.resources.displayMetrics.density
    return if (density > 0f) scale / density else 1f
}

fun scaleCoordinate(value: Float, scaleFactor: Float): Int {
    return (value * scaleFactor).roundToInt()
}
