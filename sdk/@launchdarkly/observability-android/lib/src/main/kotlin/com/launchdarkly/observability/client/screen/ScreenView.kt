package com.launchdarkly.observability.client.screen

/**
 * A single screen appearance, mapped to the taxonomy `screen_view` event.
 *
 * Only [name] is required by the taxonomy; the other fields are optional and emitted under the
 * `event.*` namespace when present.
 *
 * @property name Human-readable screen name, e.g. `Profile`. Maps to `event.name`.
 * @property screenClass Activity/Fragment class, e.g. `ProfileActivity`. Maps to `event.screen_class`.
 * @property screenId Stable, fully-qualified identifier, e.g. `com.example.app.ProfileActivity`. Maps to `event.screen_id`.
 * @property category Screen group, e.g. `Onboarding`. Maps to `event.category`.
 * @property timestamp Capture time, in milliseconds since epoch.
 */
data class ScreenView(
    val name: String,
    val screenClass: String? = null,
    val screenId: String? = null,
    val category: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
