package otel

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel/sdk/trace"
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
