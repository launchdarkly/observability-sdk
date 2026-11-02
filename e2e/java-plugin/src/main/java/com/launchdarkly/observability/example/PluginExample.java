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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Runs the Java observability plugin against a local collector and exits
 * non-zero when a required signal is missing.
 *
 * <p>The first argument picks who owns {@code GlobalOpenTelemetry}:</p>
 * <ul>
 *   <li>{@code plugin} (default): nothing else claims it, so the plugin
 *       registers its SDK as the global.</li>
 *   <li>{@code claimed-global}: {@code GlobalOpenTelemetry.get()} runs before
 *       the client is built, as a library would, locking the global onto a
 *       no-op.</li>
 *   <li>{@code javaagent}: the OpenTelemetry Java agent owns the global and
 *       the evaluation runs inside an agent span.</li>
 * </ul>
 * <p>In every mode the flag evaluation must reach the plugin's exporter.</p>
 */
public final class PluginExample {
    private static final String SAMPLING_RESPONSE =
            "{\"data\":{\"sampling\":{\"spans\":["
                    + "{\"name\":{\"matchValue\":\"health-check\"},\"attributes\":[],\"events\":[],\"samplingRatio\":0},"
                    + "{\"name\":{\"matchValue\":\"kept-checkout\"},\"attributes\":[],\"events\":[],\"samplingRatio\":1}"
                    + "],\"logs\":[]}}}";

    private final List<String> traces = new CopyOnWriteArrayList<>();
    private final List<String> samplingQueries = new CopyOnWriteArrayList<>();
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "plugin";
        int status = new PluginExample().run(mode);
        if (status != 0) {
            System.exit(status);
        }
    }

    private int run(String mode) throws Exception {
        boolean agentAttached = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.startsWith("-javaagent:"));
        switch (mode) {
            case "plugin":
            case "claimed-global":
                if (agentAttached) {
                    failures.add("mode " + mode + " must run without -javaagent");
                }
                break;
            case "javaagent":
                if (!agentAttached) {
                    failures.add("mode javaagent needs -javaagent on the command line");
                }
                break;
            default:
                System.err.println("unknown mode " + mode + "; expected plugin, claimed-global or javaagent");
                return 2;
        }
        if (mode.equals("claimed-global")) {
            GlobalOpenTelemetry.get();
        }

        LDObserve.startSpan("before-init", Attributes.empty()).end();
        LDObserve.recordLog("before init", Severity.INFO, Attributes.empty());

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/sampling", exchange -> {
            samplingQueries.add(bodyAsString(exchange));
            respond(exchange, SAMPLING_RESPONSE.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/traces", exchange -> {
            traces.add(bodyAsString(exchange));
            respond(exchange, new byte[0]);
        });
        server.createContext("/v1/logs", exchange -> {
            bodyAsString(exchange);
            respond(exchange, new byte[0]);
        });
        server.createContext("/v1/metrics", exchange -> {
            bodyAsString(exchange);
            respond(exchange, new byte[0]);
        });
        server.start();

        String base = "http".concat("://") + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .serviceName("orders-service")
                .serviceVersion("1.2.3")
                .environment("test")
                .otlpEndpoint(base)
                .backendUrl(base + "/sampling")
                .build();
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();

        try (LDClient client = new LDClient("sdk-test", config)) {
            if (!waitForSamplingQuery()) {
                failures.add("plugin did not fetch sampling config");
            }

            String global = globalOwner();
            String expectedGlobal = mode.equals("plugin") ? "plugin" : mode.equals("javaagent") ? "agent" : "noop";
            if (!global.equals(expectedGlobal)) {
                failures.add("GlobalOpenTelemetry is owned by " + global + ", expected " + expectedGlobal);
            }

            // Evaluate inside a span from the global, as instrumented request
            // handling would. Under the agent that is an agent span.
            Span request = GlobalOpenTelemetry.getTracer("example").spanBuilder("GET /checkout").startSpan();
            try (Scope ignored = request.makeCurrent()) {
                client.boolVariation("checkout-enabled", LDContext.create("user-key-test"), false);
            } finally {
                request.end();
            }
            LDObserve.flush();
            boolean flagArrived = contains(traces, 0, "checkout-enabled");

            boolean kept = false;
            boolean dropped = false;
            boolean ratioArrived = false;
            for (int attempt = 0; attempt < 50 && !(kept && dropped); attempt++) {
                int before = traces.size();
                LDObserve.startSpan("health-check", Attributes.empty()).end();
                LDObserve.startSpan("kept-checkout", Attributes.empty()).end();
                LDObserve.flush();
                boolean batchHasKept = contains(traces, before, "kept-checkout");
                boolean batchHasHealth = contains(traces, before, "health-check");
                if (batchHasKept && !batchHasHealth) {
                    kept = true;
                    dropped = true;
                    ratioArrived = contains(traces, before, "launchdarkly.sampling.ratio");
                }
                if (!kept) {
                    Thread.sleep(100);
                }
            }

            if (!flagArrived) {
                failures.add("flag evaluation span checkout-enabled did not arrive with the global owned by " + global
                        + "; the tracing hook is not on the plugin exporters");
            }
            if (!kept) {
                failures.add("kept span kept-checkout did not arrive");
            }
            if (!dropped) {
                failures.add("sampling rule did not drop health-check");
            }
            if (kept && !ratioArrived) {
                failures.add("kept span kept-checkout arrived without launchdarkly.sampling.ratio");
            }
            if (contains(traces, 0, "before-init")) {
                failures.add("span before-init was exported; the pre-init call was not a local no-op");
            }

            System.out.println("RESULT"
                    + " mode=" + mode
                    + " flag-evaluation=" + (flagArrived ? "checkout-enabled" : "missing")
                    + " kept=" + (kept ? "kept-checkout" : "missing")
                    + " dropped=" + (dropped ? "health-check" : "still-exported")
                    + " sampling-ratio=" + (ratioArrived ? "present" : "missing")
                    + " global=" + global);
        } finally {
            LDObserve.shutdown();
            server.stop(0);
        }

        if (!failures.isEmpty()) {
            for (String failure : failures) {
                System.err.println("FAIL " + failure);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Reports who owns GlobalOpenTelemetry: "noop" when its spans are
     * invalid, "agent" when the Java agent bridges it, otherwise "plugin".
     */
    private static String globalOwner() {
        Span probe = GlobalOpenTelemetry.getTracer("example").spanBuilder("global-probe").startSpan();
        boolean recording = probe.getSpanContext().isValid();
        probe.end();
        if (!recording) {
            return "noop";
        }
        return GlobalOpenTelemetry.get().getClass().getName().contains("javaagent") ? "agent" : "plugin";
    }

    private boolean waitForSamplingQuery() throws InterruptedException {
        for (int i = 0; i < 50 && samplingQueries.isEmpty(); i++) {
            Thread.sleep(100);
        }
        return !samplingQueries.isEmpty();
    }

    private static boolean contains(List<String> payloads, int from, String needle) {
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

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
