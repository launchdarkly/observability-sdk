package com.launchdarkly.observability.replay

/**
 * Options for Session Replay plugin.
 *
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property capturePeriodMillis period between captures
 * @property scale optional replay scale override. When null, no additional scaling is applied. Usually from 1-4. 1 = 160DPI
 * @property enabled controls whether session replay starts capturing immediately on initialization
 */
data class ReplayOptions(
    val enabled: Boolean = true,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val capturePeriodMillis: Long = 1000, // defaults to ever 1 second
    /** Optional replay scale. Null disables scaling override. */
    val scale: Float? = 1.0f
    // TODO O11Y-623 - Add storage options
)
