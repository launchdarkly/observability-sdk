package com.launchdarkly.observability.replay.capture

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING

/**
 * A [Window] this process currently has on screen, as reported by
 * [WindowInspector.topmostAppWindow].
 *
 * [activity] is best effort: touch capture only needs the window, while screen reporting needs the
 * activity, so a window whose activity cannot be resolved is still useful.
 */
data class OnScreenWindow(val window: Window, val activity: Activity?)

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


