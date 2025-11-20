package com.launchdarkly.observability.replay

import android.view.View
import androidx.compose.ui.semantics.SemanticsConfiguration

/**
 * Basic view information used by [MaskMatcher] to evaluate whether a view should be masked.
 *
 * - [view]: the underlying Android [View] (Compose or native).
 * - [config]: the Compose [SemanticsConfiguration] when available (Compose), otherwise null (native).
 */
data class MaskViewInfo(
    val view: View,
    val config: SemanticsConfiguration?,
)

/**
 * A [MaskMatcher] can determine if a [MaskViewInfo] is a match and should be masked with an
 * opaque mask in the session replay.
 *
 * Implement this interface and provide as part of a [PrivacyProfile] to customize masking behavior.
 *
 * Matchers should not do heavy work, should execute synchronously, and not dispatch to other
 * threads for performance reasons.  If you add a matcher and notice jitter, this may be
 * the cause.
 */
interface MaskMatcher {
    /**
     * @return true if the target is a match, false otherwise
     */
    fun isMatch(target: MaskViewInfo): Boolean
}