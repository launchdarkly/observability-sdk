package com.launchdarkly.observability.api
import android.view.View
import com.launchdarkly.observability.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Marks this native View as sensitive for masking in session replay.
 * Sets a tag so the replay system can detect and apply masking.
 */
fun View.ldMask() {
    setTag(R.id.ld_mask_tag, true)
}

/**
 * Unmarks this native View as sensitive for masking in session replay.
 * Sets a tag so the replay system will not mask this view.
 */
fun View.ldUnmask() {
    setTag(R.id.ld_mask_tag, false)
}

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