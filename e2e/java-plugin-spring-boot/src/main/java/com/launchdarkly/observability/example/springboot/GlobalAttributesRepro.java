package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

/**
 * Reproduces "global attributes (e.g. UTM params) on all telemetry": the UTM
 * attributes are set once at startup, before the plugin exists, and every
 * request's span, log and metrics must carry them. The {@code [#1]} lines on
 * stdout say what to look for on the matching row in LaunchDarkly.
 */
final class GlobalAttributesRepro {
    static final AttributeKey<String> UTM_CAMPAIGN = AttributeKey.stringKey("utm_campaign");
    static final AttributeKey<String> UTM_SOURCE = AttributeKey.stringKey("utm_source");

    static final Attributes GLOBAL_ATTRIBUTES = Attributes.of(
            UTM_CAMPAIGN, "spring_promo",
            UTM_SOURCE, "email");

    /** Passed on the request log line only; it must win over the global {@code utm_source}. */
    static final Attributes PER_CALL_ATTRIBUTES = Attributes.of(UTM_SOURCE, "per_call");

    private GlobalAttributesRepro() {}

    /** Call before the Spring context (and so the LDClient and plugin) starts. */
    static void applyGlobalAttributes() {
        LDObserve.setAttributes(GLOBAL_ATTRIBUTES);
        System.out.println("[#1] global attributes set before the LaunchDarkly client starts: " + GLOBAL_ATTRIBUTES);
    }

    static void expectGlobal(String row) {
        System.out.println("[#1] expect utm_campaign=spring_promo on this row (" + row + ")");
    }

    static void expectPerCallWins(String row) {
        System.out.println("[#1] expect utm_source=per_call (per-call wins over global) (" + row + ")");
    }
}
