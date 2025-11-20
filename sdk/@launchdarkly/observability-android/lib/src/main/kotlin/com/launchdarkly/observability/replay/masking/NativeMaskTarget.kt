package com.launchdarkly.observability.replay.masking

import android.view.View
import android.widget.EditText
import android.widget.TextView
import kotlin.text.lowercase

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
}


