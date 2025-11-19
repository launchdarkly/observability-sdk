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
data class ReplayOptions @JvmOverloads constructor(
    val serviceName: String = "observability-android",
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val capturePeriodMillis: Long = 1000, // defaults to ever 1 second
    // TODO O11Y-623 - Add storage options
) {
    companion object {
        @JvmStatic
        fun builder(): ReplayOptionsBuilder = ReplayOptionsBuilder()
    }
}

/**
 * Java-friendly builder for [ReplayOptions].
 * @example
 * ```java
    ReplayOptions replay = ReplayOptions.builder()
    .backendUrl("https://example.com")
    .capturePeriodMillis(1500L)
    .build();
 * ```
 */
class ReplayOptionsBuilder {
    private var serviceName: String = "observability-android"
    private var serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION
    private var backendUrl: String = DEFAULT_BACKEND_URL
    private var debug: Boolean = false
    private var privacyProfile: PrivacyProfile = PrivacyProfile()
    private var capturePeriodMillis: Long = 1000

    fun serviceName(value: String) = apply { this.serviceName = value }
    fun serviceVersion(value: String) = apply { this.serviceVersion = value }
    fun backendUrl(value: String) = apply { this.backendUrl = value }
    fun debug(value: Boolean) = apply { this.debug = value }
    fun privacyProfile(value: PrivacyProfile) = apply { this.privacyProfile = value }
    fun capturePeriodMillis(value: Long) = apply { this.capturePeriodMillis = value }

    fun build(): ReplayOptions =
        ReplayOptions(
            serviceName = serviceName,
            serviceVersion = serviceVersion,
            backendUrl = backendUrl,
            debug = debug,
            privacyProfile = privacyProfile,
            capturePeriodMillis = capturePeriodMillis
        )
}

// No top-level factory object needed; use ReplayOptions.builder() from Java
