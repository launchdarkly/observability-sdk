package com.launchdarkly.observability.replay.masking
import android.graphics.RectF
import androidx.compose.ui.graphics.Matrix

data class Mask(
    val rect: RectF,
    val viewId: Int,
    val matrix: Matrix? = null
)

