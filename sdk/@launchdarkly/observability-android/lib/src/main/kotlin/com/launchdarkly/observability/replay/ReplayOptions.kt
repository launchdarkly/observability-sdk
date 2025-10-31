package com.launchdarkly.observability.replay

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.DEFAULT_BACKEND_URL

/**
 * Options for the [ReplayInstrumentation]
 *
 * @property backendUrl The backend URL for sending replay data. Defaults to LaunchDarkly url.
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property capturePeriodMillis period between captures
 */
data class ReplayOptions(
    val serviceName: String = "observability-android",
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val capturePeriodMillis: Long = 1000, // defaults to ever 1 second
    // TODO O11Y-623 - Add storage options
)
