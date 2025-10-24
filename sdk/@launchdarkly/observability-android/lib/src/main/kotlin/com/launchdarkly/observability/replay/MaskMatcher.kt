package com.launchdarkly.observability.replay

import androidx.compose.ui.semantics.SemanticsNode

interface MaskMatcher {
    /**
     * @return true if node is a match, false otherwise
     */
    fun isMatch(node: SemanticsNode): Boolean
}