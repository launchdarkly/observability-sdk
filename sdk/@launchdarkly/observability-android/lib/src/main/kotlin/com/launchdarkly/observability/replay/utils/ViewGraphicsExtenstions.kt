package com.launchdarkly.observability.replay.utils

import android.view.View

/**
 * Returns this view's top-left screen coordinates as a Pair of Floats (x, y).
 */
fun View.locationOnScreen(): Pair<Float, Float> {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    return loc[0].toFloat() to loc[1].toFloat()
}
