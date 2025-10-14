package com.launchdarkly.observability.replay

private const val DEFAULT_BACKEND_URL = "https://pub.observability.app.launchdarkly.com"

/**
 * Options for the [ReplayInstrumentation]
 *
 * @property backendUrl The backend URL for sending replay data. Defaults to LaunchDarkly url.
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property capturePeriodMillis period between captures
 */
data class ReplayOptions(
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile.STRICT,
    val capturePeriodMillis: Long = 1000, // defaults to ever 1 second
)