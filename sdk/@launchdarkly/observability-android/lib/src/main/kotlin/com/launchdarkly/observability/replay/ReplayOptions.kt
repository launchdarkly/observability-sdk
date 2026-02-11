package com.launchdarkly.observability.replay

/**
 * Options for Session Replay plugin.
 *
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property capturePeriodMillis period between captures
 * @property enabled controls whether session replay starts capturing immediately on initialization
 */
data class ReplayOptions(
    val enabled: Boolean = true,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val capturePeriodMillis: Long = 1000,
    // TODO O11Y-623 - Add storage options
)
