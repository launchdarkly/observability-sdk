package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the plugin starts, fails to start and coexists with other clients in
 * one process.
 */
class PluginStartupTest {

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    };
    private final Logger pluginLogger = Logger.getLogger("com.launchdarkly.observability");

    @BeforeEach
    void captureLogs() {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
        pluginLogger.addHandler(handler);
    }

    @AfterEach
    void reset() {
        pluginLogger.removeHandler(handler);
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void badOptionIsLoggedAndDoesNotStopTheClient() {
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .otlpEndpoint("otel.example.test:4318")
                .build();

        LDClient client = assertDoesNotThrow(() -> client("sdk-bad-endpoint", options));
        try {
            assertFalse(OtelManager.isInitialized());
            assertFalse(client.boolVariation("flag", LDContext.create("user"), false));
            assertDoesNotThrow(() -> LDObserve.recordLog("ignored", Severity.INFO, Attributes.empty()));
        } finally {
            assertDoesNotThrow(client::close);
        }

        LogRecord severe = records.stream()
                .filter(r -> r.getLevel() == Level.SEVERE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a SEVERE log explaining why the plugin is off"));
        assertTrue(severe.getMessage().contains("could not start"), severe.getMessage());
        assertTrue(severe.getMessage().contains("otel.example.test:4318"),
                "the log must carry the cause so the user can fix it: " + severe.getMessage());
        assertFalse(severe.getMessage().contains("needs 1.38"),
                "a configuration error is not a version problem: " + severe.getMessage());
    }

    @Test
    void openTelemetryViewFollowsThePluginLifecycle() throws Exception {
        OpenTelemetry otel = LDObserve.getOpenTelemetry();
        assertFalse(otel.getTracer("before").spanBuilder("s").startSpan().isRecording(),
                "before the plugin starts the view hands out no-ops");

        // Someone else owns the global, as with the Java agent.
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        LDClient client = client("sdk-view", new ObservabilityOptions.Builder()
                .otlpEndpoint("http://127.0.0.1:9")
                .build());
        try {
            assertSame(otel, LDObserve.getOpenTelemetry());
            Span span = otel.getTracer("app").spanBuilder("request").startSpan();
            assertTrue(span.isRecording(), "the view must reach the plugin's providers, not the global");
            span.end();
            assertFalse(otel instanceof AutoCloseable,
                    "frameworks infer close() as a destroy method; the view must not expose one");
        } finally {
            client.close();
            LDObserve.shutdown();
        }
        assertFalse(otel.getTracer("after").spanBuilder("s").startSpan().isRecording());
    }

    @Test
    void unreachableSamplingBackendLogsOneLine() {
        SamplingApiService service = new SamplingApiService(new GraphQLClient("http://127.0.0.1:9"));

        assertNull(service.getSamplingConfig("sdk-offline"));

        // Sampling threads from earlier tests may log here too; every
        // warning from the service must have the same one-line shape.
        List<LogRecord> warnings = records.stream()
                .filter(r -> r.getLevel() == Level.WARNING)
                .filter(r -> SamplingApiService.class.getName().equals(r.getLoggerName()))
                .collect(java.util.stream.Collectors.toList());
        assertFalse(warnings.isEmpty(), "a failed fetch must be reported");
        for (LogRecord warning : warnings) {
            assertNull(warning.getThrown(), "the stack trace belongs at FINE, not in every server's startup log");
            assertTrue(warning.getMessage().contains("unsampled"), warning.getMessage());
        }
    }

    @Test
    void startupSaysWhereTelemetryGoesAndWarnsWithoutAServiceName() throws Exception {
        try (LDClient client = client("sdk-unnamed", new ObservabilityOptions.Builder()
                .otlpEndpoint("http://127.0.0.1:9").backendUrl("http://127.0.0.1:9").build())) {
            assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.INFO
                    && r.getMessage().contains("exporting to http://127.0.0.1:9 as service 'unknown_service:java'")));
            assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.WARNING
                    && r.getMessage().contains("serviceName")));
        }
    }

    @Test
    void namedServiceStartsWithoutAWarning() throws Exception {
        try (LDClient client = client("sdk-named", new ObservabilityOptions.Builder()
                .serviceName("checkout").otlpEndpoint("http://127.0.0.1:9").backendUrl("http://127.0.0.1:9").build())) {
            assertTrue(records.stream().anyMatch(r -> r.getMessage().contains("as service 'checkout'")));
            assertFalse(records.stream().anyMatch(r -> r.getLevel() == Level.WARNING
                    && r.getMessage().contains("serviceName")));
        }
    }

    @Test
    void sdkVersionReportsTheClasspathVersion() {
        assertTrue(OtelManager.sdkVersion().matches("\\d+\\.\\d+\\.\\d+.*"), OtelManager.sdkVersion());
    }

    private static LDClient client(String sdkKey, ObservabilityOptions options) {
        LDConfig config = new LDConfig.Builder()
                .offline(true)
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();
        return new LDClient(sdkKey, config);
    }
}
