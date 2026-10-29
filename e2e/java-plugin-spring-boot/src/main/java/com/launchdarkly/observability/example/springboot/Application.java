package com.launchdarkly.observability.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A Spring Boot web backend that uses the LaunchDarkly Java Server SDK with
 * the observability plugin the way a service does: a flag-aware client bean,
 * a span, metrics and a log per request, and recorded errors.
 *
 * <p>Environment:</p>
 * <ul>
 *   <li>{@code LAUNCHDARKLY_SDK_KEY}: the SDK key. When unset the client runs
 *       offline so the backend still works without LaunchDarkly access.</li>
 *   <li>{@code PORT}: listen port, default 8080.</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT}: OTLP/HTTP base URL. Defaults to
 *       the LaunchDarkly endpoint.</li>
 *   <li>{@code LD_OBSERVABILITY_BACKEND_URL}: sampling-config URL. Defaults to
 *       the LaunchDarkly backend.</li>
 *   <li>{@code HTTPS_PROXY}: {@code host:port} of an egress proxy for exports
 *       and the sampling request.</li>
 * </ul>
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        GlobalAttributesRepro.applyGlobalAttributes();
        SpringApplication.run(Application.class, args);
    }
}
