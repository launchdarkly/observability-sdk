package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration options for the LaunchDarkly Observability plugin.
 * Use the {@link Builder} to construct an instance.
 */
public final class ObservabilityOptions {

    private final boolean enabled;
    private final String serviceName;
    private final String serviceVersion;
    private final String environment;
    private final String otlpEndpoint;
    private final String backendUrl;
    private final Attributes resourceAttributes;
    private final Map<String, String> customHeaders;
    private final ProxySelector proxySelector;
    private final LogLevel logsApiLevel;
    private final boolean debug;
    private final boolean manualStart;

    private ObservabilityOptions(Builder builder) {
        this.enabled = builder.enabled;
        this.serviceName = builder.serviceName;
        this.serviceVersion = builder.serviceVersion;
        this.environment = builder.environment;
        this.otlpEndpoint = builder.otlpEndpoint;
        this.backendUrl = builder.backendUrl;
        this.resourceAttributes = builder.resourceAttributes;
        this.customHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customHeaders));
        this.proxySelector = builder.proxySelector;
        this.logsApiLevel = builder.logsApiLevel;
        this.debug = builder.debug;
        this.manualStart = builder.manualStart;
    }

    /** Whether the plugin exports telemetry at all. See {@link Builder#enabled(boolean)}. */
    public boolean isEnabled() { return enabled; }
    public String getServiceName() { return serviceName; }
    public String getServiceVersion() { return serviceVersion; }
    public String getEnvironment() { return environment; }
    public String getOtlpEndpoint() { return otlpEndpoint; }
    public String getBackendUrl() { return backendUrl; }
    /** Additional resource attributes attached to every exported signal. Never null. */
    public Attributes getResourceAttributes() { return resourceAttributes; }
    /** Headers sent with every OTLP export and sampling-configuration request. Never null. */
    public Map<String, String> getCustomHeaders() { return customHeaders; }
    /** The proxy selector for outbound HTTP, or null to use the JVM default. */
    public ProxySelector getProxySelector() { return proxySelector; }
    /** The minimum severity a log record needs to be exported. */
    public LogLevel getLogsApiLevel() { return logsApiLevel; }
    public boolean isDebug() { return debug; }
    public boolean isManualStart() { return manualStart; }

    /**
     * Minimum severity for exported log records. Mirrors the OpenTelemetry
     * log severity numbers; a record is exported when its severity number is
     * at or above the configured level. {@link #NONE} disables log export.
     *
     * <p>A record whose severity is unset
     * ({@link Severity#UNDEFINED_SEVERITY_NUMBER}) is treated as
     * {@link #TRACE}, so it is only exported at the {@code TRACE} level.</p>
     *
     * @see <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/#severity-fields">OpenTelemetry Log Data Model - Severity</a>
     */
    public enum LogLevel {
        TRACE(Severity.TRACE.getSeverityNumber()),
        DEBUG(Severity.DEBUG.getSeverityNumber()),
        INFO(Severity.INFO.getSeverityNumber()),
        WARN(Severity.WARN.getSeverityNumber()),
        ERROR(Severity.ERROR.getSeverityNumber()),
        FATAL(Severity.FATAL.getSeverityNumber()),
        /** Export no log records. */
        NONE(Integer.MAX_VALUE);

        private final int severityNumber;

        LogLevel(int severityNumber) {
            this.severityNumber = severityNumber;
        }

        /** The OpenTelemetry severity number this level starts at. */
        public int getSeverityNumber() {
            return severityNumber;
        }
    }

    /**
     * Builder for {@link ObservabilityOptions}.
     */
    public static class Builder {
        private boolean enabled = true;
        private String serviceName = "";
        private String serviceVersion = "";
        private String environment = "";
        private String otlpEndpoint = Constants.DEFAULT_OTLP_ENDPOINT;
        private String backendUrl = Constants.DEFAULT_BACKEND_URL;
        private Attributes resourceAttributes = Attributes.empty();
        private Map<String, String> customHeaders = new LinkedHashMap<>();
        private ProxySelector proxySelector = null;
        private LogLevel logsApiLevel = LogLevel.INFO;
        private boolean debug = false;
        private boolean manualStart = false;

        public Builder() {}

        /**
         * Enables or disables the plugin. When {@code false} the plugin is
         * registered but starts no OpenTelemetry providers, registers no
         * flag-evaluation hook, fetches no sampling configuration, and every
         * {@link LDObserve} call is a no-op. Defaults to {@code true}.
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

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
         * Adds resource attributes to every exported span, log record and
         * metric. These are merged over the plugin's own resource, so they
         * can override {@code service.name} and friends.
         */
        public Builder resourceAttributes(Attributes resourceAttributes) {
            this.resourceAttributes = Objects.requireNonNull(resourceAttributes, "resourceAttributes");
            return this;
        }

        /**
         * Sets headers that are sent with every OTLP export and with the
         * sampling-configuration request. Replaces any previously set headers.
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = new LinkedHashMap<>(Objects.requireNonNull(customHeaders, "customHeaders"));
            return this;
        }

        /**
         * Adds one header that is sent with every OTLP export and with the
         * sampling-configuration request.
         */
        public Builder customHeader(String name, String value) {
            this.customHeaders.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Routes every outbound request (OTLP exports and the
         * sampling-configuration request) through the given proxy selector.
         *
         * <p>When unset, the JVM default selector is used, which honours the
         * standard {@code http.proxyHost}, {@code https.proxyHost} and
         * {@code http.nonProxyHosts} system properties.</p>
         */
        public Builder proxySelector(ProxySelector proxySelector) {
            this.proxySelector = proxySelector;
            return this;
        }

        /**
         * Routes every outbound request through an HTTP proxy at the given
         * host and port.
         */
        public Builder proxyHostAndPort(String host, int port) {
            Objects.requireNonNull(host, "host");
            return proxySelector(new FixedProxySelector(new InetSocketAddress(host, port)));
        }

        /**
         * Sets the minimum severity for exported log records. Defaults to
         * {@link LogLevel#INFO}. Use {@link LogLevel#NONE} to disable log export.
         */
        public Builder logsApiLevel(LogLevel logsApiLevel) {
            this.logsApiLevel = Objects.requireNonNull(logsApiLevel, "logsApiLevel");
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

    /**
     * A {@link ProxySelector} that sends everything through one HTTP proxy.
     */
    static final class FixedProxySelector extends ProxySelector {
        private final List<Proxy> proxies;

        FixedProxySelector(InetSocketAddress address) {
            this.proxies = Collections.singletonList(new Proxy(Proxy.Type.HTTP, address));
        }

        @Override
        public List<Proxy> select(URI uri) {
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
            // Nothing to fall back to; the exporter retries on its own schedule.
        }
    }
}
