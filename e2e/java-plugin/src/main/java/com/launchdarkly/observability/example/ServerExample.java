package com.launchdarkly.observability.example;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A long-running HTTP server that uses the LaunchDarkly Java Server SDK with
 * the observability plugin, the way a service would. Every request is traced,
 * evaluates a flag (which the plugin records as a span event), records a
 * metric and a log, and some routes record an error.
 *
 * <p>Environment:</p>
 * <ul>
 *   <li>{@code LAUNCHDARKLY_SDK_KEY}: the SDK key. When unset the client runs
 *       offline, so the server still works without LaunchDarkly access.</li>
 *   <li>{@code PORT}: listen port, default 8080.</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT}: OTLP/HTTP base URL. Defaults to
 *       the LaunchDarkly endpoint.</li>
 *   <li>{@code LD_OBSERVABILITY_BACKEND_URL}: sampling-config URL. Defaults to
 *       the LaunchDarkly backend.</li>
 *   <li>{@code HTTPS_PROXY} / {@code https_proxy}: {@code host:port} of an
 *       egress proxy for exports and the sampling request.</li>
 * </ul>
 */
public final class ServerExample {
    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> STATUS = AttributeKey.longKey("http.response.status_code");

    public static void main(String[] args) throws Exception {
        String sdkKey = env("LAUNCHDARKLY_SDK_KEY", "");
        int port = Integer.parseInt(env("PORT", "8080"));

        ObservabilityOptions.Builder options = new ObservabilityOptions.Builder()
                .serviceName("java-plugin-example")
                .serviceVersion(env("SERVICE_VERSION", "dev"))
                .environment("test")
                .resourceAttributes(Attributes.of(AttributeKey.stringKey("example.runtime"), System.getProperty("java.version")))
                .logsApiLevel(ObservabilityOptions.LogLevel.INFO);
        String otlp = env("OTEL_EXPORTER_OTLP_ENDPOINT", "");
        if (!otlp.isEmpty()) {
            options.otlpEndpoint(otlp);
        }
        String backend = env("LD_OBSERVABILITY_BACKEND_URL", "");
        if (!backend.isEmpty()) {
            options.backendUrl(backend);
        }
        String proxy = env("HTTPS_PROXY", env("https_proxy", ""));
        if (!proxy.isEmpty()) {
            String hostPort = proxy.replaceFirst("^[a-z]+://", "").replaceAll("/.*$", "");
            int colon = hostPort.lastIndexOf(':');
            options.proxyHostAndPort(hostPort.substring(0, colon), Integer.parseInt(hostPort.substring(colon + 1)));
        }

        LDConfig config = new LDConfig.Builder()
                .offline(sdkKey.isEmpty())
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options.build()))))
                .build();
        LDClient client = new LDClient(sdkKey.isEmpty() ? "offline" : sdkKey, config);
        System.out.println("LaunchDarkly client initialized=" + client.isInitialized()
                + (sdkKey.isEmpty() ? " (offline: LAUNCHDARKLY_SDK_KEY not set)" : ""));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", exchange -> handle(exchange, client, "/", ServerExample::index));
        server.createContext("/rolldice", exchange -> handle(exchange, client, "/rolldice", ServerExample::rollDice));
        server.createContext("/error", exchange -> handle(exchange, client, "/error", ServerExample::error));
        server.start();
        System.out.println("Listening on http://0.0.0.0:" + port + "  (routes: /, /rolldice, /error)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            try {
                client.close();
            } catch (IOException ignored) {
                // Nothing left to do on shutdown.
            }
            LDObserve.shutdown();
        }, "example-shutdown"));

        Thread.currentThread().join();
    }

    @FunctionalInterface
    private interface Route {
        String apply(LDClient client, LDContext context) throws Exception;
    }

    private static void handle(HttpExchange exchange, LDClient client, String route, Route body) throws IOException {
        long started = System.nanoTime();
        Span span = LDObserve.startSpan("HTTP " + exchange.getRequestMethod() + " " + route,
                Attributes.of(ROUTE, route));
        int status = 200;
        String response;
        try (Scope ignored = span.makeCurrent()) {
            LDContext context = LDContext.builder("user-" + ThreadLocalRandom.current().nextInt(1000)).build();
            response = body.apply(client, context);
        } catch (Exception e) {
            status = 500;
            response = "error: " + e.getMessage() + "\n";
            span.setStatus(StatusCode.ERROR, e.getMessage());
            LDObserve.recordError(e, Attributes.of(ROUTE, route));
        } finally {
            span.setAttribute(STATUS, (long) status);
            span.end();
        }
        double millis = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started) / 1000.0;
        LDObserve.recordHistogram("http.server.duration_ms", millis, Attributes.of(ROUTE, route));
        LDObserve.recordIncr("http.server.requests", Attributes.of(ROUTE, route, STATUS, (long) status));
        LDObserve.recordLog(exchange.getRequestMethod() + " " + route + " -> " + status,
                status >= 500 ? Severity.ERROR : Severity.INFO, Attributes.of(ROUTE, route));

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String index(LDClient client, LDContext context) {
        boolean enabled = client.boolVariation("example-greeting", context, false);
        return (enabled ? "Hello from the flagged path" : "Hello, World!") + "\n";
    }

    private static String rollDice(LDClient client, LDContext context) {
        int sides = (int) client.intVariation("dice-sides", context, 6);
        int roll = ThreadLocalRandom.current().nextInt(1, sides + 1);
        Span.current().setAttribute("dice.roll", roll);
        return roll + "\n";
    }

    private static String error(LDClient client, LDContext context) {
        boolean soft = client.boolVariation("soft-errors", context, false);
        if (soft) {
            return "soft error recorded\n";
        }
        throw new IllegalStateException("random error from the Java plugin example");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
