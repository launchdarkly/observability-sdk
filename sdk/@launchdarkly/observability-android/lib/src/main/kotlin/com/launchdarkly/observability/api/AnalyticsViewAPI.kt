package com.launchdarkly.observability.api

import android.view.View
import com.launchdarkly.observability.R

/**
 * Tags this native View with a stable analytics identifier used as `event.id` for auto-captured
 * `click` events on this view (or its descendants, when the tap resolves to a child). Prefer a
 * human-readable, stable id (e.g. `"checkout.pay_button"`).
 *
 * Takes precedence over the view's resource entry name and React Native `testID` when resolving
 * `event.id`.
 */
fun View.ldId(id: String) {
    setTag(R.id.ld_id_tag, id)
}
