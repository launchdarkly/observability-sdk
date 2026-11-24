package com.launchdarkly.observability;

import com.launchdarkly.logging.Logs;

import io.opentelemetry.api.common.Attributes;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ObservabilityOptionsTest {
  @Test
  public void theDefaultsAreAndroids() {
    ObservabilityOptions options = ObservabilityOptions.builder().build();
    assertTrue(options.isEnabled());
    assertEquals("observability-java-client", options.getServiceName());
    assertNull(options.getServiceVersion());
    assertEquals("https://otel.observability.app.launchdarkly.com:4318", options.getOtlpEndpoint());
    assertEquals("https://pub.observability.app.launchdarkly.com", options.getBackendUrl());
    assertNull(options.getContextFriendlyName());
    assertTrue(options.getResourceAttributes().isEmpty());
    assertTrue(options.getCustomHeaders().isEmpty());
    assertFalse(options.isDebug());
    assertEquals(ObservabilityOptions.LogLevel.INFO, options.getLogsApiLevel());
    assertTrue(options.getTracesApi().isIncludeErrors());
    assertTrue(options.getTracesApi().isIncludeSpans());
    assertTrue(options.getMetricsApi().isEnabled());
    assertTrue(options.getAnalytics().isTrackEvents());
    assertTrue(options.getAnalytics().isAppLaunch());
    assertTrue(options.getInstrumentations().isCrashReporting());
    assertFalse(options.getInstrumentations().isLaunchTime());
    assertEquals("LaunchDarklyObservabilityPlugin", options.getLoggerName());
    assertTrue(options.isShutdownHook());
    assertEquals(15 * 60 * 1000, options.getSessionBackgroundTimeoutMillis());
    assertTrue(options.getAnalytics().isTaps());
    assertTrue(options.getAnalytics().isScreenViews());
    assertTrue(options.getAnalytics().isAppLifecycle());
    assertTrue(options.getInstrumentations().isUserTaps());
    assertTrue(options.getInstrumentations().isScreens());
    assertFalse("text is opt-in", options.getInstrumentations().isRecordText());
    assertTrue(ObservabilityOptions.Instrumentations.builder().recordText(true).build().isRecordText());
    assertFalse(ObservabilityOptions.Instrumentations.enabled().isRecordText());
  }

  @Test
  public void theDesktopOptionsAreKept() {
    ObservabilityOptions options = ObservabilityOptions.builder()
        .sessionBackgroundTimeoutMillis(1234)
        .analytics(ObservabilityOptions.Analytics.builder().taps(false).screenViews(false).appLifecycle(false).build())
        .instrumentations(ObservabilityOptions.Instrumentations.builder().userTaps(false).screens(false).build())
        .build();
    assertEquals(1234, options.getSessionBackgroundTimeoutMillis());
    assertFalse(options.getAnalytics().isTaps());
    assertFalse(options.getAnalytics().isScreenViews());
    assertFalse(options.getAnalytics().isAppLifecycle());
    assertFalse(options.getInstrumentations().isUserTaps());
    assertFalse(options.getInstrumentations().isScreens());
    assertFalse(ObservabilityOptions.Instrumentations.disabled().isUserTaps());
    assertTrue(ObservabilityOptions.Instrumentations.enabled().isScreens());
  }

  @Test
  public void everySetterIsKept() {
    Attributes resource = Attributes.builder().put("team", "desktop").build();
    ObservabilityOptions options = ObservabilityOptions.builder()
        .enabled(false)
        .serviceName("svc")
        .serviceVersion("1.0")
        .otlpEndpoint("http://otlp")
        .backendUrl("http://backend")
        .contextFriendlyName("Pat")
        .resourceAttributes(resource)
        .customHeaders(Collections.singletonMap("X", "1"))
        .debug(true)
        .logsApiLevel(ObservabilityOptions.LogLevel.NONE)
        .tracesApi(ObservabilityOptions.TracesApi.builder().includeErrors(false).includeSpans(true).build())
        .metricsApi(ObservabilityOptions.MetricsApi.disabled())
        .analytics(ObservabilityOptions.Analytics.builder().trackEvents(false).appLaunch(false).build())
        .instrumentations(ObservabilityOptions.Instrumentations.enabled())
        .logAdapter(Logs.none())
        .loggerName("logger")
        .shutdownHook(false)
        .build();
    assertFalse(options.isEnabled());
    assertEquals("svc", options.getServiceName());
    assertEquals("1.0", options.getServiceVersion());
    assertEquals("http://otlp", options.getOtlpEndpoint());
    assertEquals("http://backend", options.getBackendUrl());
    assertEquals("Pat", options.getContextFriendlyName());
    assertSame(resource, options.getResourceAttributes());
    assertEquals("1", options.getCustomHeaders().get("X"));
    assertTrue(options.isDebug());
    assertEquals(Integer.MAX_VALUE, options.getLogsApiLevel().getLevel());
    assertFalse(options.getTracesApi().isIncludeErrors());
    assertTrue(options.getTracesApi().isIncludeSpans());
    assertFalse(options.getMetricsApi().isEnabled());
    assertFalse(options.getAnalytics().isTrackEvents());
    assertFalse(options.getAnalytics().isAppLaunch());
    assertTrue(options.getInstrumentations().isLaunchTime());
    assertSame(Logs.none(), options.getLogAdapter());
    assertEquals("logger", options.getLoggerName());
    assertFalse(options.isShutdownHook());
    assertFalse(ObservabilityOptions.Instrumentations.disabled().isCrashReporting());
  }

  @Test
  public void levelsFollowTheOpenTelemetrySeverityNumbers() {
    int[] expected = {1, 5, 9, 13, 17, 21};
    ObservabilityOptions.LogLevel[] levels = ObservabilityOptions.LogLevel.values();
    for (int i = 0; i < expected.length; i++) {
      assertEquals(levels[i].name(), expected[i], levels[i].getLevel());
    }
  }

  @Test
  public void metricsCompareByValue() {
    Metric metric = new Metric("m", 1, Attributes.builder().put("a", "b").build());
    assertEquals(metric, new Metric("m", 1, Attributes.builder().put("a", "b").build()));
    assertEquals(metric.hashCode(), new Metric("m", 1, Attributes.builder().put("a", "b").build()).hashCode());
    assertNotEquals(metric, new Metric("m", 2, metric.getAttributes()));
    assertNotEquals(metric, new Metric("n", 1, metric.getAttributes()));
    assertNotEquals(metric, new Metric("m", 1));
    assertNotEquals(metric, "m");
    assertTrue(new Metric("m", 1, null).getAttributes().isEmpty());
    assertEquals("Metric(name=m, value=1.0, attributes={})", new Metric("m", 1).toString());
  }
}
