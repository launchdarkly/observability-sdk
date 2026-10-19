package com.launchdarkly.observability.internal;

/**
 * Internal constants for the LaunchDarkly Observability plugin.
 */
public final class Constants {
    private Constants() {}

    public static final String DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4318";
    public static final String DEFAULT_BACKEND_URL = "https://pub.observability.app.launchdarkly.com";
    public static final String INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability";

    /**
     * The plugin version. Kept in step with {@code version} in
     * {@code gradle.properties}, which release-please updates on release. A
     * plain constant rather than a manifest or resource lookup so it resolves
     * in unit tests, shaded jars and classloaders that hide non-class files.
     */
    // x-release-please-start-version
    public static final String INSTRUMENTATION_VERSION = "0.1.0";
    // x-release-please-end

    public static final String PROJECT_ID_ATTRIBUTE = "highlight.project_id";
    public static final String ERROR_SPAN_NAME = "highlight.error";
    public static final String ATTR_SAMPLING_RATIO = "launchdarkly.sampling.ratio";

    public static final String TRACES_PATH = "/v1/traces";
    public static final String LOGS_PATH = "/v1/logs";
    public static final String METRICS_PATH = "/v1/metrics";
}
