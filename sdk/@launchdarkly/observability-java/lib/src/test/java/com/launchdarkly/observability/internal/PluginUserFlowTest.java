package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the plugin the way a server app does: an LDClient with the
 * observability plugin, a local sampling endpoint, and a local OTLP collector.
 */
class PluginUserFlowTest {

    private static final String SAMPLING_RESPONSE =
            "{\"data\":{\"sampling\":{\"spans\":[{\"name\":{\"matchValue\":\"health-check\"},"
                    + "\"attributes\":[],\"events\":[],\"samplingRatio\":0}],\"logs\":[]}}}";

    private static final AttributeKey<String> TENANT = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<Long> TIER = AttributeKey.longKey("tenant.tier");
    private static final AttributeKey<String> REGION = AttributeKey.stringKey("region");
    private static final AttributeKey<String> STALE = AttributeKey.stringKey("stale");

    private HttpServer server;
    private final List<String> traces = new CopyOnWriteArrayList<>();
    private final List<byte[]> rawTraces = new CopyOnWriteArrayList<>();
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final List<String> metrics = new CopyOnWriteArrayList<>();
    private final List<String> samplingQueries = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pluginExportsFlagEvaluationAndHonorsSamplingRules() throws Exception {
        startCollector();
        ObservabilityOptions options = options(false);

        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            assertTrue(client.isInitialized());
            assertTrue(waitForSamplingQuery(), "plugin did not fetch sampling config");
            assertTrue(waitForSamplingApplied(), "sampling config was not applied");

            client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            LDObserve.recordLog("user signed in", Severity.INFO, Attributes.empty());
            LDObserve.recordCount("orders.placed", 1, Attributes.empty());
            LDObserve.startSpan("user-checkout", Attributes.empty()).end();
            LDObserve.flush();

            assertFalse(traces.isEmpty(), "no OTLP traces were received");
            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation was not exported: " + summarize(traces));
            assertTrue(payloadContains(traces, "user-checkout"), "manual span was not exported");
            assertTrue(payloadContains(logs, "user signed in"), "log was not exported");
            assertTrue(payloadContains(metrics, "orders.placed"), "metric was not exported");

            assertTrue(waitUntilHealthCheckDropped(), "sampling rule did not drop health-check spans");
            LDObserve.startSpan("user-checkout", Attributes.empty()).end();
            LDObserve.flush();
            assertTrue(payloadContains(traces, "user-checkout"), "unmatched spans should still export");

            // The exports above made the OTLP exporters count their own
            // work; those counters must not reach the customer's metrics.
            LDObserve.flush();
            assertFalse(payloadContains(metrics, "otlp.exporter"),
                    "exporter self-metrics were exported: " + summarize(metrics));
        }
    }

    @Test
    void secondClientWithAnotherSdkKeySharesTheFirstClientsProviders() throws Exception {
        startCollector();
        LDConfig first = new LDConfig.Builder()
                .offline(true)
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options(false)))))
                .build();
        LDConfig second = new LDConfig.Builder()
                .offline(true)
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options(false)))))
                .build();

        try (LDClient a = new LDClient("sdk-test", first)) {
            assertTrue(waitForSamplingQuery(), "first client did not fetch sampling config");
            try (LDClient b = new LDClient("sdk-other", second)) {
                b.boolVariation("second-client-flag", LDContext.create("user-b"), false);
                LDObserve.flush();
                assertTrue(payloadContains(traces, "second-client-flag"),
                        "the second client's evaluations should still export on the shared providers");
                assertTrue(payloadContains(traces, "sdk-test"), "exports should carry the first SDK key");
                assertFalse(payloadContains(traces, "sdk-other"), "the second SDK key must not leak into exports");
                Thread.sleep(300);
                assertEquals(1, samplingQueries.size(),
                        "the second client must not replace the first client's sampling config");
            }
        }
    }

    @Test
    void manualStartKeepsSamplingConfigFetchedBeforeProvidersStart() throws Exception {
        startCollector();
        ObservabilityOptions options = options(true);

        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            assertTrue(client.isInitialized());
            assertFalse(OtelManager.isInitialized());
            assertTrue(waitForSamplingQuery(), "plugin did not fetch sampling config");
            LDObserve.start("sdk-test", options);
            assertTrue(waitForSamplingApplied(), "manual start lost the sampling config");
            assertTrue(waitUntilHealthCheckDropped(), "manual start did not apply the sampling rule");
        }
        assertEquals(1, samplingQueries.size(), "manual start must not fetch the sampling config again");
    }

    @Test
    void standaloneStartFetchesAndAppliesSamplingConfig() throws Exception {
        startCollector();

        LDObserve.start("sdk-test", options(true));

        assertTrue(waitForSamplingQuery(), "standalone start did not fetch sampling config");
        assertTrue(waitForSamplingApplied(), "standalone start did not apply the sampling config");
        assertTrue(waitUntilHealthCheckDropped(), "standalone start did not apply the sampling rule");
    }

    @Test
    void preInitCallDoesNotStickGlobalOnNoopAndLaterSpanExports() throws Exception {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
        LDObserve.startSpan("before-init", Attributes.empty()).end();
        LDObserve.recordLog("before init", Severity.INFO, Attributes.empty());

        startCollector();
        ObservabilityOptions options = options(false);
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            assertNotSame(OpenTelemetry.noop(), GlobalOpenTelemetry.get());
            client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            LDObserve.startSpan("after-init", Attributes.empty()).end();
            LDObserve.flush();
            assertTrue(payloadContains(traces, "after-init"),
                    "span started after a pre-init call was not exported: " + summarize(traces));
            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation was not exported after a pre-init call: " + summarize(traces));
        }
    }

    @Test
    void flagEvaluationExportsWhenGlobalWasClaimedFirst() throws Exception {
        // A library that calls GlobalOpenTelemetry.get() before the LDClient is
        // built locks the global onto a no-op, so the plugin cannot register.
        GlobalOpenTelemetry.get();

        startCollector();
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options(false)))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            LDObserve.flush();
            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation was not exported with a pre-claimed global: " + summarize(traces));
            assertTrue(payloadContains(traces, "feature_flag.key"));
        }
    }

    @Test
    void recordErrorInsidePluginSpanAddsExceptionToThatSpan() throws Exception {
        startCollector();
        LDObserve.start("sdk-test", options(true));

        Span request = LDObserve.startSpan("GET /checkout", Attributes.empty());
        try (Scope ignored = request.makeCurrent()) {
            LDObserve.recordError(new IllegalStateException("payment declined"),
                    Attributes.of(AttributeKey.stringKey("order.id"), "order-42"));
        } finally {
            request.end();
        }
        LDObserve.flush();

        assertTrue(payloadContains(traces, "GET /checkout"), "request span was not exported: " + summarize(traces));
        assertTrue(payloadContains(traces, "payment declined"), "exception was not recorded: " + summarize(traces));
        assertTrue(payloadContains(traces, "order-42"), "attributes were not recorded on the exception event");
        assertFalse(payloadContains(traces, Constants.ERROR_SPAN_NAME),
                "recordError started a separate error span instead of using the current span");
    }

    @Test
    void recordErrorInsideForeignSpanExportsErrorSpanInSameTrace() throws Exception {
        // The application (or the OpenTelemetry Java agent) owns the current
        // span; an exception recorded on it would never reach LaunchDarkly.
        InMemorySpanExporter appExporter = InMemorySpanExporter.create();
        SdkTracerProvider appTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(appExporter))
                .build();
        OpenTelemetrySdk appSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(appTracerProvider)
                .buildAndRegisterGlobal();

        startCollector();
        LDObserve.start("sdk-test", options(true));

        try {
            Span request = appSdk.getTracer("app").spanBuilder("GET /checkout").startSpan();
            try (Scope ignored = request.makeCurrent()) {
                LDObserve.recordError(new IllegalStateException("payment declined"), Attributes.empty());
            } finally {
                request.end();
            }
            LDObserve.flush();

            assertTrue(payloadContains(traces, Constants.ERROR_SPAN_NAME),
                    "error span was not exported to LaunchDarkly: " + summarize(traces));
            assertTrue(payloadContains(traces, "payment declined"));
            assertTrue(rawPayloadContains(rawTraces, request.getSpanContext().getTraceIdBytes()),
                    "error span is not in the application's trace");
            assertTrue(appExporter.getFinishedSpanItems().get(0).getEvents().isEmpty(),
                    "the exception went to the application's span instead of LaunchDarkly");
        } finally {
            appTracerProvider.shutdown();
        }
    }

    @Test
    void flagEvaluationExportsUnderApplicationOwnedGlobal() throws Exception {
        // The application (or the OpenTelemetry Java agent) owns the global.
        InMemorySpanExporter appExporter = InMemorySpanExporter.create();
        SdkTracerProvider appTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(appExporter))
                .build();
        OpenTelemetrySdk appSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(appTracerProvider)
                .buildAndRegisterGlobal();

        startCollector();
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options(false)))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            Span request = appSdk.getTracer("app").spanBuilder("GET /checkout").startSpan();
            try (Scope ignored = request.makeCurrent()) {
                client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            } finally {
                request.end();
            }
            LDObserve.flush();

            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation was not exported under an application-owned global: " + summarize(traces));
            assertTrue(rawPayloadContains(rawTraces, request.getSpanContext().getTraceIdBytes()),
                    "evaluation span is not in the application's trace");

            // The feature_flag event still lands on the caller's current span.
            List<SpanData> appSpans = appExporter.getFinishedSpanItems();
            assertEquals(1, appSpans.size());
            assertEquals(1, appSpans.get(0).getEvents().size());
            assertEquals("feature_flag", appSpans.get(0).getEvents().get(0).getName());
            assertEquals("checkout-enabled", appSpans.get(0).getEvents().get(0).getAttributes()
                    .get(AttributeKey.stringKey("feature_flag.key")));
        } finally {
            appTracerProvider.shutdown();
        }
    }

    @Test
    void flagEvaluationExportsUnderUnsampledForeignParent() throws Exception {
        // The application's SDK (or the agent) samples this request out.
        SdkTracerProvider appTracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOff())
                .build();
        OpenTelemetrySdk appSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(appTracerProvider)
                .buildAndRegisterGlobal();

        startCollector();
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options(false)))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            Span request = appSdk.getTracer("app").spanBuilder("GET /checkout").startSpan();
            assertFalse(request.getSpanContext().isSampled());
            try (Scope ignored = request.makeCurrent()) {
                client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
                LDObserve.startSpan("user-checkout", Attributes.empty()).end();
            } finally {
                request.end();
            }
            LDObserve.flush();

            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation under an unsampled parent was dropped: " + summarize(traces));
            assertTrue(payloadContains(traces, "user-checkout"),
                    "LDObserve span under an unsampled parent was dropped");
        } finally {
            appTracerProvider.shutdown();
        }
    }

    @Test
    void flagEvaluationExportsAfterManualStart() throws Exception {
        startCollector();
        ObservabilityOptions options = options(true);
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            LDObserve.start("sdk-test", options);
            client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            LDObserve.flush();
            assertTrue(payloadContains(traces, "checkout-enabled"),
                    "flag evaluation was not exported after manual start: " + summarize(traces));
        }
    }

    @Test
    void globalAttributesSetBeforeStartReachEverySignal() throws Exception {
        LDObserve.setAttributes(Attributes.of(TENANT, "tenant-global-7", TIER, 3L));

        startCollector();
        InMemorySpanExporter spans = InMemorySpanExporter.create();
        InMemoryLogRecordExporter logRecords = InMemoryLogRecordExporter.create();
        ObservabilityOptions options = optionsBuilder(false)
                .spanProcessor(SimpleSpanProcessor.create(spans))
                .logProcessor(SimpleLogRecordProcessor.create(logRecords))
                .build();
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            LDObserve.startSpan("user-checkout", Attributes.empty()).end();
            LDObserve.recordError(new IllegalStateException("payment declined"), Attributes.empty());
            LDObserve.recordLog("user signed in", Severity.INFO, Attributes.empty());
            LDObserve.recordCount("orders.placed", 1, Attributes.empty());
            LDObserve.recordMetric("queue.depth", 4.0, Attributes.empty());
            LDObserve.flush();
        }

        List<SpanData> finished = spans.getFinishedSpanItems();
        List<String> names = new ArrayList<>();
        for (SpanData span : finished) {
            names.add(span.getName());
            assertEquals("tenant-global-7", span.getAttributes().get(TENANT), span.getName() + " has no global");
            assertEquals(3L, span.getAttributes().get(TIER), span.getName() + " has no global");
        }
        assertTrue(names.contains("user-checkout"), "manual span missing: " + names);
        assertTrue(names.contains(Constants.ERROR_SPAN_NAME), "error span missing: " + names);
        assertTrue(finished.stream().anyMatch(s -> "checkout-enabled".equals(
                        s.getAttributes().get(AttributeKey.stringKey("feature_flag.key")))),
                "flag evaluation span missing: " + names);

        List<LogRecordData> records = logRecords.getFinishedLogRecordItems();
        assertEquals(1, records.size());
        assertEquals("tenant-global-7", records.get(0).getAttributes().get(TENANT));

        assertTrue(payloadContains(traces, "tenant-global-7"), "globals were not exported on spans");
        assertTrue(payloadContains(logs, "tenant-global-7"), "globals were not exported on logs");
        assertTrue(payloadContains(metrics, "orders.placed") && payloadContains(metrics, "tenant-global-7"),
                "globals were not exported on metrics: " + summarize(metrics));
    }

    @Test
    void perCallAttributesWinAndSpansKeepTheGlobalsCurrentAtStart() throws Exception {
        startCollector();
        InMemorySpanExporter spans = InMemorySpanExporter.create();
        InMemoryLogRecordExporter logRecords = InMemoryLogRecordExporter.create();
        LDObserve.start("sdk-test", optionsBuilder(true)
                .spanProcessor(SimpleSpanProcessor.create(spans))
                .logProcessor(SimpleLogRecordProcessor.create(logRecords))
                .build());

        LDObserve.setAttributes(Attributes.of(REGION, "region-global", STALE, "stale-global"));
        Span early = LDObserve.startSpan("early", Attributes.empty());
        LDObserve.setAttributes(Attributes.of(REGION, "region-updated"));
        LDObserve.removeAttributes(List.of("stale"));
        early.end();
        LDObserve.startSpan("explicit", Attributes.of(REGION, "region-call")).end();
        LDObserve.startSpan("late", Attributes.empty()).end();
        LDObserve.recordLog("explicit log", Severity.INFO, Attributes.of(REGION, "region-call"));
        LDObserve.recordCount("orders.region", 1, Attributes.of(REGION, "region-call"));
        LDObserve.flush();

        SpanData earlySpan = span(spans, "early");
        assertEquals("region-global", earlySpan.getAttributes().get(REGION));
        assertEquals("stale-global", earlySpan.getAttributes().get(STALE));
        SpanData explicitSpan = span(spans, "explicit");
        assertEquals("region-call", explicitSpan.getAttributes().get(REGION));
        assertNull(explicitSpan.getAttributes().get(STALE), "a removed global must not be applied");
        assertEquals("region-updated", span(spans, "late").getAttributes().get(REGION));

        assertEquals("region-call", logRecords.getFinishedLogRecordItems().get(0).getAttributes().get(REGION));

        assertTrue(payloadContains(metrics, "region-call"), "per-call metric attribute missing: " + summarize(metrics));
        assertFalse(payloadContains(metrics, "region-updated"), "a global overwrote a per-call metric attribute");
    }

    @Test
    void userProcessorsRunInOrderAndTheirChangesAreExported() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        AttributeKey<String> enriched = AttributeKey.stringKey("enriched.by");
        AttributeKey<String> saw = AttributeKey.stringKey("second.saw");
        SpanProcessor first = new OnStartProcessor(span -> {
            calls.add("first-span");
            span.setAttribute(enriched, "first-processor");
        });
        SpanProcessor second = new OnStartProcessor(span -> {
            calls.add("second-span");
            span.setAttribute(saw, span.getAttribute(enriched) + "|" + span.getAttribute(TENANT));
        });
        LogRecordProcessor firstLog = (context, record) -> {
            calls.add("first-log");
            record.setAttribute(enriched, "first-log-processor");
        };
        LogRecordProcessor secondLog = (context, record) -> {
            calls.add("second-log");
            Attributes seen = record.toLogRecordData().getAttributes();
            record.setAttribute(saw, seen.get(enriched) + "|" + seen.get(TENANT));
        };
        LDObserve.setAttributes(Attributes.of(TENANT, "tenant-global-7"));

        startCollector();
        LDObserve.start("sdk-test", optionsBuilder(true)
                .spanProcessors(List.of(first, second))
                .logProcessors(List.of(firstLog, secondLog))
                .build());

        LDObserve.startSpan("ordered", Attributes.empty()).end();
        LDObserve.recordLog("ordered log", Severity.INFO, Attributes.empty());
        LDObserve.flush();

        assertEquals(List.of("first-span", "second-span", "first-log", "second-log"), calls);
        assertTrue(payloadContains(traces, "first-processor|tenant-global-7"),
                "user span processor changes were not exported: " + summarize(traces));
        assertTrue(payloadContains(logs, "first-log-processor|tenant-global-7"),
                "user log processor changes were not exported: " + summarize(logs));

        // The sampling rules still apply after the user's processors.
        assertTrue(waitForSamplingApplied(), "sampling config was not applied");
        assertTrue(waitUntilHealthCheckDropped(), "sampling rule did not drop health-check spans");
    }

    @Test
    void throwingUserProcessorsDoNotBreakExport() throws Exception {
        InMemorySpanExporter after = InMemorySpanExporter.create();
        startCollector();
        LDObserve.start("sdk-test", optionsBuilder(true)
                .spanProcessor(new SafeProcessorTest.ThrowingSpanProcessor())
                .spanProcessor(SimpleSpanProcessor.create(after))
                .logProcessor(new SafeProcessorTest.ThrowingLogProcessor())
                .build());

        assertDoesNotThrow(() -> LDObserve.startSpan("after-throw", Attributes.empty()).end());
        assertDoesNotThrow(() -> LDObserve.recordLog("log after throw", Severity.INFO, Attributes.empty()));
        assertDoesNotThrow(() -> LDObserve.recordError(new IllegalStateException("still recorded"), Attributes.empty()));
        assertDoesNotThrow(LDObserve::flush);

        assertTrue(payloadContains(traces, "after-throw"), "span was not exported: " + summarize(traces));
        assertTrue(payloadContains(traces, "still recorded"), "error span was not exported");
        assertTrue(payloadContains(logs, "log after throw"), "log was not exported: " + summarize(logs));
        assertEquals(2, after.getFinishedSpanItems().size(), "processors after the throwing one must still run");

        assertDoesNotThrow(LDObserve::shutdown);
    }

    private static SpanData span(InMemorySpanExporter exporter, String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no span named " + name));
    }

    /** A span processor that only acts in {@code onStart}. */
    private static final class OnStartProcessor implements SpanProcessor {
        private final Consumer<ReadWriteSpan> onStart;

        OnStartProcessor(Consumer<ReadWriteSpan> onStart) {
            this.onStart = onStart;
        }

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            onStart.accept(span);
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return false;
        }
    }

    private ObservabilityOptions options(boolean manualStart) {
        return optionsBuilder(manualStart).build();
    }

    private ObservabilityOptions.Builder optionsBuilder(boolean manualStart) {
        String base = "http".concat("://") + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        return new ObservabilityOptions.Builder()
                .serviceName("orders-service")
                .serviceVersion("1.2.3")
                .environment("test")
                .otlpEndpoint(base)
                .backendUrl(base + "/sampling")
                .manualStart(manualStart);
    }

    private void startCollector() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/sampling", exchange -> {
            samplingQueries.add(bodyAsString(exchange));
            respond(exchange, 200, "application/json", SAMPLING_RESPONSE.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/traces", exchange -> {
            byte[] body = bodyBytes(exchange);
            rawTraces.add(body);
            traces.add(new String(body, StandardCharsets.UTF_8));
            respond(exchange, 200, "application/x-protobuf", new byte[0]);
        });
        server.createContext("/v1/logs", exchange -> {
            logs.add(bodyAsString(exchange));
            respond(exchange, 200, "application/x-protobuf", new byte[0]);
        });
        server.createContext("/v1/metrics", exchange -> {
            metrics.add(bodyAsString(exchange));
            respond(exchange, 200, "application/x-protobuf", new byte[0]);
        });
        server.start();
    }

    private boolean waitForSamplingQuery() throws InterruptedException {
        for (int i = 0; i < 50 && samplingQueries.isEmpty(); i++) {
            Thread.sleep(100);
        }
        return !samplingQueries.isEmpty();
    }

    private boolean waitForSamplingApplied() throws InterruptedException {
        for (int i = 0; i < 50 && !OtelManager.isSamplingConfigured(); i++) {
            Thread.sleep(100);
        }
        return OtelManager.isSamplingConfigured();
    }

    private boolean waitUntilHealthCheckDropped() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            int before = traces.size();
            LDObserve.startSpan("health-check", Attributes.empty()).end();
            LDObserve.startSpan("keep-alive", Attributes.empty()).end();
            LDObserve.flush();
            if (newPayloadsContain(traces, before, "keep-alive")
                    && !newPayloadsContain(traces, before, "health-check")) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static String summarize(List<String> payloads) {
        StringBuilder text = new StringBuilder();
        for (String payload : payloads) {
            for (int i = 0; i < payload.length(); i++) {
                char c = payload.charAt(i);
                text.append(c >= 32 && c < 127 ? c : ' ');
            }
            text.append('\n');
        }
        String rendered = text.toString();
        return rendered.length() > 1500 ? rendered.substring(0, 1500) : rendered;
    }

    private static boolean payloadContains(List<String> payloads, String needle) {
        return newPayloadsContain(payloads, 0, needle);
    }

    private static boolean newPayloadsContain(List<String> payloads, int from, String needle) {
        List<String> snapshot = new ArrayList<>(payloads);
        for (int i = from; i < snapshot.size(); i++) {
            if (snapshot.get(i).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rawPayloadContains(List<byte[]> payloads, byte[] needle) {
        for (byte[] payload : payloads) {
            outer:
            for (int i = 0; i + needle.length <= payload.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (payload[i + j] != needle[j]) {
                        continue outer;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static String bodyAsString(HttpExchange exchange) throws IOException {
        return new String(bodyBytes(exchange), StandardCharsets.UTF_8);
    }

    private static byte[] bodyBytes(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                raw = in.readAllBytes();
            }
        }
        return raw;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
