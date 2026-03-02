package com.launchdarkly.observability.api
import android.view.View
import com.launchdarkly.observability.R

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