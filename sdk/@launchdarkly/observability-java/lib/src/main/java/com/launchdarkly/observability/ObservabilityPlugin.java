package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.EvaluationTracingHook;
import com.launchdarkly.observability.internal.GraphQLClient;
import com.launchdarkly.observability.internal.OtelManager;
import com.launchdarkly.observability.internal.SamplingApiService;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.Plugin;
import com.launchdarkly.sdk.server.integrations.PluginMetadata;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LaunchDarkly Observability plugin that integrates OpenTelemetry tracing,
 * logging, and metrics with the LaunchDarkly Java Server SDK.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * LDConfig config = new LDConfig.Builder()
 *     .plugins(Components.plugins().setPlugins(List.of(
 *         new ObservabilityPlugin(
 *             new ObservabilityOptions.Builder()
 *                 .serviceName("my-service")
 *                 .serviceVersion("1.0.0")
 *                 .environment("production")
 *                 .build()
 *         )
 *     )))
 *     .build();
 * LDClient client = new LDClient("YOUR_SDK_KEY", config);
 * }</pre>
 *
 * <p>Outbound HTTP (OTLP exports and the sampling-configuration request) goes
 * through {@code java.net.http.HttpClient}. It honours the JVM proxy system
 * properties by default; use
 * {@link ObservabilityOptions.Builder#proxySelector(java.net.ProxySelector)} or
 * {@link ObservabilityOptions.Builder#proxyHostAndPort(String, int)} to set
 * one explicitly, and
 * {@link ObservabilityOptions.Builder#customHeaders(java.util.Map)} to add
 * headers to every request.</p>
 */
public class ObservabilityPlugin extends Plugin {

    private static final Logger log = Logger.getLogger(ObservabilityPlugin.class.getName());
    private static final String PLUGIN_NAME = "launchdarkly-observability";

    private final ObservabilityOptions options;

    /**
     * Creates a new observability plugin with the given options.
     *
     * @param options configuration options for the plugin
     */
    public ObservabilityPlugin(ObservabilityOptions options) {
        this.options = options;
    }

    @Override
    public PluginMetadata getMetadata() {
        return new PluginMetadata() {
            @Override
            public String getName() {
                return PLUGIN_NAME;
            }
        };
    }

    @Override
    public void register(LDClient client, EnvironmentMetadata metadata) {
        if (!options.isEnabled()) {
            if (options.isDebug()) {
                log.info("Observability plugin is disabled; no telemetry will be exported");
            }
            return;
        }

        String sdkKey = metadata.getSdkKey();

        if (!options.isManualStart()) {
            OtelManager.initialize(sdkKey, options);
        }

        // Fetch sampling config in background
        Thread samplingThread = new Thread(() -> {
            try {
                GraphQLClient graphQLClient = new GraphQLClient(
                        options.getBackendUrl(), options.getCustomHeaders(), options.getProxySelector());
                SamplingApiService samplingService = new SamplingApiService(graphQLClient);
                SamplingConfig config = samplingService.getSamplingConfig(sdkKey);
                if (config != null) {
                    OtelManager.setSamplingConfig(config);
                    if (options.isDebug()) {
                        log.info("Loaded sampling config: " + config);
                    }
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to fetch sampling config", e);
            }
        }, "ld-observability-sampling");
        samplingThread.setDaemon(true);
        samplingThread.start();
    }

    @Override
    public List<Hook> getHooks(EnvironmentMetadata metadata) {
        if (!options.isEnabled()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new EvaluationTracingHook(true, true));
    }
}
