package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.GraphQLClient;
import com.launchdarkly.observability.internal.sampling.SamplingApiService;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.subsystems.ApplicationInfo;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;

import java.time.Duration;
import java.util.Map;

/**
 * The one {@link ObservabilityService} a process has. Telemetry is filed under one project, the
 * credential the service started with, so a second start with another credential is refused, as in
 * the server-side Java plugin.
 */
public final class ObservabilityRuntime {
  static final Duration METRIC_INTERVAL = Duration.ofSeconds(10);

  /** Builds a service. Replaced in tests to export in memory. */
  interface Factory {
    ObservabilityService create(String credential, ObservabilityOptions options, EnvironmentMetadata metadata,
        LDLogger logger);
  }

  static final Factory OTLP = ObservabilityRuntime::createOtlp;

  private static final Object LOCK = new Object();
  private static volatile ObservabilityService current;
  private static Factory factory = OTLP;
  private static Thread shutdownHook;

  private ObservabilityRuntime() {}

  /**
   * Starts the service, or returns the one already running for this credential.
   *
   * @param metadata the SDK's metadata, or null without an {@code LDClient}
   * @return the service, or null if one is running for another credential or it could not start
   */
  public static ObservabilityService start(String credential, ObservabilityOptions options,
      EnvironmentMetadata metadata) {
    LDLogger logger = logger(options);
    synchronized (LOCK) {
      ObservabilityService running = current;
      if (running != null) {
        if (running.getCredential().equals(credential)) {
          // LDObserve.init and the plugin both start it; whichever runs first decides the options.
          logger.debug("LaunchDarkly observability is already running for this credential; the options of"
              + " this start are ignored");
          return running;
        }
        logger.warn("LaunchDarkly observability is already running for another credential; this one"
            + " records nothing, and its options are ignored");
        return null;
      }
      ObservabilityService service;
      try {
        service = factory.create(credential, options, metadata, logger);
      } catch (RuntimeException | LinkageError e) {
        // LinkageError: an application that pins an older OpenTelemetry than the plugin needs.
        // Neither may stop the application, or the LDClient, from starting.
        logger.error("LaunchDarkly observability could not start, so it records nothing; flag evaluations"
            + " are unaffected: {}", e.toString());
        return null;
      }
      current = service;
      if (options.isShutdownHook() && shutdownHook == null) {
        shutdownHook = new Thread(ObservabilityRuntime::shutdown, "LaunchDarkly-observability-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
      }
      logger.info("LaunchDarkly observability started: exporting to {} as service \"{}\"",
          options.getOtlpEndpoint(), options.getServiceName());
      return service;
    }
  }

  /** The running service, or null. */
  public static ObservabilityService current() {
    return current;
  }

  /** The running service if it exports under this credential, or null. */
  public static ObservabilityService currentFor(String credential) {
    ObservabilityService service = current;
    return service != null && service.getCredential().equals(credential) ? service : null;
  }

  /**
   * Stops the running service, if any. A later {@link #start} starts a new one. The service stops
   * under the lock {@code start} takes, so a new service cannot start while the old one still has its
   * crash handler installed.
   */
  public static void shutdown() {
    synchronized (LOCK) {
      ObservabilityService service = current;
      current = null;
      if (service != null) {
        service.shutdown();
      }
    }
  }

  static LDLogger logger(ObservabilityOptions options) {
    return LDLogger.withAdapter(Logs.level(options.getLogAdapter(), options.isDebug() ? LDLogLevel.DEBUG : LDLogLevel.INFO),
        options.getLoggerName());
  }

  private static ObservabilityService createOtlp(String credential, ObservabilityOptions options,
      EnvironmentMetadata metadata, LDLogger logger) {
    String endpoint = options.getOtlpEndpoint();
    Map<String, String> headers = options.getCustomHeaders();
    // The exporters count their own exports into GlobalOpenTelemetry's meter provider, which would
    // put those counts into the application's own metrics if it has one.
    OtlpHttpSpanExporter spans = OtlpHttpSpanExporter.builder()
        .setEndpoint(endpoint + Constants.TRACES_PATH)
        .setCompression("gzip")
        .setHeaders(() -> headers)
        .setMeterProvider(MeterProvider.noop())
        .build();
    OtlpHttpLogRecordExporter logs = OtlpHttpLogRecordExporter.builder()
        .setEndpoint(endpoint + Constants.LOGS_PATH)
        .setCompression("gzip")
        .setHeaders(() -> headers)
        .setMeterProvider(MeterProvider.noop())
        .build();
    // Delta temporality, as on Android: each export carries what happened since the last one.
    OtlpHttpMetricExporter metrics = OtlpHttpMetricExporter.builder()
        .setEndpoint(endpoint + Constants.METRICS_PATH)
        .setCompression("gzip")
        .setHeaders(() -> headers)
        .setMeterProvider(MeterProvider.noop())
        .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
        .build();

    CustomSampler sampler = new CustomSampler();
    ObservabilityService service = new ObservabilityService(credential, options, metadata, logger,
        new ObservabilityService.Exporters(spans, logs, metrics), new SessionManager(options.getSessionBackgroundTimeoutMillis()), sampler,
        new AppLaunch.PreferencesVersionStore(applicationKey(options, metadata)), METRIC_INTERVAL);
    fetchSamplingConfig(credential, options, sampler, logger);
    return service;
  }

  /**
   * The application the launch history is kept for: its LaunchDarkly application id, else its service
   * name, else, when the service name is the default that any application might share, its main class
   * or jar as the JVM reports it.
   */
  static String applicationKey(ObservabilityOptions options, EnvironmentMetadata metadata) {
    return applicationKey(options, metadata, System.getProperty("sun.java.command"));
  }

  static String applicationKey(ObservabilityOptions options, EnvironmentMetadata metadata, String javaCommand) {
    ApplicationInfo application = metadata == null ? null : metadata.getApplicationInfo();
    String id = application == null ? null : application.getApplicationId();
    if (id != null) {
      return id;
    }
    if (!options.getServiceName().equals(Constants.DEFAULT_SERVICE_NAME)) {
      return options.getServiceName();
    }
    String main = javaCommand == null ? "" : javaCommand.trim().split("\\s+")[0];
    return main.isEmpty() ? options.getServiceName() : options.getServiceName() + "/" + main;
  }

  /** Fetches the sampling rules on a daemon thread. Until they arrive, nothing is sampled. */
  static Thread fetchSamplingConfig(String credential, ObservabilityOptions options, CustomSampler sampler,
      LDLogger logger) {
    Thread thread = new Thread(() -> {
      GraphQLClient client = new GraphQLClient(options.getBackendUrl(), options.getCustomHeaders(), logger);
      try {
        SamplingConfig config = new SamplingApiService(client, logger).getSamplingConfig(credential);
        if (config != null) {
          sampler.setConfig(config);
          logger.debug("Loaded the LaunchDarkly sampling configuration: {}", config);
        }
      } finally {
        client.close();
      }
    }, "LaunchDarkly-observability-sampling");
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  /** Builds services with {@code factory} until {@link #resetForTest()}. For tests. */
  static void setFactoryForTest(Factory testFactory) {
    synchronized (LOCK) {
      factory = testFactory;
    }
  }

  /** Stops any service and restores the real factory. For tests. */
  static void resetForTest() {
    shutdown();
    synchronized (LOCK) {
      factory = OTLP;
    }
  }

  static Thread shutdownHookForTest() {
    synchronized (LOCK) {
      return shutdownHook;
    }
  }
}
