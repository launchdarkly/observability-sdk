package com.launchdarkly.observability.api

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Compose semantics key carrying an explicit, developer-supplied analytics identifier for an
 * element. When a `click` event is auto-captured for a Compose element, the SDK prefers this value
 * for `event.id`, falling back to the element's test tag and then native resolution.
 */
val LdIdSemanticsKey = SemanticsPropertyKey<String>("ld_id")

/**
 * Convenience property delegate for use inside `semantics {}` blocks.
 */
var SemanticsPropertyReceiver.ldId by LdIdSemanticsKey

/**
 * Tags this Compose element with a stable analytics identifier used as `event.id` for auto-captured
 * `click` events on this element. Prefer a human-readable, stable id (e.g. `"checkout.pay_button"`).
 */
fun Modifier.ldId(id: String): Modifier = this.semantics {
    ldId = id
}
