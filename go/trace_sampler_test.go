package ldobserve

import (
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"math/rand"
	"testing"

	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

func TestTraceSampler_ShouldSample_WithSampledParent(t *testing.T) {
	// Create a sampler with some rates
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context with sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: trace.FlagsSampled,
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test sampling parameters
	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32},
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// Should always sample when parent is sampled
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent(t *testing.T) {
	// Create a sampler with specific rates
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with trace ID that should be sampled (lower than threshold)
	// For 0.5 rate, threshold is 0.5 * (1 << 63) = 0x4000000000000000
	// We'll use a trace ID with upper 8 bytes that when shifted right by 1 gives a value < threshold
	traceID := trace.TraceID{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x20} // Small value

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// Should sample based on trace ID ratio
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_AboveThreshold(t *testing.T) {
	// Create a sampler with specific rates
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with trace ID that should NOT be sampled (above threshold)
	// For 0.5 rate, threshold is 0.5 * (1 << 63) = 0x4000000000000000
	// The condition is: if x < bound then sample, so we need x >= bound to NOT sample
	// We need (x >> 1) >= threshold, so x >= threshold * 2
	threshold := uint64(0.5 * (1 << 63))
	x := threshold*2 + 1 // This ensures (x >> 1) = threshold + 0.5 >= threshold

	// Create trace ID with the calculated value in the upper 8 bytes
	traceIDBytes := make([]byte, 16)
	binary.BigEndian.PutUint64(traceIDBytes[8:16], x)
	traceID := trace.TraceID{}
	copy(traceID[:], traceIDBytes)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// Should NOT sample based on trace ID ratio
	if result.Decision != sdktrace.Drop {
		t.Errorf("Expected decision %v, got %v", sdktrace.Drop, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_NoParentContext(t *testing.T) {
	// Create a sampler with specific rates
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Test with no parent context
	params := sdktrace.SamplingParameters{
		ParentContext: context.Background(),
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// Should sample based on trace ID ratio since no parent context
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_UnspecifiedKind(t *testing.T) {
	// Create a sampler with specific rates but no Unspecified kind
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with Unspecified kind
	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindUnspecified,
	}

	result := sampler.ShouldSample(params)

	// Should sample since no bounds specified for Unspecified kind
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_UnspecifiedKindWithDefault(t *testing.T) {
	// Create a sampler with specific rates including Unspecified kind
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer:      0.5,
		trace.SpanKindUnspecified: 0.25,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with Unspecified kind
	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindUnspecified,
	}

	result := sampler.ShouldSample(params)

	// Should sample based on Unspecified kind rate
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_UnknownKind(t *testing.T) {
	// Create a sampler with specific rates but no Client kind
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with Client kind (not in rates)
	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindClient,
	}

	result := sampler.ShouldSample(params)

	// Should sample since no bounds specified for Client kind
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_UnknownKindWithUnspecifiedFallback(t *testing.T) {
	// Create a sampler with specific rates including Unspecified kind as fallback
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer:      0.5,
		trace.SpanKindUnspecified: 0.25, // This should be used as fallback for unknown kinds
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with Client kind (not in rates, but should fall back to Unspecified rate)
	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindClient,
	}

	result := sampler.ShouldSample(params)

	// Should sample based on Unspecified kind rate (0.25) since Client kind has no rate configured
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_WithUnsampledParent_UnknownKindWithUnspecifiedFallback_AboveThreshold(t *testing.T) {
	// Create a sampler with specific rates including Unspecified kind as fallback
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer:      0.5,
		trace.SpanKindUnspecified: 0.25, // This should be used as fallback for unknown kinds
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with Client kind (not in rates, but should fall back to Unspecified rate)
	// For 0.25 rate, threshold is 0.25 * (1 << 63) = 0x2000000000000000
	// We need (x >> 1) >= threshold, so x >= threshold * 2
	threshold := uint64(0.25 * (1 << 63))
	x := threshold*2 + 1 // This ensures (x >> 1) = threshold + 0.5 >= threshold

	// Create trace ID with the calculated value in the upper 8 bytes
	traceIDBytes := make([]byte, 16)
	binary.BigEndian.PutUint64(traceIDBytes[8:16], x)
	traceID := trace.TraceID{}
	copy(traceID[:], traceIDBytes)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindClient,
	}

	result := sampler.ShouldSample(params)

	// Should NOT sample based on Unspecified kind rate (0.25) since trace ID is above threshold
	if result.Decision != sdktrace.Drop {
		t.Errorf("Expected decision %v, got %v", sdktrace.Drop, result.Decision)
	}
}

func TestTraceSampler_Description(t *testing.T) {
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
		trace.SpanKindClient: 0.25,
	}
	sampler := getSampler(rates)

	// The description format is "TraceIDRatioBased{map[key:value]}"
	// Since map order is not guaranteed, we'll check that it contains the expected parts
	description := sampler.Description()
	if !contains(description, "TraceIDRatioBased{map[") {
		t.Errorf("Expected description to contain 'TraceIDRatioBased{map[', got %s", description)
	}
	if !contains(description, "}") {
		t.Errorf("Expected description to end with '}', got %s", description)
	}

	// Check that it contains the expected key-value pairs
	// The actual format uses lowercase keys like "server:0.5"
	if !contains(description, "server:0.5") {
		t.Errorf("Expected description to contain server rate, got %s", description)
	}
	if !contains(description, "client:0.25") {
		t.Errorf("Expected description to contain client rate, got %s", description)
	}
}

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr ||
		(len(s) > len(substr) && (s[:len(substr)] == substr ||
			s[len(s)-len(substr):] == substr ||
			contains(s[1:], substr))))
}

func TestGetSampler_EmptyRates(t *testing.T) {
	rates := map[trace.SpanKind]float64{}
	sampler := getSampler(rates)

	// Should create sampler with empty bounds
	if len(sampler.traceIDUpperBounds) != 0 {
		t.Errorf("Expected empty bounds, got %d", len(sampler.traceIDUpperBounds))
	}
	if sampler.description != "TraceIDRatioBased{map[]}" {
		t.Errorf("Expected description %s, got %s", "TraceIDRatioBased{map[]}", sampler.description)
	}
}

func TestGetSampler_WithRates(t *testing.T) {
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
		trace.SpanKindClient: 0.25,
	}
	sampler := getSampler(rates)

	// Check that bounds are calculated correctly
	// 0.5 * (1 << 63) = 0x4000000000000000
	// 0.25 * (1 << 63) = 0x2000000000000000
	expectedServerBound := uint64(0.5 * (1 << 63))
	expectedClientBound := uint64(0.25 * (1 << 63))

	if sampler.traceIDUpperBounds[trace.SpanKindServer] != expectedServerBound {
		t.Errorf("Expected server bound %d, got %d", expectedServerBound, sampler.traceIDUpperBounds[trace.SpanKindServer])
	}
	if sampler.traceIDUpperBounds[trace.SpanKindClient] != expectedClientBound {
		t.Errorf("Expected client bound %d, got %d", expectedClientBound, sampler.traceIDUpperBounds[trace.SpanKindClient])
	}
}

func TestTraceSampler_ShouldSample_EdgeCase_ZeroRate(t *testing.T) {
	// Create a sampler with zero rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.0,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// With 0.0 rate, threshold is 0, so no trace ID should be sampled
	if result.Decision != sdktrace.Drop {
		t.Errorf("Expected decision %v, got %v", sdktrace.Drop, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_EdgeCase_OneRate(t *testing.T) {
	// Create a sampler with 100% rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 1.0,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// With 1.0 rate, threshold is max uint64, so all trace IDs should be sampled
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_TraceIDCalculation(t *testing.T) {
	// Create a sampler with 0.5 rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test the exact calculation from the code
	// The code does: binary.BigEndian.Uint64(p.TraceID[8:16]) >> 1
	// For 0.5 rate, threshold is 0.5 * (1 << 63) = 0x4000000000000000
	// The condition is: if x < bound then sample

	// Create a trace ID where the upper 8 bytes when shifted right by 1 equals exactly the threshold
	threshold := uint64(0.5 * (1 << 63))
	// We need (x >> 1) = threshold, so x = threshold * 2
	x := threshold * 2

	// Create trace ID with the calculated value in the upper 8 bytes
	traceIDBytes := make([]byte, 16)
	binary.BigEndian.PutUint64(traceIDBytes[8:16], x)
	traceID := trace.TraceID{}
	copy(traceID[:], traceIDBytes)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// This should be exactly at the threshold, so (x >> 1) = threshold
	// Since the condition is x < bound, and x = threshold, this should NOT sample
	if result.Decision != sdktrace.Drop {
		t.Errorf("Expected decision %v, got %v", sdktrace.Drop, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_TraceIDCalculation_JustBelowThreshold(t *testing.T) {
	// Create a sampler with 0.5 rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with a value just below the threshold
	threshold := uint64(0.5 * (1 << 63))
	// We need (x >> 1) < threshold, so x < threshold * 2
	x := threshold*2 - 2 // This ensures (x >> 1) = threshold - 1 < threshold

	// Create trace ID with the calculated value in the upper 8 bytes
	traceIDBytes := make([]byte, 16)
	binary.BigEndian.PutUint64(traceIDBytes[8:16], x)
	traceID := trace.TraceID{}
	copy(traceID[:], traceIDBytes)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// This should be just below the threshold, so it should sample
	if result.Decision != sdktrace.RecordAndSample {
		t.Errorf("Expected decision %v, got %v", sdktrace.RecordAndSample, result.Decision)
	}
}

func TestTraceSampler_ShouldSample_TraceIDCalculation_JustAboveThreshold(t *testing.T) {
	// Create a sampler with 0.5 rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// Create a parent context without sampled trace
	parentTraceID := trace.TraceID{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
	parentSpanID := trace.SpanID{1, 2, 3, 4, 5, 6, 7, 8}
	parentContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID:    parentTraceID,
		SpanID:     parentSpanID,
		TraceFlags: 0, // Not sampled
	})
	ctx := trace.ContextWithSpanContext(context.Background(), parentContext)

	// Test with a value just above the threshold
	threshold := uint64(0.5 * (1 << 63))
	// We need (x >> 1) >= threshold, so x >= threshold * 2
	x := threshold*2 + 2 // This ensures (x >> 1) = threshold + 1 >= threshold

	// Create trace ID with the calculated value in the upper 8 bytes
	traceIDBytes := make([]byte, 16)
	binary.BigEndian.PutUint64(traceIDBytes[8:16], x)
	traceID := trace.TraceID{}
	copy(traceID[:], traceIDBytes)

	params := sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test-span",
		Kind:          trace.SpanKindServer,
	}

	result := sampler.ShouldSample(params)

	// This should be just above the threshold, so it should NOT sample
	if result.Decision != sdktrace.Drop {
		t.Errorf("Expected decision %v, got %v", sdktrace.Drop, result.Decision)
	}
}

func TestTraceSampler_StatisticalSampling_50PercentRate(t *testing.T) {
	// Create a sampler with 50% rate
	rates := map[trace.SpanKind]float64{
		trace.SpanKindServer: 0.5,
	}
	sampler := getSampler(rates)

	// The test is non-deterministic, so this tries to reach a good balance of
	// trials versus speed. When running tests with the race detector performance
	// is very slow, so we run fewer trials and make the deviation tolerance larger.
	// These number can be adjusted if the test proves flaky.
	const numSpans = 100_000
	const expectedRate = 0.5
	const tolerance = 0.005 // Allow 0.5% deviation from expected rate

	sampledCount := 0

	for i := range numSpans {
		// Generate a random trace ID for each span
		traceID := generateRandomTraceID()

		params := sdktrace.SamplingParameters{
			ParentContext: context.Background(),
			TraceID:       traceID,
			Name:          fmt.Sprintf("test-span-%d", i),
			Kind:          trace.SpanKindServer,
		}

		result := sampler.ShouldSample(params)
		if result.Decision == sdktrace.RecordAndSample {
			sampledCount++
		}
	}

	// Calculate observed sampling rate
	observedRate := float64(sampledCount) / float64(numSpans)

	// Check if observed rate is within acceptable tolerance
	if math.Abs(observedRate-expectedRate) > tolerance {
		t.Errorf("Observed sampling rate %.6f does not match expected rate %.6f within tolerance %.6f",
			observedRate, expectedRate, tolerance)
		t.Errorf("Sampled %d out of %d spans (expected approximately %d)",
			sampledCount, numSpans, int(float64(numSpans)*expectedRate))
	}

	// Log the results for verification
	t.Logf("Statistical sampling test results:")
	t.Logf("  Total spans tested: %d", numSpans)
	t.Logf("  Spans sampled: %d", sampledCount)
	t.Logf("  Observed rate: %.6f", observedRate)
	t.Logf("  Expected rate: %.6f", expectedRate)
	t.Logf("  Difference: %.6f", math.Abs(observedRate-expectedRate))
	t.Logf("  Tolerance: %.6f", tolerance)
}

// generateRandomTraceID creates a random trace ID for testing
func generateRandomTraceID() trace.TraceID {
	var traceID trace.TraceID
	for i := 0; i < 16; i++ {
		traceID[i] = byte(rand.Intn(256))
	}
	return traceID
}
