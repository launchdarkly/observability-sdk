package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * A service that exports in memory, with a controllable clock, session ids and launch history.
 */
final class TestTelemetry {
  static final String CREDENTIAL = "mob-test-key";
  static final long BACKGROUND_TIMEOUT = 15L * 60 * 1000;

  final InMemorySpanExporter spans = InMemorySpanExporter.create();
  final InMemoryLogRecordExporter logs = InMemoryLogRecordExporter.create();
  final InMemoryMetricExporter metrics = InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  final AtomicLong clock = new AtomicLong(1_000_000);
  final MemoryVersionStore versions = new MemoryVersionStore();
  final CustomSampler sampler = new CustomSampler(ratio -> false);
  final LogCapture logCapture = Logs.capture();
  final LDLogger logger = LDLogger.withAdapter(logCapture, "test");
  final SessionManager sessions = new SessionManager(Constants.SESSION_MAX_LIFETIME_MILLIS, BACKGROUND_TIMEOUT, clock::get,
      new Random(1));

  ObservabilityService service(ObservabilityOptions options) {
    return service(options, null);
  }

  ObservabilityService service(ObservabilityOptions options, EnvironmentMetadata metadata) {
    return new ObservabilityService(CREDENTIAL, options, metadata, logger,
        exporters(), sessions, sampler, versions, Duration.ofHours(1));
  }

  /**
   * Exporters that keep what they received when the service shuts them down, since the in-memory
   * exporters clear themselves on shutdown.
   */
  ObservabilityService.Exporters exporters() {
    return new ObservabilityService.Exporters(
        new SpanExporter() {
          @Override
          public CompletableResultCode export(Collection<SpanData> batch) {
            return spans.export(batch);
          }

          @Override
          public CompletableResultCode flush() {
            return spans.flush();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        },
        new LogRecordExporter() {
          @Override
          public CompletableResultCode export(Collection<LogRecordData> batch) {
            return logs.export(batch);
          }

          @Override
          public CompletableResultCode flush() {
            return logs.flush();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        },
        new MetricExporter() {
          @Override
          public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return metrics.getAggregationTemporality(instrumentType);
          }

          @Override
          public CompletableResultCode export(Collection<MetricData> batch) {
            return metrics.export(batch);
          }

          @Override
          public CompletableResultCode flush() {
            return metrics.flush();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        });
  }

  /** A factory for {@link ObservabilityRuntime} that builds services exporting here. */
  ObservabilityRuntime.Factory factory() {
    return (credential, options, metadata, ignored) -> new ObservabilityService(credential, options, metadata,
        logger, exporters(), sessions, sampler, versions,
        Duration.ofHours(1));
  }

  /** Options for a test service: no crash handler and no launch span, unless a test turns them on. */
  static ObservabilityOptions.Builder quietOptions() {
    return ObservabilityOptions.builder()
        .shutdownHook(false)
        .analytics(ObservabilityOptions.Analytics.builder().appLaunch(false).build())
        .instrumentations(ObservabilityOptions.Instrumentations.disabled());
  }

  List<SpanData> finishedSpans(ObservabilityService service) {
    assertTrue("flush timed out", service.flushAndWait());
    return spans.getFinishedSpanItems();
  }

  List<SpanData> spansNamed(ObservabilityService service, String name) {
    return finishedSpans(service).stream().filter(s -> s.getName().equals(name)).collect(Collectors.toList());
  }

  List<LogRecordData> finishedLogs(ObservabilityService service) {
    assertTrue("flush timed out", service.flushAndWait());
    return logs.getFinishedLogRecordItems();
  }

  List<MetricData> finishedMetrics(ObservabilityService service) {
    assertTrue("flush timed out", service.flushAndWait());
    return new ArrayList<>(metrics.getFinishedMetricItems());
  }

  static <T> T attribute(io.opentelemetry.api.common.Attributes attributes, AttributeKey<T> key) {
    return attributes.get(key);
  }

  static class MemoryVersionStore implements AppLaunch.VersionStore {
    final Map<String, String> values = new HashMap<>();
    RuntimeException readFailure;
    RuntimeException writeFailure;

    @Override
    public String read() {
      if (readFailure != null) {
        throw readFailure;
      }
      return values.get("version");
    }

    @Override
    public void write(String version) {
      if (writeFailure != null) {
        throw writeFailure;
      }
      values.put("version", version);
    }
  }
}
