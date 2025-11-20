package com.launchdarkly.observability.replay

import android.view.View
import androidx.compose.ui.semantics.SemanticsConfiguration

/**
 * Native view target; [config] is always null.
 */
data class NativeMaskTarget(
    override val view: View,
) : MaskTarget


