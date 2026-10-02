package com.launchdarkly.observability.client.screen

/**
 * Implement on an `Activity` to customize how it is reported as a `screen_view`.
 *
 * When automatic screen tracking captures an activity, these values take precedence over the
 * derived defaults (cleaned class name).
 */
interface LDScreenNameProvider {
    /** Human-readable screen name (maps to `event.name`). Return `null` to fall back to defaults. */
    val ldScreenName: String?

    /** Optional screen group (maps to `event.category`). */
    val ldScreenCategory: String?
        get() = null
}
