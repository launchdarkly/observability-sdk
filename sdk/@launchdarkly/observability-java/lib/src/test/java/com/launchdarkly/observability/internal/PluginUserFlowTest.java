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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
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
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the plugin the way a server app does: an LDClient with the
 * observability plugin, a local sampling endpoint, and a local OTLP collector.
 */
class PluginUserFlowTest {

    private static final String SAMPLING_RESPONSE =
            "{\"data\":{\"sampling\":{\"spans\":[{\"name\":{\"matchValue\":\"health-check\"},"
                    + "\"attributes\":[],\"events\":[],\"samplingRatio\":0}],\"logs\":[]}}}";

    private HttpServer server;
    private final List<String> traces = new CopyOnWriteArrayList<>();
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
    }

    private ObservabilityOptions options(boolean manualStart) {
        String base = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        return new ObservabilityOptions.Builder()
                .serviceName("orders-service")
                .serviceVersion("1.2.3")
                .environment("test")
                .otlpEndpoint(base)
                .backendUrl(base + "/sampling")
                .manualStart(manualStart)
                .build();
    }

    private void startCollector() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/sampling", exchange -> {
            samplingQueries.add(bodyAsString(exchange));
            respond(exchange, 200, "application/json", SAMPLING_RESPONSE.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/traces", exchange -> {
            traces.add(bodyAsString(exchange));
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

    private static String bodyAsString(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                raw = in.readAllBytes();
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
