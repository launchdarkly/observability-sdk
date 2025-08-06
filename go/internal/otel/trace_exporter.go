package otel

import (
	"context"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

type traceExporter struct {
	sdktrace.SpanExporter
	sampler ExportSampler
}

// readOnlySpanWorkaround is a workaround to allow us to add extra attributes to a span.
// This is because the span is readonly, and the OTEL SDK implementation prevents
// creating a ReadOnlySpan outside of the SDK.
// So we wrap the ReadOnlySpan in a struct and provide an alternate implementation
// of the Attributes method that returns the original attributes plus the extra attributes.
type readOnlySpanWorkaround struct {
	sdktrace.ReadOnlySpan
	extraAttributes []attribute.KeyValue
}

func (r readOnlySpanWorkaround) Attributes() []attribute.KeyValue {
	return append(r.ReadOnlySpan.Attributes(), r.extraAttributes...)
}

// ExportSpans implements trace.SpanExporter.
func (t *traceExporter) ExportSpans(ctx context.Context, spans []sdktrace.ReadOnlySpan) error {
	omittedSpanIds := make([]trace.SpanID, 0, len(spans))
	spanById := make(map[trace.SpanID]sdktrace.ReadOnlySpan)
	childrenByParentId := make(map[trace.SpanID][]trace.SpanID)

	// THe first pass we sample items which are directly impacted by a sampling decision.
	// We also build a map of children spans by parent span id, which allows us to quickly traverse the span tree.
	for _, s := range spans {
		parentId := s.Parent().SpanID()
		if parentId.IsValid() {
			childrenByParentId[parentId] = append(childrenByParentId[parentId], s.SpanContext().SpanID())
		}

		res := t.sampler.SampleSpan(s)
		if res.Sample {
			rs := readOnlySpanWorkaround{
				ReadOnlySpan:    s,
				extraAttributes: res.Attributes,
			}
			spanById[s.SpanContext().SpanID()] = rs
		} else {
			omittedSpanIds = append(omittedSpanIds, s.SpanContext().SpanID())
		}
	}

	// Find all children of spans that have been sampled out and remove them.
	// Repeat until there are no more children to remove.
	for len(omittedSpanIds) != 0 {

		spanId, o := omittedSpanIds[0], omittedSpanIds[1:]
		omittedSpanIds = o

		affectedSpans := childrenByParentId[spanId]
		if affectedSpans == nil {
			continue
		}
		omittedSpanIds = append(omittedSpanIds, affectedSpans...)

		for _, affectedSpanId := range affectedSpans {
			delete(spanById, affectedSpanId)
		}
	}

	exportedSpans := make([]sdktrace.ReadOnlySpan, 0, len(spans))
	for _, s := range spanById {
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

func newTraceExporter(exporter sdktrace.SpanExporter, sampler ExportSampler) *traceExporter {
	return &traceExporter{SpanExporter: exporter, sampler: sampler}
}

var _ sdktrace.SpanExporter = &traceExporter{}
