package com.launchdarkly.observability.replay

import androidx.compose.ui.semantics.SemanticsNode

/**
 * A [MaskMatcher] can determine if a [SemanticsNode] is a match and should be masked with an
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
     * @return true if node is a match, false otherwise
     */
    fun isMatch(node: SemanticsNode): Boolean
}
