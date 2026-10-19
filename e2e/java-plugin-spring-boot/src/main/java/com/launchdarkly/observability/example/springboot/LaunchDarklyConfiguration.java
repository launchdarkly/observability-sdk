package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Builds the one {@link LDClient} for the application with the observability
 * plugin installed, and shuts both down with the context.
 */
@Configuration
public class LaunchDarklyConfiguration {
    private static final Logger log = LoggerFactory.getLogger(LaunchDarklyConfiguration.class);

    @Bean(destroyMethod = "close")
    public LDClient ldClient(
            @Value("${LAUNCHDARKLY_SDK_KEY:}") String sdkKey,
            @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:}") String otlpEndpoint,
            @Value("${LD_OBSERVABILITY_BACKEND_URL:}") String backendUrl,
            @Value("${HTTPS_PROXY:${https_proxy:}}") String httpsProxy,
            @Value("${SERVICE_VERSION:dev}") String serviceVersion) {
        ObservabilityOptions.Builder options = new ObservabilityOptions.Builder()
                .serviceName("java-plugin-spring-boot-example")
                .serviceVersion(serviceVersion)
                .environment("test")
                .resourceAttributes(Attributes.of(
                        AttributeKey.stringKey("example.framework"), "spring-boot",
                        AttributeKey.stringKey("example.runtime"), System.getProperty("java.version")))
                .logsApiLevel(ObservabilityOptions.LogLevel.INFO);
        if (!otlpEndpoint.isEmpty()) {
            options.otlpEndpoint(otlpEndpoint);
        }
        if (!backendUrl.isEmpty()) {
            options.backendUrl(backendUrl);
        }
        if (!httpsProxy.isEmpty()) {
            String hostPort = httpsProxy.replaceFirst("^[a-z]+://", "").replaceAll("/.*$", "");
            int colon = hostPort.lastIndexOf(':');
            options.proxyHostAndPort(hostPort.substring(0, colon), Integer.parseInt(hostPort.substring(colon + 1)));
        }

        boolean offline = sdkKey.isEmpty();
        LDConfig config = new LDConfig.Builder()
                .offline(offline)
                .startWait(Duration.ofSeconds(5))
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options.build()))))
                .build();
        LDClient client = new LDClient(offline ? "offline" : sdkKey, config);
        log.info("LaunchDarkly client initialized={}{}", client.isInitialized(),
                offline ? " (offline: LAUNCHDARKLY_SDK_KEY not set)" : "");
        return client;
    }

    /** Flushes and stops the OpenTelemetry providers after the client bean closes. */
    @Bean
    public ObservabilityShutdown observabilityShutdown() {
        return new ObservabilityShutdown();
    }

    static final class ObservabilityShutdown implements AutoCloseable {
        @Override
        public void close() {
            LDObserve.shutdown();
        }
    }
}
