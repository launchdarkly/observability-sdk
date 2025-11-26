package com.launchdarkly.observability.replay.capture

import android.graphics.Rect
import android.view.View


enum class WindowType {
    ACTIVITY,
    DIALOG,
    OTHER
}

data class WindowEntry(
    val rootView: View,
    var type: WindowType,
    val wmType: Int,
    val width: Int,
    val height: Int,
    val screenLeft: Int,
    val screenTop: Int
) {
    fun rect(): Rect {
        return Rect(0, 0, width, height)
    }
}


