package com.launchdarkly.observability.replay

import android.view.View
import androidx.compose.ui.semantics.SemanticsConfiguration

/**
 * Compose target with a non-null [SemanticsConfiguration].
 */
data class ComposeMaskTarget(
    override val view: View,
    val config: SemanticsConfiguration,
) : MaskTarget


