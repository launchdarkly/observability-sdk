package com.launchdarkly.observability.api

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Compose semantics key carrying a stable analytics identifier for a Compose element. When a
 * `click` event is auto-captured for a Compose element, the plugin prefers this value for its id,
 * falling back to the element's test tag. The same key as on Android.
 */
public val LdIdSemanticsKey: SemanticsPropertyKey<String> = SemanticsPropertyKey<String>("ld_id")

/**
 * Convenience property delegate for use inside `semantics {}` blocks.
 */
public var SemanticsPropertyReceiver.ldId: String by LdIdSemanticsKey

/**
 * Tags this Compose element with a stable analytics identifier used as the id of auto-captured
 * `click` events on this element. Prefer a human-readable, stable id (e.g. `"checkout.pay_button"`).
 */
public fun Modifier.ldId(id: String): Modifier = this.semantics {
    ldId = id
}
