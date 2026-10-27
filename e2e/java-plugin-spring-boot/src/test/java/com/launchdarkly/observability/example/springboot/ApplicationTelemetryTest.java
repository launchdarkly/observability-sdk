package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.observability.LDObserve;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Boots the whole application against a local OTLP collector and sampling
 * endpoint, drives its routes over HTTP, and checks what the plugin exports.
 * The LaunchDarkly client is offline (no SDK key), so flags return defaults.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTelemetryTest {

    /** Drops health-check request spans, keeps everything else. */
    private static final String SAMPLING_RESPONSE =
            "{\"data\":{\"sampling\":{\"spans\":["
                    + "{\"name\":{\"regexValue\":\"^GET /actuator/health\"},\"attributes\":[],\"events\":[],\"samplingRatio\":0}"
                    + "],\"logs\":[]}}}";

    private static final List<String> TRACES = new CopyOnWriteArrayList<>();
    private static final List<String> LOGS = new CopyOnWriteArrayList<>();
    private static final List<String> METRICS = new CopyOnWriteArrayList<>();
    private static final List<String> SAMPLING_QUERIES = new CopyOnWriteArrayList<>();
    private static final HttpServer COLLECTOR = startCollector();

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void pointPluginAtCollector(DynamicPropertyRegistry registry) {
        String base = "http".concat("://") + COLLECTOR.getAddress().getHostString() + ":" + COLLECTOR.getAddress().getPort();
        registry.add("OTEL_EXPORTER_OTLP_ENDPOINT", () -> base);
        registry.add("LD_OBSERVABILITY_BACKEND_URL", () -> base + "/sampling");
        registry.add("LAUNCHDARKLY_SDK_KEY", () -> "");
        registry.add("SERVICE_VERSION", () -> "test-version");
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    @Test
    void indexEvaluatesFlagInsideRequestSpan() {
        ResponseEntity<String> response = get("/", "alice");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, World!\n");

        awaitExported(TRACES, "GET /", "LDClient.boolVariation", "example-greeting");
        // The plugin's hook adds the feature_flag event to the current
        // (request) span and records its own evaluation span.
        awaitExported(TRACES, "GET /", "feature_flag", "feature_flag.key");
        awaitExported(TRACES, "java-plugin-spring-boot-example", "test-version", "example.framework", "spring-boot");
    }

    @Test
    void rollDiceUsesFlagDefaultAndRecordsAttribute() {
        ResponseEntity<Map> response = http.exchange("/rolldice", HttpMethod.GET, withUser("bob"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("user", "bob").containsEntry("sides", 6);
        assertThat((Integer) response.getBody().get("roll")).isBetween(1, 6);

        awaitExported(TRACES, "GET /rolldice", "dice.roll");
        awaitExported(TRACES, "LDClient.intVariation", "dice-sides", "feature_flag.context.id", "bob");
    }

    @Test
    void flagRouteReturnsJsonDefault() {
        ResponseEntity<Map> response = http.exchange("/flags/some-flag", HttpMethod.GET, withUser("carol"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("key", "some-flag").containsEntry("value", "null");

        awaitExported(TRACES, "LDClient.jsonValueVariation", "some-flag");
    }

    @Test
    void errorIsRecordedAndAnswered500() {
        ResponseEntity<String> response = get("/error", "dave");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("error: random error from the Spring Boot example\n");

        // Recorded as an exception event on the filter's request span.
        awaitExported(TRACES, "GET /error", "java.lang.IllegalStateException",
                "random error from the Spring Boot example", "exception.stacktrace");
        awaitExported(LOGS, "GET /error -> 500", "ERROR");
    }

    @Test
    void unknownRouteIs404() {
        ResponseEntity<String> response = get("/no-such-route", "erin");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        awaitExported(LOGS, "GET /no-such-route -> 404");
    }

    @Test
    void everyRequestEmitsLogAndMetrics() {
        assertThat(get("/", "frank").getStatusCode()).isEqualTo(HttpStatus.OK);

        awaitExported(LOGS, "GET / -> 200", "INFO", "http.route");
        // The periodic metric reader exports every 5 seconds.
        awaitExported(METRICS, "http.server.requests", "http.server.request.duration_ms", "http.route");
    }

    @Test
    void backendSamplingRuleDropsHealthChecks() {
        awaitTrue(() -> !SAMPLING_QUERIES.isEmpty(), "the plugin never fetched sampling config");
        assertThat(SAMPLING_QUERIES.get(0)).contains("organization_verbose_id");

        // The rule applies once the background fetch lands; retry until a
        // batch carries the kept span without the health check.
        awaitTrue(() -> {
            int before = TRACES.size();
            assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/flags/after-health", "gina").getStatusCode()).isEqualTo(HttpStatus.OK);
            LDObserve.flush();
            String batch = String.join("\n", TRACES.subList(before, TRACES.size()));
            return batch.contains("GET /flags/after-health") && !batch.contains("GET /actuator/health");
        }, "sampling rule did not drop GET /actuator/health spans: " + summarize(TRACES));
    }

    private ResponseEntity<String> get(String path, String userKey) {
        return http.exchange(path, HttpMethod.GET, withUser(userKey), String.class);
    }

    private static HttpEntity<Void> withUser(String userKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Key", userKey);
        return new HttpEntity<>(headers);
    }

    /** Waits until one exported payload contains every needle. */
    private static void awaitExported(List<String> payloads, String... needles) {
        awaitTrue(() -> {
            LDObserve.flush();
            for (String payload : new ArrayList<>(payloads)) {
                boolean all = true;
                for (String needle : needles) {
                    if (!payload.contains(needle)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    return true;
                }
            }
            return false;
        }, "no exported payload contains all of " + List.of(needles) + ":\n" + summarize(payloads));
    }

    private static void awaitTrue(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail(message);
    }

    private static String summarize(List<String> payloads) {
        StringBuilder text = new StringBuilder();
        for (String payload : new ArrayList<>(payloads)) {
            for (char c : payload.toCharArray()) {
                text.append(c >= 32 && c < 127 ? c : ' ');
            }
            text.append('\n');
        }
        return text.length() > 2000 ? text.substring(0, 2000) : text.toString();
    }

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/sampling", exchange -> {
                SAMPLING_QUERIES.add(body(exchange));
                respond(exchange, "application/json", SAMPLING_RESPONSE.getBytes(StandardCharsets.UTF_8));
            });
            server.createContext("/v1/traces", exchange -> {
                TRACES.add(body(exchange));
                respond(exchange, "application/x-protobuf", new byte[0]);
            });
            server.createContext("/v1/logs", exchange -> {
                LOGS.add(body(exchange));
                respond(exchange, "application/x-protobuf", new byte[0]);
            });
            server.createContext("/v1/metrics", exchange -> {
                METRICS.add(body(exchange));
                respond(exchange, "application/x-protobuf", new byte[0]);
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String body(HttpExchange exchange) throws IOException {
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
