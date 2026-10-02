package com.launchdarkly.observability.client.screen

import io.opentelemetry.api.common.Attributes

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
 * @property attributes Optional caller-supplied attributes attached to the `screen_view` span,
 *   applied at lower precedence than the reserved `event.*` fields so they can never clobber the
 *   taxonomy. Empty for automatically captured screens.
 * @property timestamp Capture time, in milliseconds since epoch.
 */
data class ScreenView(
    val name: String,
    val screenClass: String? = null,
    val screenId: String? = null,
    val category: String? = null,
    val attributes: Attributes = Attributes.empty(),
    val timestamp: Long = System.currentTimeMillis(),
)
