package com.launchdarkly.observability;

import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.PluginMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityPluginTest {

    @Test
    void metadataReturnsCorrectName() {
        ObservabilityOptions options = new ObservabilityOptions.Builder().build();
        ObservabilityPlugin plugin = new ObservabilityPlugin(options);

        PluginMetadata metadata = plugin.getMetadata();
        assertEquals("launchdarkly-observability", metadata.getName());
    }

    @Test
    void getHooksReturnsTracingHook() {
        ObservabilityOptions options = new ObservabilityOptions.Builder().build();
        ObservabilityPlugin plugin = new ObservabilityPlugin(options);

        List<Hook> hooks = plugin.getHooks(null);
        assertNotNull(hooks);
        assertEquals(1, hooks.size());
        // The hook should be a TracingHook instance
        assertNotNull(hooks.get(0));
    }

    @Test
    void optionsBuilderDefaults() {
        ObservabilityOptions options = new ObservabilityOptions.Builder().build();
        assertEquals("", options.getServiceName());
        assertEquals("", options.getServiceVersion());
        assertEquals("", options.getEnvironment());
        assertFalse(options.isDebug());
        assertFalse(options.isManualStart());
        assertEquals("https://otel.observability.app.launchdarkly.com:4318", options.getOtlpEndpoint());
        assertEquals("https://pub.observability.app.launchdarkly.com", options.getBackendUrl());
    }

    @Test
    void optionsBuilderCustomValues() {
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .serviceName("test-service")
                .serviceVersion("2.0.0")
                .environment("staging")
                .otlpEndpoint("https://otel.example.test:4318")
                .backendUrl("https://backend.example.test")
                .debug(true)
                .manualStart(true)
                .build();

        assertEquals("test-service", options.getServiceName());
        assertEquals("2.0.0", options.getServiceVersion());
        assertEquals("staging", options.getEnvironment());
        assertEquals("https://otel.example.test:4318", options.getOtlpEndpoint());
        assertEquals("https://backend.example.test", options.getBackendUrl());
        assertTrue(options.isDebug());
        assertTrue(options.isManualStart());
    }
}
