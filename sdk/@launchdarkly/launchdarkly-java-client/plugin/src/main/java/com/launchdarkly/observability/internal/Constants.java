package com.launchdarkly.observability.internal;

/**
 * Names and endpoints shared by the plugin's classes. Not part of the public API.
 */
public final class Constants {
  private Constants() {}

  /** The plugin's name in {@code PluginMetadata}, following the Android plugin's. */
  public static final String PLUGIN_NAME = "@launchdarkly/observability-java-client";

  public static final String DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4318";
  public static final String DEFAULT_BACKEND_URL = "https://pub.observability.app.launchdarkly.com";
  public static final String DEFAULT_SERVICE_NAME = "observability-java-client";
  public static final String DISTRO_NAME = "launchdarkly-observability-java-client";

  /** The instrumentation scope of every tracer, logger and meter the plugin uses, as on Android. */
  public static final String INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability";

  public static final String TRACES_PATH = "/v1/traces";
  public static final String LOGS_PATH = "/v1/logs";
  public static final String METRICS_PATH = "/v1/metrics";

  public static final String PROJECT_ID_ATTRIBUTE = "highlight.project_id";
  public static final String SESSION_ID_ATTRIBUTE = "session.id";
  public static final String SAMPLING_RATIO_ATTRIBUTE = "launchdarkly.sampling.ratio";

  public static final String ERROR_SPAN_NAME = "highlight.error";
  public static final String TRACK_SPAN_NAME = "track";
  public static final String APP_LAUNCH_SPAN_NAME = "app_launch";
  public static final String APP_START_EVENT_NAME = "app.start";
  public static final String IDENTIFY_LOG_MESSAGE = "LD.identify";
  public static final String SCREEN_VIEW_SPAN_NAME = "screen_view";
  public static final String CLICK_SPAN_NAME = "click";
  public static final String APP_FOREGROUND_SPAN_NAME = "app_foreground";
  public static final String APP_BACKGROUND_SPAN_NAME = "app_background";

  /** A session is replaced after this long, however active it is, as on Android. */
  public static final long SESSION_MAX_LIFETIME_MILLIS = 4L * 60 * 60 * 1000;
}
