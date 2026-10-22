package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.EvaluationTracingHook;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.PluginMetadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;

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
        // The plugin's own hook, so evaluations export even when another SDK
        // owns GlobalOpenTelemetry.
        assertInstanceOf(EvaluationTracingHook.class, hooks.get(0));
    }

    @Test
    void optionsBuilderDefaults() {
        ObservabilityOptions options = new ObservabilityOptions.Builder().build();
        assertEquals("", options.getServiceName());
        assertEquals("", options.getServiceVersion());
        assertEquals("", options.getEnvironment());
        assertFalse(options.isDebug());
        assertFalse(options.isManualStart());
        assertTrue(options.isShutdownHook());
        assertEquals("https://otel.observability.app.launchdarkly.com:4318", options.getOtlpEndpoint());
        assertEquals("https://pub.observability.app.launchdarkly.com", options.getBackendUrl());
        assertTrue(options.isEnabled());
        assertEquals(Attributes.empty(), options.getResourceAttributes());
        assertTrue(options.getCustomHeaders().isEmpty());
        assertNull(options.getProxySelector());
        assertEquals(ObservabilityOptions.LogLevel.INFO, options.getLogsApiLevel());
    }

    @Test
    void disabledPluginRegistersNoHooks() {
        ObservabilityOptions options = new ObservabilityOptions.Builder().enabled(false).build();
        ObservabilityPlugin plugin = new ObservabilityPlugin(options);

        assertFalse(options.isEnabled());
        assertTrue(plugin.getHooks(null).isEmpty(), "a disabled plugin must not add a flag-evaluation hook");
    }

    @Test
    void customHeadersAccumulateAndAreCopied() {
        Map<String, String> initial = new java.util.HashMap<>();
        initial.put("Authorization", "Bearer one");
        ObservabilityOptions.Builder builder = new ObservabilityOptions.Builder()
                .customHeaders(initial)
                .customHeader("X-Tenant", "acme");
        initial.put("X-Leaked", "later");
        ObservabilityOptions options = builder.build();

        assertEquals(Map.of("Authorization", "Bearer one", "X-Tenant", "acme"), options.getCustomHeaders());
        assertThrows(UnsupportedOperationException.class, () -> options.getCustomHeaders().put("a", "b"));
    }

    @Test
    void proxyHostAndPortBuildsAnHttpProxySelector() {
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .proxyHostAndPort("proxy.example.test", 3128)
                .build();

        List<Proxy> proxies = options.getProxySelector().select(URI.create("https://otel.example.test/v1/traces"));
        assertEquals(1, proxies.size());
        assertEquals(Proxy.Type.HTTP, proxies.get(0).type());
        assertEquals("proxy.example.test:3128", proxies.get(0).address().toString().replace("/<unresolved>", ""));
    }

    @Test
    void resourceAttributesAndLogLevelAreKept() {
        Attributes attrs = Attributes.of(AttributeKey.stringKey("team"), "payments");
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .resourceAttributes(attrs)
                .logsApiLevel(ObservabilityOptions.LogLevel.WARN)
                .build();

        assertEquals(attrs, options.getResourceAttributes());
        assertEquals(ObservabilityOptions.LogLevel.WARN, options.getLogsApiLevel());
        assertEquals(13, ObservabilityOptions.LogLevel.WARN.getSeverityNumber());
        assertTrue(ObservabilityOptions.LogLevel.NONE.getSeverityNumber() > ObservabilityOptions.LogLevel.FATAL.getSeverityNumber());
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
