package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
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
