package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.EvaluationTracingHook;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.PluginMetadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertTrue(options.getSpanProcessors().isEmpty());
        assertTrue(options.getLogProcessors().isEmpty());
    }

    @Test
    void processorsAccumulateInOrderAndAreCopied() {
        SpanProcessor spanA = SimpleSpanProcessor.create(InMemorySpanExporter.create());
        SpanProcessor spanB = SimpleSpanProcessor.create(InMemorySpanExporter.create());
        LogRecordProcessor logA = SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create());
        LogRecordProcessor logB = SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create());
        List<SpanProcessor> spanList = new ArrayList<>(List.of(spanA));
        ObservabilityOptions.Builder builder = new ObservabilityOptions.Builder()
                .spanProcessors(spanList)
                .spanProcessor(spanB)
                .logProcessors(List.of(logA))
                .logProcessor(logB);
        spanList.add(SimpleSpanProcessor.create(InMemorySpanExporter.create()));
        ObservabilityOptions options = builder.build();

        assertEquals(List.of(spanA, spanB), options.getSpanProcessors());
        assertEquals(List.of(logA, logB), options.getLogProcessors());
        assertThrows(UnsupportedOperationException.class, () -> options.getSpanProcessors().add(spanA));
        assertThrows(UnsupportedOperationException.class, () -> options.getLogProcessors().add(logA));

        ObservabilityOptions replaced = builder.spanProcessors(List.of(spanB)).logProcessors(List.of()).build();
        assertEquals(List.of(spanB), replaced.getSpanProcessors());
        assertTrue(replaced.getLogProcessors().isEmpty());
        assertEquals(List.of(spanA, spanB), options.getSpanProcessors(), "built options must not change");
    }

    @Test
    void processorsRejectNull() {
        ObservabilityOptions.Builder builder = new ObservabilityOptions.Builder();
        assertThrows(NullPointerException.class, () -> builder.spanProcessors(null));
        assertThrows(NullPointerException.class, () -> builder.spanProcessors(Arrays.asList((SpanProcessor) null)));
        assertThrows(NullPointerException.class, () -> builder.spanProcessor(null));
        assertThrows(NullPointerException.class, () -> builder.logProcessors(null));
        assertThrows(NullPointerException.class, () -> builder.logProcessors(Arrays.asList((LogRecordProcessor) null)));
        assertThrows(NullPointerException.class, () -> builder.logProcessor(null));
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
