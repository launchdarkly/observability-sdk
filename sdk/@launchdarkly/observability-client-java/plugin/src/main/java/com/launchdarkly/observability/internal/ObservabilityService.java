package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.Metric;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.SamplingLogProcessor;
import com.launchdarkly.observability.internal.sampling.SamplingTraceExporter;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.subsystems.ApplicationInfo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Owns the plugin's OpenTelemetry providers and records every signal: what {@code LDObserve} is
 * asked for, what the SDK hook sees, launches and crashes. The shapes of the signals follow the
 * Android plugin's {@code ObservabilityService} and {@code ObservabilityHookExporter}.
 * <p>
 * The providers are the plugin's own and are not registered as {@code GlobalOpenTelemetry}, so an
 * application, or a library it uses, that sets up OpenTelemetry itself is unaffected.
 * {@code LDObserve.getOpenTelemetry()} hands them out to instrumentation that should export to
 * LaunchDarkly.
 */
public final class ObservabilityService {
  static final AttributeKey<String> FEATURE_FLAG_KEY = AttributeKey.stringKey("feature_flag.key");
  static final AttributeKey<String> FEATURE_FLAG_PROVIDER_NAME = AttributeKey.stringKey("feature_flag.provider.name");
  static final AttributeKey<String> FEATURE_FLAG_CONTEXT_ID = AttributeKey.stringKey("feature_flag.context.id");
  static final AttributeKey<String> FEATURE_FLAG_RESULT_VALUE = AttributeKey.stringKey("feature_flag.result.value");
  static final AttributeKey<Long> FEATURE_FLAG_VARIATION_INDEX =
      AttributeKey.longKey("feature_flag.result.variationIndex");
  static final AttributeKey<Boolean> FEATURE_FLAG_IN_EXPERIMENT =
      AttributeKey.booleanKey("feature_flag.result.reason.inExperiment");
  static final String PROVIDER_NAME = "LaunchDarkly";
  static final String EVALUATION_SPAN_NAME = "evaluation";
  static final String FEATURE_FLAG_EVENT_NAME = "feature_flag";

  static final long FLUSH_TIMEOUT_SECONDS = 5;
  /**
   * The longest string attribute kept, in characters. Longer ones, such as a very large JSON flag
   * value or track payload, are cut to this length, so one value cannot swell every export.
   */
  static final int MAX_ATTRIBUTE_VALUE_LENGTH = 16 * 1024;
  static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

  /** Where the service sends what it records. */
  static final class Exporters {
    final SpanExporter spans;
    final LogRecordExporter logs;
    final MetricExporter metrics;

    Exporters(SpanExporter spans, LogRecordExporter logs, MetricExporter metrics) {
      this.spans = spans;
      this.logs = logs;
      this.metrics = metrics;
    }
  }

  private final String credential;
  private final ObservabilityOptions options;
  private final LDLogger logger;
  private final SessionManager sessions;
  private final CustomSampler sampler;
  private final Resource resource;
  private final SdkTracerProvider tracerProvider;
  private final SdkLoggerProvider loggerProvider;
  private final SdkMeterProvider meterProvider;
  private final OpenTelemetrySdk openTelemetry;
  private final Tracer tracer;
  private final Logger otelLogger;
  private final Meter meter;
  private final CrashReporter crashReporter;
  private final List<ToolkitInstrumentation> toolkits = new CopyOnWriteArrayList<>();
  private final List<ObservabilityListener> listeners = new CopyOnWriteArrayList<>();
  private final ScreenStack screens = new ScreenStack();
  // The session the screen history belongs to; a new session starts its history afresh.
  private String screenSession;

  private final Map<String, DoubleGauge> gauges = new ConcurrentHashMap<>();
  private final Map<String, LongCounter> counters = new ConcurrentHashMap<>();
  private final Map<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();
  private final Map<String, LongUpDownCounter> upDownCounters = new ConcurrentHashMap<>();

  // The context keys of the last completed identify, for the track spans recorded through LDObserve,
  // which have no context of their own.
  private volatile Attributes cachedContextKeys = Attributes.empty();
  // This launch, when the options record it; set once, while the service starts.
  private AppLaunch appLaunch;

  ObservabilityService(String credential, ObservabilityOptions options, EnvironmentMetadata metadata,
      LDLogger logger, Exporters exporters, SessionManager sessions, CustomSampler sampler,
      AppLaunch.VersionStore versionStore, Duration metricInterval) {
    this.credential = credential;
    this.options = options;
    this.logger = logger;
    this.sessions = sessions;
    this.sampler = sampler;
    this.resource = ObservabilityResource.build(credential, options, metadata);

    // Always record, and decide at export with the LaunchDarkly sampling rules, as the other
    // LaunchDarkly plugins do. Session and global attributes are added first, so sampling sees them.
    this.tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .setSpanLimits(SpanLimits.builder().setMaxAttributeValueLength(MAX_ATTRIBUTE_VALUE_LENGTH).build())
        .setSampler(Sampler.alwaysOn())
        .addSpanProcessor(new SessionAttributesSpanProcessor(sessions))
        .addSpanProcessor(BatchSpanProcessor.builder(new SamplingTraceExporter(exporters.spans, sampler, logger))
            .setScheduleDelay(Duration.ofSeconds(1))
            .build())
        .build();
    this.loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .setLogLimits(() -> LogLimits.builder().setMaxAttributeValueLength(MAX_ATTRIBUTE_VALUE_LENGTH).build())
        .addLogRecordProcessor(new SessionAttributesLogProcessor(sessions))
        .addLogRecordProcessor(new LogLevelFilterProcessor(
            new SamplingLogProcessor(BatchLogRecordProcessor.builder(exporters.logs)
                .setScheduleDelay(Duration.ofSeconds(1))
                .build(), sampler, logger),
            options.getLogsApiLevel().getLevel()))
        .build();
    this.meterProvider = SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(PeriodicMetricReader.builder(exporters.metrics).setInterval(metricInterval).build())
        .build();
    this.openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setMeterProvider(meterProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
    this.tracer = openTelemetry.getTracer(Constants.INSTRUMENTATION_SCOPE_NAME, Version.VERSION);
    this.otelLogger = openTelemetry.getLogsBridge().get(Constants.INSTRUMENTATION_SCOPE_NAME);
    this.meter = openTelemetry.getMeter(Constants.INSTRUMENTATION_SCOPE_NAME);

    // From here on, a failure must not leave the providers' threads running with nothing able to stop
    // them. The crash handler goes in last, since it is what outlives a failed start.
    try {
      if (options.getAnalytics().isAppLaunch()) {
        recordAppLaunch(metadata, versionStore);
      }
      startToolkits(options.getInstrumentations());
      this.crashReporter = options.getInstrumentations().isCrashReporting()
          ? CrashReporter.install(this::recordCrash)
          : null;
    } catch (RuntimeException | LinkageError e) {
      toolkits.forEach(ToolkitInstrumentation::stop);
      tracerProvider.shutdown();
      loggerProvider.shutdown();
      meterProvider.shutdown();
      throw e;
    }
  }

  /**
   * Starts watching the windows and clicks of each UI toolkit the runtime has, if asked to. A runtime
   * without the desktop module, such as a jlink-built image for a console application, has no AWT
   * classes, and most have no JavaFX; observability then runs without that toolkit's signals instead
   * of failing to start. Neither toolkit is started: each attaches once the application uses it.
   */
  private void startToolkits(ObservabilityOptions.Instrumentations instrumentations) {
    if (!instrumentations.isScreens() && !instrumentations.isUserTaps()) {
      return;
    }
    ToolkitSignals signals = new ToolkitSignals(desktopSignals());
    try {
      toolkits.add(DesktopInstrumentation.create(signals, instrumentations.isScreens(),
          instrumentations.isUserTaps(), instrumentations.isRecordText(), logger));
    } catch (LinkageError e) {
      logger.info("Not watching Swing and AWT windows, since this runtime has no AWT: {}", e.toString());
    }
    FxSupport.addTo(toolkits, signals, instrumentations.isScreens(), instrumentations.isUserTaps(),
        instrumentations.isRecordText(), logger);
    for (ToolkitInstrumentation toolkit : toolkits) {
      signals.add(toolkit);
      toolkit.start();
    }
  }

  /** This launch, or null when the options do not record launches. */
  public AppLaunch getAppLaunch() {
    return appLaunch;
  }

  /** The credential the service exports under. */
  public String getCredential() {
    return credential;
  }

  /** The current session id. */
  public String getSessionId() {
    return sessions.getSessionId();
  }

  /** The plugin's OpenTelemetry, for other instrumentation to export through. */
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  /** The options the service started with. */
  public ObservabilityOptions getOptions() {
    return options;
  }

  /** The logger the service reports through. */
  public LDLogger getLogger() {
    return logger;
  }

  /** The OpenTelemetry resource every signal carries. */
  public Resource getResource() {
    return resource;
  }

  /**
   * Adds a listener, which hears every signal recorded from now on, whether or not the options turn
   * the signal's span on.
   */
  public void addListener(ObservabilityListener listener) {
    listeners.add(listener);
  }

  /** Removes a listener added with {@link #addListener}. */
  public void removeListener(ObservabilityListener listener) {
    listeners.remove(listener);
  }

  /** Tells every listener, ignoring any that throws: a listener must not break what it listens to. */
  private void notifyListeners(java.util.function.Consumer<ObservabilityListener> call) {
    for (ObservabilityListener listener : listeners) {
      try {
        call.accept(listener);
      } catch (RuntimeException e) {
        logger.debug("Ignoring an error in an observability listener: {}", e.toString());
      }
    }
  }

  CustomSampler getSampler() {
    return sampler;
  }

  /** Reports global attribute keys that were ignored because session management owns them. */
  public void logReservedAttributes(Set<String> keys) {
    logger.warn("Ignoring reserved global attribute keys: {}", keys);
  }

  // -- LDObserve --

  public void recordMetric(Metric metric) {
    if (options.getMetricsApi().isEnabled()) {
      gauges.computeIfAbsent(metric.getName(), n -> meter.gaugeBuilder(n).build())
          .set(metric.getValue(), metricAttributes(metric.getAttributes()));
    }
  }

  public void recordCount(Metric metric) {
    if (options.getMetricsApi().isEnabled()) {
      counter(metric.getName()).add((long) metric.getValue(), metricAttributes(metric.getAttributes()));
    }
  }

  public void recordIncr(Metric metric) {
    if (options.getMetricsApi().isEnabled()) {
      counter(metric.getName()).add(1, metricAttributes(metric.getAttributes()));
    }
  }

  public void recordHistogram(Metric metric) {
    if (options.getMetricsApi().isEnabled()) {
      histograms.computeIfAbsent(metric.getName(), n -> meter.histogramBuilder(n).build())
          .record(metric.getValue(), metricAttributes(metric.getAttributes()));
    }
  }

  public void recordUpDownCounter(Metric metric) {
    if (options.getMetricsApi().isEnabled()) {
      upDownCounters.computeIfAbsent(metric.getName(), n -> meter.upDownCounterBuilder(n).build())
          .add((long) metric.getValue(), metricAttributes(metric.getAttributes()));
    }
  }

  private LongCounter counter(String name) {
    return counters.computeIfAbsent(name, n -> meter.counterBuilder(n).build());
  }

  /** Global attributes, overridden by the data point's own, plus the session id. */
  private Attributes metricAttributes(Attributes attributes) {
    return GlobalAttributes.merge(attributes).toBuilder()
        .put(Constants.SESSION_ID_ATTRIBUTE, sessions.getSessionId())
        .build();
  }

  /**
   * Emits a log record. The severity text is the severity's name, which sampling rules and the
   * LaunchDarkly UI read. With a span context the record is correlated with that span; otherwise
   * with the current span, if any.
   */
  public void recordLog(String message, Severity severity, Attributes attributes, SpanContext spanContext) {
    LogRecordBuilder builder = otelLogger.logRecordBuilder()
        .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .setSeverity(severity)
        .setSeverityText(severity.name())
        .setBody(message)
        .setAllAttributes(attributes);
    if (spanContext != null && spanContext.isValid()) {
      builder.setContext(Context.current().with(Span.wrap(spanContext)));
    }
    builder.emit();
  }

  /**
   * Records an error as a {@code highlight.error} span, a child of the current span if there is one,
   * carrying the exception as an event. The attributes go on both the span and the event, so
   * sampling rules and the error view both see them.
   */
  public void recordError(Throwable error, Attributes attributes) {
    if (options.getTracesApi().isIncludeErrors()) {
      recordErrorSpan(error, attributes);
    }
  }

  private void recordErrorSpan(Throwable error, Attributes attributes) {
    Span span = tracer.spanBuilder(Constants.ERROR_SPAN_NAME)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(attributes)
        .startSpan();
    span.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
    span.recordException(error, attributes);
    span.end();
  }

  /**
   * An exception no code caught. Recorded whatever {@code tracesApi.includeErrors} says, since crash
   * reporting has its own option, as on Android. The thread may be about to die, and with it the
   * JVM, so the record is flushed straight away; the shutdown hook waits for it.
   */
  void recordCrash(Thread thread, Throwable error) {
    recordErrorSpan(error, Attributes.builder()
        .put("thread.name", thread.getName())
        .put("thread.id", thread.getId())
        .build());
    tracerProvider.forceFlush();
  }

  /** Starts a span the caller ends, or an invalid one that records nothing when spans are off. */
  public Span startSpan(String name, Attributes attributes) {
    if (!options.getTracesApi().isIncludeSpans()) {
      return Span.getInvalid();
    }
    return tracer.spanBuilder(name).setSpanKind(SpanKind.CLIENT).setAllAttributes(attributes).startSpan();
  }

  /**
   * Records a {@code track} span. The event's own data comes first, then the context keys, then
   * {@code key} and {@code value}, so the event's identity cannot be overwritten.
   *
   * @param contextKeys the context keys of the tracked event, or null to use the last identify's
   */
  public void track(String key, Double metricValue, Attributes data, Attributes contextKeys) {
    notifyListeners(l -> l.track(key, metricValue, data));
    if (!options.getAnalytics().isTrackEvents() || !options.getTracesApi().isIncludeSpans()) {
      return;
    }
    AttributesBuilder attributes = Attributes.builder()
        .putAll(data)
        .putAll(contextKeys != null ? contextKeys : cachedContextKeys)
        .put("key", key);
    if (metricValue != null) {
      attributes.put("value", metricValue);
    }
    tracer.spanBuilder(Constants.TRACK_SPAN_NAME)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(attributes.build())
        .startSpan()
        .end();
  }

  // -- the SDK hook --

  /** Starts the {@code evaluation} span for a flag evaluation. */
  Span beforeEvaluation(String flagKey, String contextId) {
    return tracer.spanBuilder(EVALUATION_SPAN_NAME)
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(FEATURE_FLAG_KEY, flagKey)
        .setAttribute(FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
        .setAttribute(FEATURE_FLAG_CONTEXT_ID, contextId)
        .startSpan();
  }

  /** Adds the {@code feature_flag} event with the result to the evaluation span, and ends it. */
  void afterEvaluation(Span span, String flagKey, String contextId, String valueJson, Integer variationIndex,
      Boolean inExperiment) {
    AttributesBuilder attributes = Attributes.builder()
        .put(FEATURE_FLAG_KEY, flagKey)
        .put(FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
        .put(FEATURE_FLAG_CONTEXT_ID, contextId)
        .put(FEATURE_FLAG_RESULT_VALUE, valueJson);
    if (inExperiment != null) {
      attributes.put(FEATURE_FLAG_IN_EXPERIMENT, inExperiment);
    }
    if (variationIndex != null) {
      attributes.put(FEATURE_FLAG_VARIATION_INDEX, variationIndex.longValue());
    }
    span.addEvent(FEATURE_FLAG_EVENT_NAME, attributes.build());
    span.end();
  }

  /**
   * After a completed identify: remembers the context keys for later track spans, and records an
   * {@code LD.identify} log.
   *
   * @param contextKeys each context kind and its key
   * @param canonicalKey the context's fully qualified key
   */
  void afterIdentify(Attributes contextKeys, String canonicalKey) {
    cachedContextKeys = contextKeys;
    Map<String, String> keys = new LinkedHashMap<>();
    contextKeys.forEach((key, value) -> keys.put(key.getKey(), String.valueOf(value)));
    Map<String, String> unmodifiable = Collections.unmodifiableMap(keys);
    notifyListeners(l -> l.identified(unmodifiable, canonicalKey));
    String friendlyName = options.getContextFriendlyName();
    recordLog(Constants.IDENTIFY_LOG_MESSAGE, Severity.INFO, contextKeys.toBuilder()
        .put("key", friendlyName != null ? friendlyName : canonicalKey)
        .put("canonicalKey", canonicalKey)
        .put("identify.result.status", "completed")
        .build(), null);
  }

  // -- windows and clicks --

  static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");
  static final AttributeKey<String> EVENT_SCREEN_CLASS = AttributeKey.stringKey("event.screen_class");
  static final AttributeKey<String> EVENT_SCREEN_ID = AttributeKey.stringKey("event.screen_id");
  static final AttributeKey<String> EVENT_SCREEN_NAME = AttributeKey.stringKey("event.screen_name");
  static final AttributeKey<String> EVENT_PREVIOUS_SCREEN = AttributeKey.stringKey("event.previous_screen");
  static final AttributeKey<String> EVENT_CATEGORY = AttributeKey.stringKey("event.category");
  static final AttributeKey<String> EVENT_LIFECYCLE_STATE = AttributeKey.stringKey("event.lifecycle_state");
  static final AttributeKey<String> EVENT_TYPE = AttributeKey.stringKey("event.type");
  static final AttributeKey<String> EVENT_TAG = AttributeKey.stringKey("event.tag");
  static final AttributeKey<String> EVENT_CLASSNAME = AttributeKey.stringKey("event.classname");
  static final AttributeKey<String> EVENT_ID = AttributeKey.stringKey("event.id");
  static final AttributeKey<String> EVENT_TEXT = AttributeKey.stringKey("event.text");
  static final AttributeKey<String> EVENT_XPATH = AttributeKey.stringKey("event.xpath");
  static final AttributeKey<Long> EVENT_X = AttributeKey.longKey("event.x");
  static final AttributeKey<Long> EVENT_Y = AttributeKey.longKey("event.y");

  /** What {@link DesktopInstrumentation} reports, routed to the span emitters below. */
  ToolkitInstrumentation.Signals desktopSignals() {
    return new ToolkitInstrumentation.Signals() {
      @Override
      public void foreground() {
        sessions.onForegrounded();
        lifecycleSpan(Constants.APP_FOREGROUND_SPAN_NAME, "foreground");
        notifyListeners(ObservabilityListener::foreground);
      }

      @Override
      public void background() {
        sessions.onBackgrounded();
        lifecycleSpan(Constants.APP_BACKGROUND_SPAN_NAME, "background");
        notifyListeners(ObservabilityListener::background);
      }

      @Override
      public void startedInBackground() {
        sessions.onBackgrounded();
      }

      @Override
      public void screenShown(String name, String screenClass, String screenId) {
        trackScreenView(name, screenClass, screenId, null, Attributes.empty());
      }

      @Override
      public void click(Click click) {
        recordClick(click);
        notifyListeners(l -> l.click(click));
      }
    };
  }

  private void lifecycleSpan(String name, String state) {
    if (!options.getAnalytics().isAppLifecycle() || !options.getTracesApi().isIncludeSpans()) {
      return;
    }
    tracer.spanBuilder(name)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(cachedContextKeys.toBuilder().put(EVENT_LIFECYCLE_STATE, state).build())
        .startSpan()
        .end();
  }

  /**
   * Records a screen view as a {@code screen_view} span, unless the screen is already the current
   * one. The caller's attributes come first, then the context keys, then the {@code event.*} fields,
   * so the screen's identity cannot be overwritten, as on Android.
   */
  public void trackScreenView(String name, String screenClass, String screenId, String category,
      Attributes attributes) {
    ScreenStack.Change change;
    synchronized (screens) {
      // A new session starts with no screens behind it, as on Android.
      String session = sessions.getSessionId();
      if (!session.equals(screenSession)) {
        screens.reset();
        screenSession = session;
      }
      change = screens.record(name, screenId);
    }
    if (change.changed) {
      notifyListeners(l -> l.screenShown(name, screenClass, screenId));
    }
    if (!change.changed || !options.getAnalytics().isScreenViews() || !options.getTracesApi().isIncludeSpans()) {
      return;
    }
    AttributesBuilder builder = Attributes.builder()
        .putAll(attributes)
        .putAll(cachedContextKeys)
        .put(EVENT_NAME, name);
    putIfPresent(builder, EVENT_SCREEN_CLASS, screenClass);
    putIfPresent(builder, EVENT_SCREEN_ID, screenId);
    putIfPresent(builder, EVENT_PREVIOUS_SCREEN, change.previous);
    putIfPresent(builder, EVENT_CATEGORY, category);
    tracer.spanBuilder(Constants.SCREEN_VIEW_SPAN_NAME)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(builder.build())
        .startSpan()
        .end();
  }

  /**
   * Records a {@code click} span from press to click, with the screen it landed on. The context keys
   * come first and the {@code event.*} fields last, as on Android.
   */
  void recordClick(Click click) {
    if (!options.getAnalytics().isTaps() || !options.getTracesApi().isIncludeSpans()) {
      return;
    }
    AttributesBuilder builder = cachedContextKeys.toBuilder().put(EVENT_TYPE, Constants.CLICK_SPAN_NAME);
    putIfPresent(builder, EVENT_TAG, click.tag);
    putIfPresent(builder, EVENT_CLASSNAME, click.classname);
    putIfPresent(builder, EVENT_ID, click.id);
    putIfPresent(builder, EVENT_TEXT, click.text);
    putIfPresent(builder, EVENT_XPATH, click.xpath);
    putIfPresent(builder, EVENT_SCREEN_ID, screens.currentScreenId());
    putIfPresent(builder, EVENT_SCREEN_NAME, screens.currentScreenName());
    builder.put(EVENT_X, click.x).put(EVENT_Y, click.y);
    tracer.spanBuilder(Constants.CLICK_SPAN_NAME)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(builder.build())
        .setStartTimestamp(click.startMillis, TimeUnit.MILLISECONDS)
        .startSpan()
        .end(click.endMillis, TimeUnit.MILLISECONDS);
  }

  /** The current screen's name, which clicks are attributed to. For tests. */
  String currentScreenName() {
    return screens.currentScreenName();
  }

  private static void putIfPresent(AttributesBuilder builder, AttributeKey<String> key, String value) {
    if (value != null) {
      builder.put(key, value);
    }
  }

  // -- launch --

  private void recordAppLaunch(EnvironmentMetadata metadata, AppLaunch.VersionStore versionStore) {
    ApplicationInfo application = metadata == null ? null : metadata.getApplicationInfo();
    String versionName = application == null ? null : application.getApplicationVersionName();
    String applicationVersion = application == null ? null : application.getApplicationVersion();
    // As on Android, the version is the human-readable name and the build the version code.
    String version = versionName != null ? versionName : applicationVersion;
    String build = versionName != null ? applicationVersion : null;
    if (version == null) {
      version = options.getServiceVersion();
    }
    long now = System.currentTimeMillis();
    AppLaunch launch = AppLaunch.resolve(version, build, versionStore, now);
    appLaunch = launch;

    AttributesBuilder attributes = Attributes.builder().put("event.launch_type", launch.launchType.wireValue);
    if (launch.version != null) {
      attributes.put("event.version", launch.version);
    }
    if (launch.build != null) {
      attributes.put("event.build", launch.build);
    }
    if (launch.previousVersion != null) {
      attributes.put("event.previous_version", launch.previousVersion);
    }

    Long jvmStart = options.getInstrumentations().isLaunchTime() ? AppLaunch.jvmStartMillis() : null;
    // With launch time on, the span covers the startup itself, from JVM start to now.
    long start = jvmStart != null && jvmStart <= now ? jvmStart : now;
    Span span = tracer.spanBuilder(Constants.APP_LAUNCH_SPAN_NAME)
        .setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(attributes.build())
        .setStartTimestamp(start, TimeUnit.MILLISECONDS)
        .startSpan();
    if (start != now) {
      span.addEvent(Constants.APP_START_EVENT_NAME, Attributes.builder()
          .put("start.type", "cold")
          .put("start.duration_ms", now - start)
          .build(), now, TimeUnit.MILLISECONDS);
    }
    span.end(now, TimeUnit.MILLISECONDS);
  }

  // -- lifecycle --

  /** Starts exporting everything queued, without waiting for it, so it is safe on the UI thread. */
  public void flush() {
    tracerProvider.forceFlush();
    loggerProvider.forceFlush();
    meterProvider.forceFlush();
  }

  /** Exports everything queued and waits for it, up to a deadline. For tests and {@code shutdown}. */
  boolean flushAndWait() {
    return awaitAll(FLUSH_TIMEOUT_SECONDS,
        tracerProvider::forceFlush, loggerProvider::forceFlush, meterProvider::forceFlush);
  }

  /**
   * Stops recording crashes, exports what is queued and stops the providers, waiting up to
   * {@link #SHUTDOWN_TIMEOUT_SECONDS} for an unreachable endpoint before giving up on it.
   */
  public void shutdown() {
    if (crashReporter != null) {
      crashReporter.uninstall();
    }
    toolkits.forEach(ToolkitInstrumentation::stop);
    notifyListeners(ObservabilityListener::stopped);
    listeners.clear();
    if (!awaitAll(SHUTDOWN_TIMEOUT_SECONDS,
        tracerProvider::shutdown, loggerProvider::shutdown, meterProvider::shutdown)) {
      logger.warn("LaunchDarkly observability did not finish exporting within {} seconds; telemetry still"
          + " queued is dropped", SHUTDOWN_TIMEOUT_SECONDS);
    }
  }

  /**
   * Runs the operations in parallel and waits for them all under one deadline. Each runs on its own
   * thread, because some block before returning their result: the metric reader's shutdown waits
   * for an export in progress.
   */
  @SafeVarargs
  private static boolean awaitAll(long timeoutSeconds, Supplier<CompletableResultCode>... operations) {
    CountDownLatch done = new CountDownLatch(operations.length);
    for (Supplier<CompletableResultCode> operation : operations) {
      Thread thread = new Thread(() -> {
        try {
          operation.get().join(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
          done.countDown();
        }
      }, "LaunchDarkly-observability-shutdown");
      thread.setDaemon(true);
      thread.start();
    }
    try {
      return done.await(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
