package com.launchdarkly.observability.replay.masking

import android.view.View

/**
 * Native view target; [config] is always null.
 */
data class NativeMaskTarget(
    override val view: View,
) : MaskTarget


