package com.launchdarkly.observability.api

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Compose semantics key used to mark nodes as sensitive for masking. The same key as on Android,
 * so shared Compose code masks the same way on both.
 */
public val LdMaskSemanticsKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey<Boolean>("ld_mask")

/**
 * Convenience property delegate for use inside semantics {} blocks.
 */
public var SemanticsPropertyReceiver.ldMask: Boolean by LdMaskSemanticsKey

/**
 * Marks this Compose element — and every descendant of it — as sensitive for masking in session
 * replay.
 */
public fun Modifier.ldMask(): Modifier = this.semantics {
    ldMask = true
}

/**
 * Marks this Compose element — and every descendant of it — as explicitly *not* sensitive for
 * masking in session replay. This overrides global masking rules such as `maskText` and
 * `maskTextInputs` for the affected elements. If this element or one of its ancestors is also
 * explicitly masked via [ldMask], the explicit mask wins. A password field is masked regardless.
 */
public fun Modifier.ldUnmask(): Modifier = this.semantics {
    ldMask = false
}
