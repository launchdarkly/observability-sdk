package com.launchdarkly.observability.replay

/**
 * Options for Session Replay plugin.
 *
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property capturePeriodMillis period between captures
 */
data class ReplayOptions(
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val capturePeriodMillis: Long = 1000, // defaults to ever 1 second
    // TODO O11Y-623 - Add storage options
)
