package com.launchdarkly.observability.replay

import android.view.View
import kotlin.math.roundToInt

fun calculateScaleFactor(scale: Double?, view: View): Double {
    if (scale == null) return 1.0
    val density = view.resources.displayMetrics.density.toDouble()
    return if (density > 0.0) scale / density else 1.0
}

fun scaleCoordinate(value: Float, scaleFactor: Double): Int {
    return (value * scaleFactor).roundToInt()
}
