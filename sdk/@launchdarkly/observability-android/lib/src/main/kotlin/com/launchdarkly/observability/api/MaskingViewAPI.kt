package com.launchdarkly.observability.api
import android.view.View
import com.launchdarkly.observability.R

/**
 * Marks this native View — and every descendant of it — as sensitive for masking in session
 * replay.
 */
fun View.ldMask() {
    setTag(R.id.ld_mask_tag, true)
}

/**
 * Marks this native View — and every descendant of it — as explicitly *not* sensitive for
 * masking in session replay. This overrides global masking rules such as `maskText` and
 * `maskTextInputs` for the affected views. If this view or one of its ancestors is also
 * explicitly masked via [ldMask], the explicit mask wins.
 */
fun View.ldUnmask() {
    setTag(R.id.ld_mask_tag, false)
}