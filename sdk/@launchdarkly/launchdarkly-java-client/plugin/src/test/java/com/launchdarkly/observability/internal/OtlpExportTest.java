package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.Metric;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.LDValue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The real exporters and sampling request, against a local server standing in for LaunchDarkly. */
public class OtlpExportTest {
  private final MockWebServer server = new MockWebServer();
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private final Map<String, byte[]> bodies = new ConcurrentHashMap<>();

  @After
  public void tearDown() throws IOException {
    ObservabilityRuntime.resetForTest();
    server.shutdown();
  }

  @Test
  public void everySignalIsExportedGzippedWithTheCustomHeaders() throws Exception {
    server.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        requests.add(request);
        bodies.put(request.getPath(), request.getBody().readByteArray());
        if (request.getPath().equals("/graphql")) {
          return new MockResponse().setBody("{\"data\":{\"sampling\":{\"spans\":[],\"logs\":[]}}}");
        }
        return new MockResponse().setResponseCode(200);
      }
    });
    server.start();
    String base = server.url("").toString().replaceAll("/$", "");
    LDObserve.init("mob-e2e", TestTelemetry.quietOptions()
        .otlpEndpoint(base)
        .backendUrl(base + "/graphql")
        .customHeaders(Collections.singletonMap("X-Custom", "on"))
        .build());
    assertNotNull(ObservabilityRuntime.current());

    LDObserve.startSpan("exported-span", Attributes.empty()).end();
    LDObserve.recordLog("exported-log", Severity.WARN, Attributes.empty());
    LDObserve.recordIncr(new Metric("exported-metric", 1));
    LDObserve.shutdown();

    List<String> paths = new CopyOnWriteArrayList<>();
    for (RecordedRequest request : requests) {
      paths.add(request.getPath());
      assertEquals(request.getPath(), "on", request.getHeader("X-Custom"));
      if (request.getPath().startsWith("/v1/")) {
        assertEquals("gzip", request.getHeader("Content-Encoding"));
        assertEquals("application/x-protobuf", request.getHeader("Content-Type"));
      }
    }
    assertThat(paths, hasItems("/graphql", "/v1/traces", "/v1/logs", "/v1/metrics"));
    assertThat(SamplingGunzip.text(bodies.get("/v1/traces")), containsString("exported-span"));
    assertThat(SamplingGunzip.text(bodies.get("/v1/traces")), containsString("mob-e2e"));
    assertThat(SamplingGunzip.text(bodies.get("/v1/logs")), containsString("exported-log"));
    assertThat(SamplingGunzip.text(bodies.get("/v1/metrics")), containsString("exported-metric"));
  }

  @Test
  public void theSamplingRulesAreAppliedOnceFetched() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"data\":{\"sampling\":{\"spans\":[{\"name\":{\"matchValue\":"
        + "\"noisy\"},\"samplingRatio\":0}],\"logs\":[]}}}"));
    server.start();
    TestTelemetry telemetry = new TestTelemetry();
    ObservabilityOptions options = TestTelemetry.quietOptions().backendUrl(server.url("/graphql").toString()).build();
    com.launchdarkly.observability.internal.sampling.CustomSampler sampler =
        new com.launchdarkly.observability.internal.sampling.CustomSampler();
    Thread fetch = ObservabilityRuntime.fetchSamplingConfig("mob-key", options, sampler, telemetry.logger);
    fetch.join(5000);
    assertTrue(sampler.isSamplingEnabled());
    LDValue body = LDValue.parse(SamplingGunzip.text(server.takeRequest().getBody().readByteArray()));
    assertEquals("mob-key", body.get("variables").get("organization_verbose_id").stringValue());
  }

  @Test
  public void aFailedFetchLeavesEverythingUnsampled() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(503));
    server.start();
    TestTelemetry telemetry = new TestTelemetry();
    com.launchdarkly.observability.internal.sampling.CustomSampler sampler =
        new com.launchdarkly.observability.internal.sampling.CustomSampler();
    ObservabilityRuntime.fetchSamplingConfig("k", TestTelemetry.quietOptions()
        .backendUrl(server.url("/graphql").toString()).build(), sampler, telemetry.logger).join(5000);
    assertTrue(!sampler.isSamplingEnabled());
  }

  /** OTLP bodies are protobuf; their strings are readable once ungzipped. */
  static final class SamplingGunzip {
    static String text(byte[] gzipped) throws IOException {
      assertNotNull("nothing was sent", gzipped);
      return com.launchdarkly.observability.internal.sampling.SamplingTestAccess.gunzip(gzipped);
    }
  }
}
