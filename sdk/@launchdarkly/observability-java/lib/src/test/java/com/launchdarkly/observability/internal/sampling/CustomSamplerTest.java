package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomSamplerTest {

    // Deterministic sampler that always samples (ratio >= 1)
    private static final CustomSampler ALWAYS_SAMPLE = new CustomSampler(ratio -> true);
    // Deterministic sampler that never samples (used when ratio config matches)
    private static final CustomSampler NEVER_SAMPLE = new CustomSampler(ratio -> false);

    @Test
    void noConfigAlwaysSamples() {
        CustomSampler sampler = new CustomSampler();
        SpanData span = createTestSpan("test-span", Attributes.empty());
        CustomSampler.SamplingResult result = sampler.sampleSpan(span);
        assertTrue(result.isSampled());
    }

    @Test
    void spanNameValueMatchFilters() {
        SamplingConfig config = new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofValue("health-check"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        1
                )),
                Collections.emptyList()
        );

        NEVER_SAMPLE.setConfig(config);

        // Matching span should be filtered (NEVER_SAMPLE says no)
        SpanData healthSpan = createTestSpan("health-check", Attributes.empty());
        CustomSampler.SamplingResult result = NEVER_SAMPLE.sampleSpan(healthSpan);
        assertFalse(result.isSampled());

        // Non-matching span should pass through (no config match = always sample)
        SpanData otherSpan = createTestSpan("user-request", Attributes.empty());
        result = NEVER_SAMPLE.sampleSpan(otherSpan);
        assertTrue(result.isSampled());
    }

    @Test
    void spanNameRegexMatchFilters() {
        SamplingConfig config = new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofRegex("health.*"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        1
                )),
                Collections.emptyList()
        );

        NEVER_SAMPLE.setConfig(config);

        SpanData healthSpan = createTestSpan("health-check", Attributes.empty());
        assertFalse(NEVER_SAMPLE.sampleSpan(healthSpan).isSampled());

        SpanData otherSpan = createTestSpan("user-request", Attributes.empty());
        assertTrue(NEVER_SAMPLE.sampleSpan(otherSpan).isSampled());
    }

    @Test
    void spanAttributeMatchFilters() {
        SamplingConfig config = new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        null,
                        List.of(new SamplingConfig.AttributeMatchConfig(
                                SamplingConfig.MatchConfig.ofValue("http.route"),
                                SamplingConfig.MatchConfig.ofValue("/health")
                        )),
                        Collections.emptyList(),
                        1
                )),
                Collections.emptyList()
        );

        NEVER_SAMPLE.setConfig(config);

        Attributes matchAttrs = Attributes.of(AttributeKey.stringKey("http.route"), "/health");
        assertFalse(NEVER_SAMPLE.sampleSpan(createTestSpan("request", matchAttrs)).isSampled());

        Attributes noMatchAttrs = Attributes.of(AttributeKey.stringKey("http.route"), "/api/users");
        assertTrue(NEVER_SAMPLE.sampleSpan(createTestSpan("request", noMatchAttrs)).isSampled());
    }

    @Test
    void matchConfigAlwaysSampleReturnsAttributes() {
        SamplingConfig config = new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofValue("my-span"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        5
                )),
                Collections.emptyList()
        );

        ALWAYS_SAMPLE.setConfig(config);
        SpanData span = createTestSpan("my-span", Attributes.empty());
        CustomSampler.SamplingResult result = ALWAYS_SAMPLE.sampleSpan(span);
        assertTrue(result.isSampled());
        assertNotNull(result.getAttributes());
        assertEquals(5L, result.getAttributes().get(AttributeKey.longKey("highlight.sampling.ratio")));
    }

    @Test
    void matchesValueHelperExactMatch() {
        assertTrue(CustomSampler.matchesValue(
                SamplingConfig.MatchConfig.ofValue("hello"), "hello"));
        assertFalse(CustomSampler.matchesValue(
                SamplingConfig.MatchConfig.ofValue("hello"), "world"));
    }

    @Test
    void matchesValueHelperRegex() {
        assertTrue(CustomSampler.matchesValue(
                SamplingConfig.MatchConfig.ofRegex("hel.*"), "hello"));
        assertFalse(CustomSampler.matchesValue(
                SamplingConfig.MatchConfig.ofRegex("^world$"), "hello"));
    }

    @Test
    void matchesValueNullConfigAlwaysMatches() {
        assertTrue(CustomSampler.matchesValue(null, "anything"));
    }

    @Test
    void matchesValueNullActualNeverMatches() {
        assertFalse(CustomSampler.matchesValue(
                SamplingConfig.MatchConfig.ofValue("test"), null));
    }

    @Test
    void defaultSamplerBehavior() {
        assertFalse(CustomSampler.defaultSampler(0));
        assertTrue(CustomSampler.defaultSampler(1));
        // ratio of 2 should sample ~50% (not deterministic, but at least doesn't throw)
        CustomSampler.defaultSampler(2);
    }

    // --- Helper to create a minimal SpanData using SDK testing utilities ---

    private static SpanData createTestSpan(String name, Attributes attributes) {
        return TestSpanData.builder()
                .setName(name)
                .setKind(SpanKind.INTERNAL)
                .setSpanContext(SpanContext.create(
                        "00000000000000000000000000000001",
                        "0000000000000001",
                        TraceFlags.getSampled(),
                        TraceState.getDefault()
                ))
                .setStatus(StatusData.unset())
                .setHasEnded(true)
                .setStartEpochNanos(0)
                .setEndEpochNanos(1)
                .setAttributes(attributes)
                .build();
    }
}
