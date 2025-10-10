package com.launchdarkly.observability.replay

data class ReplayOptions(
    val backendUrl: String = "https://pub.observability.ld-stg.launchdarkly.com", // TODO: update to prod
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile.STRICT,
    val captureIntervalMillis: Long = 1000, // Default to 1 second
)