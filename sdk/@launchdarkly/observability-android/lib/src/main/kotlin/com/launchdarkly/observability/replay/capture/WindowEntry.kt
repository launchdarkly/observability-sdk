package com.launchdarkly.observability.replay.capture

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING


enum class WindowType {
    ACTIVITY,
    DIALOG,
    OTHER
}

data class WindowEntry(
    val rootView: View,
    var type: WindowType,
    val layoutParams: WindowManager.LayoutParams?,
    val width: Int,
    val height: Int,
    val screenLeft: Int,
    val screenTop: Int
) {
    fun rect(): Rect {
        return Rect(0, 0, width, height)
    }

    fun isPixelCopyCandidate(): Boolean {
        if (type != WindowType.ACTIVITY) {
            return false
        }

        if (layoutParams?.type == TYPE_APPLICATION_STARTING) { // Starting/Splash screen
            return false
        }

        if (((layoutParams?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            // Secure window
            return false
        }

        return true
    }
}


