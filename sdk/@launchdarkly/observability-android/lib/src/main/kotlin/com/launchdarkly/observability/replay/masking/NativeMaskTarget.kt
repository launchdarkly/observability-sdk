package com.launchdarkly.observability.replay.masking

import android.view.View
import android.widget.EditText
import android.widget.TextView
import kotlin.text.lowercase
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 *   Native view target
 */
data class NativeMaskTarget(
    override val view: View,
) : MaskTarget {

    override fun isTextInput(): Boolean {
        return view is EditText
    }

    override fun isText(): Boolean {
        return view is TextView
    }

    override fun isSensitive(sensitiveKeywords: List<String>): Boolean {
        val lowerDesc = view.contentDescription?.toString()?.lowercase() ?: return false
        return sensitiveKeywords.any { keyword -> lowerDesc.contains(keyword) }
    }

    override fun maskRect(): ComposeRect? {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + view.width
        val bottom = top + view.height

        return ComposeRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }
}


