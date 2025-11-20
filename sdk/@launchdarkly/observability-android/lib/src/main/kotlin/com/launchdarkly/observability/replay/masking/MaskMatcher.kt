package com.launchdarkly.observability.replay.masking

import android.view.View
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 * Target to evaluate for masking. Sealed to restrict implementations to this module/package.
 */
sealed interface MaskTarget {
    val view: View
    fun isTextInput(): Boolean
    fun isText(): Boolean
    fun isSensitive(sensitiveKeywords: List<String>): Boolean
    fun maskRect(): ComposeRect?
}


/**
 * A [MaskMatcher] can determine if a [MaskTarget] is a match and should be masked with an
 * opaque mask in the session replay.
 *
 * Implement this interface and provide as part of a [com.launchdarkly.observability.replay.PrivacyProfile] to customize masking behavior.
 *
 * Matchers should not do heavy work, should execute synchronously, and not dispatch to other
 * threads for performance reasons.  If you add a matcher and notice jitter, this may be
 * the cause.
 */
interface MaskMatcher {
    /**
     * @return true if the target is a match, false otherwise
     */
    fun isMatch(target: MaskTarget): Boolean
}