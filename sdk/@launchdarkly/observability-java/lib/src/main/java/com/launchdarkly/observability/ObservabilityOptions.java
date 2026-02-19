package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;

/**
 * Configuration options for the LaunchDarkly Observability plugin.
 * Use the {@link Builder} to construct an instance.
 */
public final class ObservabilityOptions {

    private final String serviceName;
    private final String serviceVersion;
    private final String environment;
    private final String otlpEndpoint;
    private final String backendUrl;
    private final boolean debug;
    private final boolean manualStart;

    private ObservabilityOptions(Builder builder) {
        this.serviceName = builder.serviceName;
        this.serviceVersion = builder.serviceVersion;
        this.environment = builder.environment;
        this.otlpEndpoint = builder.otlpEndpoint;
        this.backendUrl = builder.backendUrl;
        this.debug = builder.debug;
        this.manualStart = builder.manualStart;
    }

    public String getServiceName() { return serviceName; }
    public String getServiceVersion() { return serviceVersion; }
    public String getEnvironment() { return environment; }
    public String getOtlpEndpoint() { return otlpEndpoint; }
    public String getBackendUrl() { return backendUrl; }
    public boolean isDebug() { return debug; }
    public boolean isManualStart() { return manualStart; }

    /**
     * Builder for {@link ObservabilityOptions}.
     */
    public static class Builder {
        private String serviceName = "";
        private String serviceVersion = "";
        private String environment = "";
        private String otlpEndpoint = Constants.DEFAULT_OTLP_ENDPOINT;
        private String backendUrl = Constants.DEFAULT_BACKEND_URL;
        private boolean debug = false;
        private boolean manualStart = false;

        public Builder() {}

        /**
         * Sets the service name for resource attributes.
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Sets the service version for resource attributes.
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the deployment environment name for resource attributes.
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets a custom OTLP endpoint. Defaults to the LaunchDarkly OTLP endpoint.
         */
        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        /**
         * Sets a custom backend URL for fetching sampling configuration.
         */
        public Builder backendUrl(String backendUrl) {
            this.backendUrl = backendUrl;
            return this;
        }

        /**
         * Enables debug logging for the observability plugin.
         */
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * When set to true, the OTLP providers will not start automatically.
         * Call {@link LDObserve#start(String, ObservabilityOptions)} to start them manually.
         */
        public Builder manualStart(boolean manualStart) {
            this.manualStart = manualStart;
            return this;
        }

        public ObservabilityOptions build() {
            return new ObservabilityOptions(this);
        }
    }
}
