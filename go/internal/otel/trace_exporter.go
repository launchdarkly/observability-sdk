package otel

import (
	"context"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/sdk/trace"
)

type traceExporter struct {
	trace.SpanExporter
	sampler ExportSampler
}

// readOnlySpanWorkaround is a workaround to allow us to add extra attributes to a span.
// This is because the span is readonly, and the OTEL SDK implementation prevents
// creating a ReadOnlySpan outside of the SDK.
// So we wrap the ReadOnlySpan in a struct and provide an alternate implementation
// of the Attributes method that returns the original attributes plus the extra attributes.
type readOnlySpanWorkaround struct {
	trace.ReadOnlySpan
	extraAttributes []attribute.KeyValue
}

func (r readOnlySpanWorkaround) Attributes() []attribute.KeyValue {
	return append(r.ReadOnlySpan.Attributes(), r.extraAttributes...)
}

// ExportSpans implements trace.SpanExporter.
func (t *traceExporter) ExportSpans(ctx context.Context, spans []trace.ReadOnlySpan) error {
	exportedSpans := make([]trace.ReadOnlySpan, 0, len(spans))
	for _, s := range spans {
		res := t.sampler.SampleSpan(s)
		if res.Sample {
			exportedSpans = append(
				exportedSpans,
				readOnlySpanWorkaround{
					ReadOnlySpan:    s,
					extraAttributes: res.Attributes,
				},
			)
		}
	}

	return t.SpanExporter.ExportSpans(ctx, exportedSpans)
}

func newTraceExporter(exporter trace.SpanExporter, sampler ExportSampler) *traceExporter {
	return &traceExporter{SpanExporter: exporter, sampler: sampler}
}

var _ trace.SpanExporter = &traceExporter{}
