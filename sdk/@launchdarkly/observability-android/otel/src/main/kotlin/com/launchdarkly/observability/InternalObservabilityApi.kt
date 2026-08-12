package com.launchdarkly.observability

/**
 * Marks plumbing that is public only so the full observability artifact can reach it across the
 * module boundary. It is not part of the supported surface of
 * `com.launchdarkly:launchdarkly-otel-android` and may change or disappear in any release.
 *
 * These declarations would be `internal` if the two artifacts were one module. They cannot be:
 * Kotlin's `internal` is per-compilation-unit, and the friend-module escape hatch relies on name
 * mangling that would break the moment an application resolves the two artifacts at different
 * versions.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Internal LaunchDarkly observability API. It is not supported for application use " +
        "and may change in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS,
)
annotation class InternalObservabilityApi
