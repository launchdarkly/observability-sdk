package otel

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/sdk/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/gql"
)

type testExporter struct {
	exportedSpans []trace.ReadOnlySpan
}

// Shutdown implements trace.SpanExporter.
func (e *testExporter) Shutdown(ctx context.Context) error {
	return nil
}

func (e *testExporter) ExportSpans(ctx context.Context, spans []trace.ReadOnlySpan) error {
	e.exportedSpans = append(e.exportedSpans, spans...)
	return nil
}

var _ trace.SpanExporter = &testExporter{}

func TestTraceExporter_NoSamplingConfig(t *testing.T) {
	// Test that spans with no sampling configuration are always sampled
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)
	tracer := provider.Tracer("test")
	_, span := tracer.Start(context.Background(), "unmatched-span")
	span.SetAttributes(attribute.String("service.name", "unmatched-service"))
	span.End()

	// Verify the span was exported (should always be sampled when no config matches)
	if len(exporter.exportedSpans) != 1 {
		t.Errorf("expected 1 exported span, got %d", len(exporter.exportedSpans))
	}

	// Verify no sampling ratio attribute was added (since no config matched)
	exportedSpan := exporter.exportedSpans[0]
	for _, attr := range exportedSpan.Attributes() {
		if attr.Key == "launchdarkly.sampling.ratio" {
			t.Error("unexpected sampling ratio attribute on span with no matching config")
		}
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_WithMatchingConfigSampledIn(t *testing.T) {
	// Test that spans with matching configuration and sampler returning true are exported with sampling ratio
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)

	// Set up a configuration that matches our span
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Spans: []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig{
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test-span",
					},
				},
				SamplingRatio: 1,
			},
		},
	}
	customSampler.SetConfig(config)

	tracer := provider.Tracer("test")
	_, span := tracer.Start(context.Background(), "test-span")
	span.SetAttributes(attribute.String("service.name", "test-service"))
	span.End()

	// Verify the span was exported
	if len(exporter.exportedSpans) != 1 {
		t.Errorf("expected 1 exported span, got %d", len(exporter.exportedSpans))
	}

	// Verify the sampling ratio attribute was added with correct value
	exportedSpan := exporter.exportedSpans[0]
	foundSamplingRatio := false
	expectedRatio := int64(1) // From the config we set
	for _, attr := range exportedSpan.Attributes() {
		if attr.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio = true
			if attr.Value.Type() != attribute.INT64 {
				t.Errorf("expected sampling ratio attribute to be INT64 type, got %v", attr.Value.Type())
			}
			if attr.Value.AsInt64() != expectedRatio {
				t.Errorf("expected sampling ratio to be %d, got %d", expectedRatio, attr.Value.AsInt64())
			}
			break
		}
	}
	if !foundSamplingRatio {
		t.Error("expected sampling ratio attribute to be added to exported span")
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_WithMatchingConfigSampledOut(t *testing.T) {
	// Test that spans with matching configuration but sampler returning false are not exported
	customSampler := NewCustomSampler(neverSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)

	// Set up a configuration that matches our span
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Spans: []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig{
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test-span",
					},
				},
				SamplingRatio: 1,
			},
		},
	}
	customSampler.SetConfig(config)

	tracer := provider.Tracer("test")
	_, span := tracer.Start(context.Background(), "test-span")
	span.SetAttributes(attribute.String("service.name", "test-service"))
	span.End()

	// Verify no spans were exported (since neverSampler returns false)
	if len(exporter.exportedSpans) != 0 {
		t.Errorf("expected 0 exported spans, got %d", len(exporter.exportedSpans))
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_WithDifferentSamplingRatio(t *testing.T) {
	// Test that spans with different sampling ratios get the correct value
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)

	// Set up a configuration with a different sampling ratio
	expectedRatio := int64(10)
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Spans: []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig{
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "test-span",
					},
				},
				SamplingRatio: int(expectedRatio),
			},
		},
	}
	customSampler.SetConfig(config)

	tracer := provider.Tracer("test")
	_, span := tracer.Start(context.Background(), "test-span")
	span.SetAttributes(attribute.String("service.name", "test-service"))
	span.End()

	// Verify the span was exported
	if len(exporter.exportedSpans) != 1 {
		t.Errorf("expected 1 exported span, got %d", len(exporter.exportedSpans))
	}

	// Verify the sampling ratio attribute was added with correct value
	exportedSpan := exporter.exportedSpans[0]
	foundSamplingRatio := false
	for _, attr := range exportedSpan.Attributes() {
		if attr.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio = true
			if attr.Value.Type() != attribute.INT64 {
				t.Errorf("expected sampling ratio attribute to be INT64 type, got %v", attr.Value.Type())
			}
			if attr.Value.AsInt64() != expectedRatio {
				t.Errorf("expected sampling ratio to be %d, got %d", expectedRatio, attr.Value.AsInt64())
			}
			break
		}
	}
	if !foundSamplingRatio {
		t.Error("expected sampling ratio attribute to be added to exported span")
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_MixedSpans(t *testing.T) {
	// Test that a mix of sampled and non-sampled spans are handled correctly
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)
	tracer := provider.Tracer("test")

	_, span1 := tracer.Start(context.Background(), "sampled-span")
	span1.SetAttributes(attribute.String("service.name", "test-service"))
	span1.End()

	_, span2 := tracer.Start(context.Background(), "another-sampled-span")
	span2.SetAttributes(attribute.String("service.name", "another-service"))
	span2.End()

	// Verify both spans were exported
	if len(exporter.exportedSpans) != 2 {
		t.Errorf("expected 2 exported spans, got %d", len(exporter.exportedSpans))
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_MultipleSpansWithDifferentRatios(t *testing.T) {
	// Test that multiple spans with different sampling ratios get the correct values
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)

	// Set up a configuration with multiple span configs
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Spans: []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig{
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "span-1",
					},
				},
				SamplingRatio: 5,
			},
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "span-2",
					},
				},
				SamplingRatio: 10,
			},
		},
	}
	customSampler.SetConfig(config)

	tracer := provider.Tracer("test")

	// Create first span
	_, span1 := tracer.Start(context.Background(), "span-1")
	span1.SetAttributes(attribute.String("service.name", "test-service"))
	span1.End()

	// Create second span
	_, span2 := tracer.Start(context.Background(), "span-2")
	span2.SetAttributes(attribute.String("service.name", "another-service"))
	span2.End()

	// Verify both spans were exported
	if len(exporter.exportedSpans) != 2 {
		t.Errorf("expected 2 exported spans, got %d", len(exporter.exportedSpans))
	}

	// Verify the first span has the correct sampling ratio
	exportedSpan1 := exporter.exportedSpans[0]
	foundSamplingRatio1 := false
	for _, attr := range exportedSpan1.Attributes() {
		if attr.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio1 = true
			if attr.Value.AsInt64() != 5 {
				t.Errorf("expected first span sampling ratio to be 5, got %d", attr.Value.AsInt64())
			}
			break
		}
	}
	if !foundSamplingRatio1 {
		t.Error("expected sampling ratio attribute to be added to first exported span")
	}

	// Verify the second span has the correct sampling ratio
	exportedSpan2 := exporter.exportedSpans[1]
	foundSamplingRatio2 := false
	for _, attr := range exportedSpan2.Attributes() {
		if attr.Key == "launchdarkly.sampling.ratio" {
			foundSamplingRatio2 = true
			if attr.Value.AsInt64() != 10 {
				t.Errorf("expected second span sampling ratio to be 10, got %d", attr.Value.AsInt64())
			}
			break
		}
	}
	if !foundSamplingRatio2 {
		t.Error("expected sampling ratio attribute to be added to second exported span")
	}

	provider.Shutdown(context.Background())
}

func TestTraceExporter_EmptySpansSlice(t *testing.T) {
	// Test that empty spans slice is handled correctly
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	traceExporter := newTraceExporter(exporter, customSampler)

	// Export empty slice
	err := traceExporter.ExportSpans(context.Background(), []trace.ReadOnlySpan{})
	if err != nil {
		t.Fatalf("ExportSpans failed: %v", err)
	}

	// Verify no spans were exported
	if len(exporter.exportedSpans) != 0 {
		t.Errorf("expected 0 exported spans, got %d", len(exporter.exportedSpans))
	}
}

func TestCanSampleOutSpans(t *testing.T) {
	customSampler := NewCustomSampler(alwaysSampler)
	exporter := &testExporter{}
	spanProcessor := trace.NewSimpleSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)
	tracer := provider.Tracer("test")
	_, span := tracer.Start(context.Background(), "test")
	span.End()
	if len(exporter.exportedSpans) != 1 {
		t.Errorf("expected 1 span, got %d", len(exporter.exportedSpans))
	}
	provider.Shutdown(context.Background())
}

func TestTraceExporter_RemoveChildrenOfUnsampledSpans(t *testing.T) {
	// Test that children of spans that are not sampled are also removed
	customSampler := NewCustomSampler(neverSampler)
	exporter := &testExporter{}
	// Sampling out children can only work if the children are in the same batch as the parent.
	// So we use a batch span processor, versus a simple span processor which looks at
	// one span at a time.
	spanProcessor := trace.NewBatchSpanProcessor(newTraceExporter(exporter, customSampler))
	provider := trace.NewTracerProvider(
		trace.WithSpanProcessor(spanProcessor),
	)

	// Set up a configuration that matches our spans
	config := &gql.GetSamplingConfigSamplingSamplingConfig{
		Spans: []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig{
			{
				Name: gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigNameMatchConfig{
					MatchParts: gql.MatchParts{
						MatchValue: "parent",
					},
				},
				SamplingRatio: 1,
			},
		},
	}
	customSampler.SetConfig(config)

	tracer := provider.Tracer("test")

	// Create a span hierarchy: parent -> child
	ctx := context.Background()

	// Create parent span (will be sampled out by neverSampler)
	ctx, parentSpan := tracer.Start(ctx, "parent")

	// Create child span (should be removed because parent is sampled out)
	_, childSpan := tracer.Start(ctx, "child")

	// End all spans in reverse order
	childSpan.End()
	parentSpan.End()
	// Flush the batch processor.
	spanProcessor.ForceFlush(ctx)

	// Verify no spans were exported (since parent is sampled out and child should be removed)
	if len(exporter.exportedSpans) != 0 {
		t.Errorf("expected 0 exported spans, got %d", len(exporter.exportedSpans))
		// Debug: print out the exported spans
		for i, span := range exporter.exportedSpans {
			t.Logf("exported span %d: %s", i, span.Name())
		}
	}

	provider.Shutdown(context.Background())
}
