package com.launchdarkly.observability;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.internal.Constants;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the {@link Observability} plugin and for {@link LDObserve#init}. The options and
 * their defaults follow the Android plugin's. Build an instance with {@link #builder()}:
 *
 * <pre><code>
 *     ObservabilityOptions options = ObservabilityOptions.builder()
 *         .serviceName("my-desktop-app")
 *         .serviceVersion("1.4.2")
 *         .instrumentations(ObservabilityOptions.Instrumentations.builder().launchTime(true).build())
 *         .build();
 * </code></pre>
 */
public final class ObservabilityOptions {
  private final boolean enabled;
  private final String serviceName;
  private final String serviceVersion;
  private final String otlpEndpoint;
  private final String backendUrl;
  private final String contextFriendlyName;
  private final Attributes resourceAttributes;
  private final Map<String, String> customHeaders;
  private final boolean debug;
  private final LogLevel logsApiLevel;
  private final TracesApi tracesApi;
  private final MetricsApi metricsApi;
  private final Analytics analytics;
  private final Instrumentations instrumentations;
  private final LDLogAdapter logAdapter;
  private final String loggerName;
  private final boolean shutdownHook;
  private final long sessionBackgroundTimeoutMillis;

  private ObservabilityOptions(Builder b) {
    this.enabled = b.enabled;
    this.serviceName = b.serviceName;
    this.serviceVersion = b.serviceVersion;
    this.otlpEndpoint = b.otlpEndpoint;
    this.backendUrl = b.backendUrl;
    this.contextFriendlyName = b.contextFriendlyName;
    this.resourceAttributes = b.resourceAttributes;
    this.customHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(b.customHeaders));
    this.debug = b.debug;
    this.logsApiLevel = b.logsApiLevel;
    this.tracesApi = b.tracesApi;
    this.metricsApi = b.metricsApi;
    this.analytics = b.analytics;
    this.instrumentations = b.instrumentations;
    this.logAdapter = b.logAdapter;
    this.loggerName = b.loggerName;
    this.shutdownHook = b.shutdownHook;
    this.sessionBackgroundTimeoutMillis = b.sessionBackgroundTimeoutMillis;
  }

  /**
   * Returns a builder with every option at its default.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Whether the plugin exports anything. See {@link Builder#enabled(boolean)}.
   *
   * @return true if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * The {@code service.name} resource attribute.
   *
   * @return the service name
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * The {@code service.version} configured, or null if none was, in which case the resource carries
   * the plugin's version, as on Android.
   *
   * @return the service version, or null
   */
  public String getServiceVersion() {
    return serviceVersion;
  }

  /**
   * The OTLP endpoint, without the {@code /v1/...} path.
   *
   * @return the endpoint
   */
  public String getOtlpEndpoint() {
    return otlpEndpoint;
  }

  /**
   * The LaunchDarkly observability backend, from which the sampling rules are fetched.
   *
   * @return the backend URL
   */
  public String getBackendUrl() {
    return backendUrl;
  }

  /**
   * The {@code key} attribute of identify logs, or null to use the context's fully qualified key.
   *
   * @return the friendly name, or null
   */
  public String getContextFriendlyName() {
    return contextFriendlyName;
  }

  /**
   * Resource attributes added to every signal. Never null.
   *
   * @return the attributes
   */
  public Attributes getResourceAttributes() {
    return resourceAttributes;
  }

  /**
   * Headers sent with every export and with the sampling request. Never null.
   *
   * @return the headers
   */
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  /**
   * Whether the plugin logs its own activity in detail.
   *
   * @return true for debug logging
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * The lowest severity of log record exported.
   *
   * @return the level
   */
  public LogLevel getLogsApiLevel() {
    return logsApiLevel;
  }

  /**
   * The trace options.
   *
   * @return the trace options
   */
  public TracesApi getTracesApi() {
    return tracesApi;
  }

  /**
   * The metric options.
   *
   * @return the metric options
   */
  public MetricsApi getMetricsApi() {
    return metricsApi;
  }

  /**
   * The analytics options.
   *
   * @return the analytics options
   */
  public Analytics getAnalytics() {
    return analytics;
  }

  /**
   * The automatic instrumentation options.
   *
   * @return the instrumentation options
   */
  public Instrumentations getInstrumentations() {
    return instrumentations;
  }

  /**
   * Where the plugin's own log messages go.
   *
   * @return the adapter
   */
  public LDLogAdapter getLogAdapter() {
    return logAdapter;
  }

  /**
   * The logger name for the plugin's own log messages.
   *
   * @return the name
   */
  public String getLoggerName() {
    return loggerName;
  }

  /**
   * Whether the plugin flushes and stops on JVM exit by itself. See {@link Builder#shutdownHook(boolean)}.
   *
   * @return true if a shutdown hook is registered
   */
  public boolean isShutdownHook() {
    return shutdownHook;
  }

  /**
   * How long a session survives in the background with nothing recorded. See
   * {@link Builder#sessionBackgroundTimeoutMillis(long)}.
   *
   * @return the timeout in milliseconds
   */
  public long getSessionBackgroundTimeoutMillis() {
    return sessionBackgroundTimeoutMillis;
  }

  /**
   * The lowest severity of log record to export, by OpenTelemetry severity number. A record is
   * exported when its severity is at or above the level; a record without one counts as
   * {@link #TRACE}. {@link #NONE} exports no log records.
   */
  public enum LogLevel {
    /** Severity 1 and above. */
    TRACE(Severity.TRACE.getSeverityNumber()),
    /** Severity 5 and above. */
    DEBUG(Severity.DEBUG.getSeverityNumber()),
    /** Severity 9 and above. */
    INFO(Severity.INFO.getSeverityNumber()),
    /** Severity 13 and above. */
    WARN(Severity.WARN.getSeverityNumber()),
    /** Severity 17 and above. */
    ERROR(Severity.ERROR.getSeverityNumber()),
    /** Severity 21 and above. */
    FATAL(Severity.FATAL.getSeverityNumber()),
    /** No log records. */
    NONE(Integer.MAX_VALUE);

    private final int level;

    LogLevel(int level) {
      this.level = level;
    }

    /**
     * The OpenTelemetry severity number this level starts at.
     *
     * @return the severity number
     */
    public int getLevel() {
      return level;
    }
  }

  /** Trace options. */
  public static final class TracesApi {
    private final boolean includeErrors;
    private final boolean includeSpans;

    private TracesApi(boolean includeErrors, boolean includeSpans) {
      this.includeErrors = includeErrors;
      this.includeSpans = includeSpans;
    }

    /**
     * Errors and spans both recorded, the default.
     *
     * @return the options
     */
    public static TracesApi enabled() {
      return new TracesApi(true, true);
    }

    /**
     * Neither errors nor spans recorded.
     *
     * @return the options
     */
    public static TracesApi disabled() {
      return new TracesApi(false, false);
    }

    /**
     * Returns a builder with both enabled.
     *
     * @return a new builder
     */
    public static Builder builder() {
      return new Builder();
    }

    /**
     * Whether {@link LDObserve#recordError} records errors.
     *
     * @return true if errors are recorded
     */
    public boolean isIncludeErrors() {
      return includeErrors;
    }

    /**
     * Whether {@link LDObserve#startSpan} and the {@code track} spans record anything.
     *
     * @return true if spans are recorded
     */
    public boolean isIncludeSpans() {
      return includeSpans;
    }

    /** Builder for {@link TracesApi}. */
    public static final class Builder {
      private boolean includeErrors = true;
      private boolean includeSpans = true;

      private Builder() {}

      /**
       * Whether to record errors. Defaults to true.
       *
       * @param includeErrors true to record errors
       * @return the builder
       */
      public Builder includeErrors(boolean includeErrors) {
        this.includeErrors = includeErrors;
        return this;
      }

      /**
       * Whether to record spans. Defaults to true.
       *
       * @param includeSpans true to record spans
       * @return the builder
       */
      public Builder includeSpans(boolean includeSpans) {
        this.includeSpans = includeSpans;
        return this;
      }

      /**
       * Builds the options.
       *
       * @return the options
       */
      public TracesApi build() {
        return new TracesApi(includeErrors, includeSpans);
      }
    }
  }

  /** Metric options. */
  public static final class MetricsApi {
    private final boolean enabled;

    private MetricsApi(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Metrics recorded, the default.
     *
     * @return the options
     */
    public static MetricsApi enabled() {
      return new MetricsApi(true);
    }

    /**
     * No metrics recorded.
     *
     * @return the options
     */
    public static MetricsApi disabled() {
      return new MetricsApi(false);
    }

    /**
     * Whether the {@code LDObserve.record...} metric methods record anything.
     *
     * @return true if metrics are recorded
     */
    public boolean isEnabled() {
      return enabled;
    }
  }

  /** Options for the analytics signals the plugin emits as spans. */
  public static final class Analytics {
    private final boolean taps;
    private final boolean trackEvents;
    private final boolean screenViews;
    private final boolean appLifecycle;
    private final boolean appLaunch;

    private Analytics(Builder b) {
      this.taps = b.taps;
      this.trackEvents = b.trackEvents;
      this.screenViews = b.screenViews;
      this.appLifecycle = b.appLifecycle;
      this.appLaunch = b.appLaunch;
    }

    /**
     * Returns a builder with everything enabled.
     *
     * @return a new builder
     */
    public static Builder builder() {
      return new Builder();
    }

    /**
     * Whether each mouse click detected by {@link Instrumentations#isUserTaps()} becomes a
     * {@code click} span.
     *
     * @return true if click spans are emitted
     */
    public boolean isTaps() {
      return taps;
    }

    /**
     * Whether each {@code LDClient.track} and {@link LDObserve#track} call becomes a {@code track} span.
     *
     * @return true if track spans are emitted
     */
    public boolean isTrackEvents() {
      return trackEvents;
    }

    /**
     * Whether each screen shown, detected by {@link Instrumentations#isScreens()} or recorded with
     * {@link LDObserve#trackScreenView}, becomes a {@code screen_view} span.
     *
     * @return true if screen-view spans are emitted
     */
    public boolean isScreenViews() {
      return screenViews;
    }

    /**
     * Whether the application coming to the foreground or going to the background becomes an
     * {@code app_foreground} or {@code app_background} span.
     *
     * @return true if lifecycle spans are emitted
     */
    public boolean isAppLifecycle() {
      return appLifecycle;
    }

    /**
     * Whether an {@code app_launch} span is emitted when the plugin starts.
     *
     * @return true if the launch span is emitted
     */
    public boolean isAppLaunch() {
      return appLaunch;
    }

    /** Builder for {@link Analytics}. */
    public static final class Builder {
      private boolean taps = true;
      private boolean trackEvents = true;
      private boolean screenViews = true;
      private boolean appLifecycle = true;
      private boolean appLaunch = true;

      private Builder() {}

      /**
       * Whether to emit {@code click} spans. Defaults to true.
       *
       * @param taps true to emit them
       * @return the builder
       */
      public Builder taps(boolean taps) {
        this.taps = taps;
        return this;
      }

      /**
       * Whether to emit {@code screen_view} spans. Defaults to true.
       *
       * @param screenViews true to emit them
       * @return the builder
       */
      public Builder screenViews(boolean screenViews) {
        this.screenViews = screenViews;
        return this;
      }

      /**
       * Whether to emit {@code app_foreground} and {@code app_background} spans. Defaults to true.
       * The session's background timeout applies either way.
       *
       * @param appLifecycle true to emit them
       * @return the builder
       */
      public Builder appLifecycle(boolean appLifecycle) {
        this.appLifecycle = appLifecycle;
        return this;
      }

      /**
       * Whether to emit {@code track} spans. Defaults to true.
       *
       * @param trackEvents true to emit them
       * @return the builder
       */
      public Builder trackEvents(boolean trackEvents) {
        this.trackEvents = trackEvents;
        return this;
      }

      /**
       * Whether to emit the {@code app_launch} span. Defaults to true. To tell an install from an
       * update or a relaunch, the plugin keeps the last application version it saw in the user's
       * Java preferences.
       *
       * @param appLaunch true to emit it
       * @return the builder
       */
      public Builder appLaunch(boolean appLaunch) {
        this.appLaunch = appLaunch;
        return this;
      }

      /**
       * Builds the options.
       *
       * @return the options
       */
      public Analytics build() {
        return new Analytics(this);
      }
    }
  }

  /** Options for automatic instrumentation. */
  public static final class Instrumentations {
    private final boolean crashReporting;
    private final boolean launchTime;
    private final boolean userTaps;
    private final boolean screens;
    private final boolean recordText;

    private Instrumentations(boolean crashReporting, boolean launchTime, boolean userTaps, boolean screens,
        boolean recordText) {
      this.crashReporting = crashReporting;
      this.launchTime = launchTime;
      this.userTaps = userTaps;
      this.screens = screens;
      this.recordText = recordText;
    }

    /**
     * Returns a builder with the defaults: everything on except launch time, as on Android.
     *
     * @return a new builder
     */
    public static Builder builder() {
      return new Builder();
    }

    /**
     * Every instrumentation enabled.
     *
     * @return the options
     */
    public static Instrumentations enabled() {
      return new Instrumentations(true, true, true, true, false);
    }

    /**
     * Every instrumentation disabled.
     *
     * @return the options
     */
    public static Instrumentations disabled() {
      return new Instrumentations(false, false, false, false, false);
    }

    /**
     * Whether an exception no code catches is recorded as an error before the thread dies.
     *
     * @return true if crashes are reported
     */
    public boolean isCrashReporting() {
      return crashReporting;
    }

    /**
     * Whether the {@code app_launch} span starts when the JVM did and carries an {@code app.start}
     * event with the startup duration. Without it the span marks only the moment the plugin started.
     *
     * @return true if launch time is measured
     */
    public boolean isLaunchTime() {
      return launchTime;
    }

    /**
     * Whether mouse clicks in the application's Swing and AWT windows are detected. Publishing them
     * as {@code click} spans is {@link Analytics#isTaps()}.
     *
     * @return true if clicks are detected
     */
    public boolean isUserTaps() {
      return userTaps;
    }

    /**
     * Whether each of the application's windows is a screen, shown when it becomes active, and
     * whether the application is in the foreground is told from whether one of its windows is
     * active. Publishing them as spans is {@link Analytics#isScreenViews()} and
     * {@link Analytics#isAppLifecycle()}.
     *
     * @return true if windows are tracked
     */
    public boolean isScreens() {
      return screens;
    }

    /**
     * Whether window titles and the text of clicked buttons and labels are recorded. See
     * {@link Builder#recordText(boolean)}.
     *
     * @return true if text is recorded
     */
    public boolean isRecordText() {
      return recordText;
    }

    /** Builder for {@link Instrumentations}. */
    public static final class Builder {
      private boolean crashReporting = true;
      private boolean launchTime = false;
      private boolean userTaps = true;
      private boolean screens = true;
      private boolean recordText = false;

      private Builder() {}

      /**
       * Whether to record what the user sees: a window's title as its screen name, and the text of a
       * clicked button, menu item or label. Defaults to false, because on a desktop that text often
       * holds personal data, such as a name, an account or a file path. Without it, a screen is named
       * after its window's class, and a click records the component's class, name and path but no
       * text. The text of text fields and other input components is never recorded.
       *
       * @param recordText true to record text
       * @return the builder
       */
      public Builder recordText(boolean recordText) {
        this.recordText = recordText;
        return this;
      }

      /**
       * Whether to detect mouse clicks in Swing and AWT windows. Defaults to true. A click records
       * the component's class, name and path in its window, and, with {@link #recordText(boolean)},
       * the text of a button or label.
       *
       * @param userTaps true to detect them
       * @return the builder
       */
      public Builder userTaps(boolean userTaps) {
        this.userTaps = userTaps;
        return this;
      }

      /**
       * Whether to track the application's windows. Defaults to true.
       *
       * @param screens true to track them
       * @return the builder
       */
      public Builder screens(boolean screens) {
        this.screens = screens;
        return this;
      }

      /**
       * Whether to report uncaught exceptions. Defaults to true. The plugin installs a default
       * uncaught-exception handler that records the error and then hands it to the handler that
       * was installed before, so the application's own handling still runs.
       *
       * @param crashReporting true to report them
       * @return the builder
       */
      public Builder crashReporting(boolean crashReporting) {
        this.crashReporting = crashReporting;
        return this;
      }

      /**
       * Whether to measure the time from JVM start to plugin start. Defaults to false.
       *
       * @param launchTime true to measure it
       * @return the builder
       */
      public Builder launchTime(boolean launchTime) {
        this.launchTime = launchTime;
        return this;
      }

      /**
       * Builds the options.
       *
       * @return the options
       */
      public Instrumentations build() {
        return new Instrumentations(crashReporting, launchTime, userTaps, screens, recordText);
      }
    }
  }

  /** Builder for {@link ObservabilityOptions}. Every setter defaults to the Android plugin's default. */
  public static final class Builder {
    private boolean enabled = true;
    private String serviceName = Constants.DEFAULT_SERVICE_NAME;
    private String serviceVersion;
    private String otlpEndpoint = Constants.DEFAULT_OTLP_ENDPOINT;
    private String backendUrl = Constants.DEFAULT_BACKEND_URL;
    private String contextFriendlyName;
    private Attributes resourceAttributes = Attributes.empty();
    private Map<String, String> customHeaders = new LinkedHashMap<>();
    private boolean debug;
    private LogLevel logsApiLevel = LogLevel.INFO;
    private TracesApi tracesApi = TracesApi.enabled();
    private MetricsApi metricsApi = MetricsApi.enabled();
    private Analytics analytics = Analytics.builder().build();
    private Instrumentations instrumentations = Instrumentations.builder().build();
    private LDLogAdapter logAdapter = Logs.toConsole();
    private String loggerName = "LaunchDarklyObservabilityPlugin";
    private boolean shutdownHook = true;
    private long sessionBackgroundTimeoutMillis = 15L * 60 * 1000;

    private Builder() {}

    /**
     * Sets how long a session survives while the application is in the background, none of its
     * windows active, with nothing recorded. The next signal after that starts a new session.
     * Defaults to 15 minutes, as on Android. An application without windows is never in the
     * background.
     *
     * @param millis the timeout in milliseconds
     * @return the builder
     */
    public Builder sessionBackgroundTimeoutMillis(long millis) {
      this.sessionBackgroundTimeoutMillis = millis;
      return this;
    }

    /**
     * Whether the plugin exports anything. When false the plugin registers no hooks, starts nothing,
     * installs no crash handler and contacts no service, and every {@link LDObserve} call does
     * nothing. Defaults to true.
     *
     * @param enabled false to turn the plugin off
     * @return the builder
     */
    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Sets {@code service.name}. Defaults to {@code observability-java-client}.
     *
     * @param serviceName the service name
     * @return the builder
     */
    public Builder serviceName(String serviceName) {
      this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
      return this;
    }

    /**
     * Sets {@code service.version}. Defaults to the plugin's version. Also the application version
     * the {@code app_launch} span compares against, when {@code LDConfig} has no application info.
     *
     * @param serviceVersion the service version
     * @return the builder
     */
    public Builder serviceVersion(String serviceVersion) {
      this.serviceVersion = serviceVersion;
      return this;
    }

    /**
     * Sets the OTLP endpoint. Defaults to LaunchDarkly's.
     *
     * @param otlpEndpoint the endpoint, without the {@code /v1/...} path
     * @return the builder
     */
    public Builder otlpEndpoint(String otlpEndpoint) {
      this.otlpEndpoint = Objects.requireNonNull(otlpEndpoint, "otlpEndpoint");
      return this;
    }

    /**
     * Sets the backend the sampling rules come from. Defaults to LaunchDarkly's.
     *
     * @param backendUrl the backend URL
     * @return the builder
     */
    public Builder backendUrl(String backendUrl) {
      this.backendUrl = Objects.requireNonNull(backendUrl, "backendUrl");
      return this;
    }

    /**
     * Sets the {@code key} attribute of identify logs. Defaults to the context's fully qualified key.
     *
     * @param contextFriendlyName the name, or null for the default
     * @return the builder
     */
    public Builder contextFriendlyName(String contextFriendlyName) {
      this.contextFriendlyName = contextFriendlyName;
      return this;
    }

    /**
     * Adds resource attributes to every signal. They can override the OpenTelemetry defaults and the
     * plugin's own, but not {@code service.name}, {@code service.version} or the
     * {@code launchdarkly.*} attributes.
     *
     * @param resourceAttributes the attributes
     * @return the builder
     */
    public Builder resourceAttributes(Attributes resourceAttributes) {
      this.resourceAttributes = Objects.requireNonNull(resourceAttributes, "resourceAttributes");
      return this;
    }

    /**
     * Sets headers sent with every export and with the sampling request, replacing any set before.
     *
     * @param customHeaders the headers
     * @return the builder
     */
    public Builder customHeaders(Map<String, String> customHeaders) {
      this.customHeaders = new LinkedHashMap<>(Objects.requireNonNull(customHeaders, "customHeaders"));
      return this;
    }

    /**
     * Turns on detailed logging of the plugin's own activity. Defaults to false.
     *
     * @param debug true for debug logging
     * @return the builder
     */
    public Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    /**
     * Sets the lowest severity of log record exported. Defaults to {@link LogLevel#INFO}.
     *
     * @param logsApiLevel the level; {@link LogLevel#NONE} exports no log records
     * @return the builder
     */
    public Builder logsApiLevel(LogLevel logsApiLevel) {
      this.logsApiLevel = Objects.requireNonNull(logsApiLevel, "logsApiLevel");
      return this;
    }

    /**
     * Sets the trace options. Defaults to {@link TracesApi#enabled()}.
     *
     * @param tracesApi the options
     * @return the builder
     */
    public Builder tracesApi(TracesApi tracesApi) {
      this.tracesApi = Objects.requireNonNull(tracesApi, "tracesApi");
      return this;
    }

    /**
     * Sets the metric options. Defaults to {@link MetricsApi#enabled()}.
     *
     * @param metricsApi the options
     * @return the builder
     */
    public Builder metricsApi(MetricsApi metricsApi) {
      this.metricsApi = Objects.requireNonNull(metricsApi, "metricsApi");
      return this;
    }

    /**
     * Sets the analytics options. Defaults to everything enabled.
     *
     * @param analytics the options
     * @return the builder
     */
    public Builder analytics(Analytics analytics) {
      this.analytics = Objects.requireNonNull(analytics, "analytics");
      return this;
    }

    /**
     * Sets the instrumentation options. Defaults to crash reporting on and launch time off.
     *
     * @param instrumentations the options
     * @return the builder
     */
    public Builder instrumentations(Instrumentations instrumentations) {
      this.instrumentations = Objects.requireNonNull(instrumentations, "instrumentations");
      return this;
    }

    /**
     * Sets where the plugin's own log messages go. Defaults to the console, like the SDK. Pass
     * {@code LDSLF4J.adapter()} from {@code launchdarkly-logging-slf4j} to use SLF4J.
     *
     * @param logAdapter the adapter
     * @return the builder
     */
    public Builder logAdapter(LDLogAdapter logAdapter) {
      this.logAdapter = Objects.requireNonNull(logAdapter, "logAdapter");
      return this;
    }

    /**
     * Sets the logger name for the plugin's own log messages. Defaults to
     * {@code LaunchDarklyObservabilityPlugin}.
     *
     * @param loggerName the name
     * @return the builder
     */
    public Builder loggerName(String loggerName) {
      this.loggerName = Objects.requireNonNull(loggerName, "loggerName");
      return this;
    }

    /**
     * Whether the plugin registers a JVM shutdown hook that exports what is still queued and stops.
     * Defaults to true. Set it to false when the application stops the plugin itself, with
     * {@link LDObserve#shutdown()}, for example from a window-closing handler.
     *
     * @param shutdownHook false to manage shutdown yourself
     * @return the builder
     */
    public Builder shutdownHook(boolean shutdownHook) {
      this.shutdownHook = shutdownHook;
      return this;
    }

    /**
     * Builds the options.
     *
     * @return the options
     */
    public ObservabilityOptions build() {
      return new ObservabilityOptions(this);
    }
  }
}
