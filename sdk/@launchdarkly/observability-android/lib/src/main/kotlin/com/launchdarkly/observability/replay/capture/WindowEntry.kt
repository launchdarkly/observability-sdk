package com.launchdarkly.observability.replay.capture

import android.view.View


enum class WindowType {
    ACTIVITY,
    DIALOG,
    POPUP,
    OTHER
}

data class WindowEntry(
    val rootView: View,
    var type: WindowType,
    val wmType: Int,
    val width: Int,
    val height: Int,
)


