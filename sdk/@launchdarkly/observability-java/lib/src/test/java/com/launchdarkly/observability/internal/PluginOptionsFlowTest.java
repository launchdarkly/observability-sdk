package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Exercises the server-oriented options (headers, proxy, resource
 * attributes, log level, enabled) through a real LDClient against a local
 * collector that records every request it receives.
 */
class PluginOptionsFlowTest {

    private static final String SAMPLING_RESPONSE = "{\"data\":{\"sampling\":{\"spans\":[],\"logs\":[]}}}";

    /** One recorded HTTP request: its request-line URI, a header of interest and the body. */
    private static final class Received {
        final String uri;
        final String path;
        final String testHeader;
        final String body;

        Received(String uri, String path, String testHeader, String body) {
            this.uri = uri;
            this.path = path;
            this.testHeader = testHeader;
            this.body = body;
        }
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startCollector() throws IOException {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // One root context so the server also works as an HTTP proxy, where the
        // request line carries an absolute URI.
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String header = exchange.getRequestHeaders().getFirst("X-Test-Header");
            received.add(new Received(exchange.getRequestURI().toString(), path,
                    header == null ? "" : header, bodyAsString(exchange)));
            if (path.endsWith("/sampling")) {
                respond(exchange, "application/json", SAMPLING_RESPONSE.getBytes(StandardCharsets.UTF_8));
            } else {
                respond(exchange, "application/x-protobuf", new byte[0]);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
        server.stop(0);
    }

    @Test
    void customHeadersAndResourceAttributesReachExportsAndSamplingRequest() throws Exception {
        ObservabilityOptions options = builder()
                .customHeader("X-Test-Header", "abc")
                .resourceAttributes(Attributes.of(AttributeKey.stringKey("team"), "payments-team"))
                .build();

        try (LDClient client = newClient(options)) {
            assertTrue(client.isInitialized());
            assertTrue(waitFor("/sampling"), "sampling config was not requested");
            LDObserve.startSpan("checkout", Attributes.empty()).end();
            LDObserve.recordLog("checkout done", Severity.INFO, Attributes.empty());
            LDObserve.recordCount("orders", 1, Attributes.empty());
            LDObserve.flush();
        }

        assertTrue(waitFor("/v1/traces"), "no trace export");
        assertTrue(waitFor("/v1/logs"), "no log export");
        assertTrue(waitFor("/v1/metrics"), "no metric export");
        for (String path : List.of("/sampling", "/v1/traces", "/v1/logs", "/v1/metrics")) {
            assertTrue(all(path).stream().allMatch(r -> "abc".equals(r.testHeader)),
                    "custom header missing on " + path);
        }
        assertTrue(anyBodyContains("/v1/traces", "payments-team"), "resource attribute missing on spans");
        assertTrue(anyBodyContains("/v1/logs", "payments-team"), "resource attribute missing on logs");
        assertTrue(anyBodyContains("/v1/metrics", "payments-team"), "resource attribute missing on metrics");
    }

    @Test
    void proxySelectorRoutesExportsAndSamplingRequestThroughProxy() throws Exception {
        InetSocketAddress proxy = server.getAddress();
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .serviceName("orders-service")
                .otlpEndpoint("http".concat("://otlp.invalid:4318"))
                .backendUrl("http".concat("://backend.invalid/sampling"))
                .proxyHostAndPort(proxy.getHostString(), proxy.getPort())
                .build();

        try (LDClient client = newClient(options)) {
            assertTrue(client.isInitialized());
            assertTrue(waitFor("/sampling"), "sampling request did not go through the proxy");
            LDObserve.startSpan("checkout", Attributes.empty()).end();
            LDObserve.flush();
        }

        assertTrue(waitFor("/v1/traces"), "trace export did not go through the proxy");
        assertTrue(all("/sampling").stream().allMatch(r -> r.uri.contains("backend.invalid")),
                "proxy did not see the absolute sampling URI");
        assertTrue(all("/v1/traces").stream().allMatch(r -> r.uri.contains("otlp.invalid")),
                "proxy did not see the absolute OTLP URI");
    }

    @Test
    void logsBelowConfiguredLevelAreNotExported() throws Exception {
        ObservabilityOptions options = builder().logsApiLevel(ObservabilityOptions.LogLevel.WARN).build();

        try (LDClient client = newClient(options)) {
            assertTrue(client.isInitialized());
            LDObserve.recordLog("debug-noise", Severity.DEBUG, Attributes.empty());
            LDObserve.recordLog("info-noise", Severity.INFO, Attributes.empty());
            LDObserve.recordLog("warn-signal", Severity.WARN, Attributes.empty());
            LDObserve.recordLog("error-signal", Severity.ERROR, Attributes.empty());
            LDObserve.flush();
        }

        assertTrue(waitFor("/v1/logs"), "no log export");
        assertTrue(anyBodyContains("/v1/logs", "warn-signal"));
        assertTrue(anyBodyContains("/v1/logs", "error-signal"));
        assertFalse(anyBodyContains("/v1/logs", "debug-noise"), "DEBUG record exported below WARN threshold");
        assertFalse(anyBodyContains("/v1/logs", "info-noise"), "INFO record exported below WARN threshold");
    }

    @Test
    void logLevelNoneExportsNoLogsButKeepsTraces() throws Exception {
        ObservabilityOptions options = builder().logsApiLevel(ObservabilityOptions.LogLevel.NONE).build();

        try (LDClient client = newClient(options)) {
            assertTrue(client.isInitialized());
            LDObserve.recordLog("fatal-but-disabled", Severity.FATAL, Attributes.empty());
            LDObserve.startSpan("checkout", Attributes.empty()).end();
            LDObserve.flush();
        }

        assertTrue(waitFor("/v1/traces"), "no trace export");
        assertFalse(anyBodyContains("/v1/logs", "fatal-but-disabled"), "log exported with level NONE");
    }

    @Test
    void disabledPluginStartsNothingAndStaysNoop() throws Exception {
        ObservabilityOptions options = builder().enabled(false).build();

        try (LDClient client = newClient(options)) {
            assertTrue(client.isInitialized());
            assertFalse(OtelManager.isInitialized(), "disabled plugin started providers");
            client.boolVariation("checkout-enabled", com.launchdarkly.sdk.LDContext.create("u"), false);
            LDObserve.startSpan("checkout", Attributes.empty()).end();
            LDObserve.recordLog("ignored", Severity.ERROR, Attributes.empty());
            LDObserve.flush();
            Thread.sleep(500);
        }

        assertTrue(received.isEmpty(), "disabled plugin made HTTP requests: " + summarize());
        assertFalse(OtelManager.isInitialized());
    }

    private ObservabilityOptions.Builder builder() {
        String base = "http".concat("://") + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        return new ObservabilityOptions.Builder()
                .serviceName("orders-service")
                .serviceVersion("1.2.3")
                .environment("test")
                .otlpEndpoint(base)
                .backendUrl(base + "/sampling");
    }

    private static LDClient newClient(ObservabilityOptions options) {
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();
        return new LDClient("sdk-test", config);
    }

    private List<Received> all(String path) {
        List<Received> out = new ArrayList<>();
        for (Received r : received) {
            if (r.path.endsWith(path)) {
                out.add(r);
            }
        }
        return out;
    }

    private boolean anyBodyContains(String path, String needle) {
        return all(path).stream().anyMatch(r -> r.body.contains(needle));
    }

    private boolean waitFor(String path) throws InterruptedException {
        for (int i = 0; i < 100 && all(path).isEmpty(); i++) {
            Thread.sleep(100);
        }
        return !all(path).isEmpty();
    }

    private String summarize() {
        StringBuilder sb = new StringBuilder();
        for (Received r : received) {
            sb.append(r.uri).append(' ');
        }
        return sb.toString();
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

    private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
