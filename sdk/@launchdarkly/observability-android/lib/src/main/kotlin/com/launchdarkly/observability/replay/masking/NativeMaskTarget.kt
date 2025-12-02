package com.launchdarkly.observability.replay.masking

import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.text.method.PasswordTransformationMethod
import kotlin.text.lowercase
import com.launchdarkly.observability.R
import android.graphics.RectF

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
        if (view is TextView) {
            // Treat password fields as sensitive (analogous to Compose SemanticsProperties.Password)
            if (view.transformationMethod is PasswordTransformationMethod) {
                return true
            }

            // Check actual displayed text
            val lowerText = view.text?.toString()?.lowercase()
            if (!lowerText.isNullOrEmpty() && sensitiveKeywords.any { keyword -> lowerText.contains(keyword) }) {
                return true
            }

            // Check hint text, which often contains labels like "email", "password", etc.
            val lowerHint = view.hint?.toString()?.lowercase()
            if (!lowerHint.isNullOrEmpty() && sensitiveKeywords.any { keyword -> lowerHint.contains(keyword) }) {
                return true
            }
        }

        // Fallback to contentDescription check
        val lowerDesc = view.contentDescription?.toString()?.lowercase()
        return !lowerDesc.isNullOrEmpty() && sensitiveKeywords.any { keyword -> lowerDesc.contains(keyword) }
    }

    override fun mask(): Mask {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val rect = RectF(left, top, left + view.width, top + view.height)
        return Mask(rect, view.id, matrix = null)
    }

    override fun hasLDMask(): Boolean {
        return view.getTag(R.id.ld_mask_tag) as? Boolean ?: false
    }


}
