package com.launchdarkly.observability.api

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Compose semantics key used to mark nodes as sensitive for masking.
 */
val LdMaskSemanticsKey = SemanticsPropertyKey<Boolean>("ld_mask")

/**
 * Convenience property delegate for use inside semantics {} blocks.
 */
var SemanticsPropertyReceiver.ldMask by LdMaskSemanticsKey

/**
 * Marks this Compose element as sensitive; session replay should mask it.
 */
fun Modifier.ldMask(): Modifier = this.semantics {
    ldMask = true
}

/**
 * Marks this Compose element as not sensitive; session replay should not mask it.
 */
fun Modifier.ldUnmask(): Modifier = this.semantics {
    ldMask = false
}
