package ldobserve

import (
	"encoding/binary"
	"fmt"

	"github.com/samber/lo"
	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	"github.com/launchdarkly/observability-sdk/go/attributes"
)

// ForceSampleAttribute is the attribute that opts a span into full sampling.
// Prefer the ForceSample span start option; reach for this only when you need
// to append the attribute to a list you are already building.
//
// The attribute is deliberately left on the exported span. A server-side
// sampling rule can match it to dial every force-sampled span back down at
// once, without a deploy.
func ForceSampleAttribute() attribute.KeyValue {
	return attribute.Bool(attributes.AttrForceSample, true)
}

// ForceSample returns a span start option that samples a single span at 100%,
// independently of its SpanKind and of the configured per-kind rates.
//
// Use it for a span carrying a discrete event you never want to miss, whose
// payload lives in its own attributes. A force-sampled span also samples in
// everything beneath it, including trace context it propagates to another
// process, so do not use it on a high-fanout span or on a cross-process publish
// (see INC-241).
func ForceSample() trace.SpanStartOption {
	return trace.WithAttributes(ForceSampleAttribute())
}

// hasForceSample reports whether the span was started with the ForceSample
// marker. Only the start-time attributes reach a sampler, so a marker added
// later via Span.SetAttributes has no effect.
func hasForceSample(attrs []attribute.KeyValue) bool {
	for _, attr := range attrs {
		if string(attr.Key) == attributes.AttrForceSample {
			// Type check, not just AsBool: OTel packs bool and int into the
			// same numeric field, so Int(key, 1) would otherwise read as true.
			return attr.Value.Type() == attribute.BOOL && attr.Value.AsBool()
		}
	}
	return false
}

type traceSampler struct {
	traceIDUpperBounds map[trace.SpanKind]uint64
	description        string
}

func (ts traceSampler) ShouldSample(p sdktrace.SamplingParameters) sdktrace.SamplingResult {
	psc := trace.SpanContextFromContext(p.ParentContext)
	if psc.IsSampled() {
		return sdktrace.SamplingResult{
			Decision:   sdktrace.RecordAndSample,
			Tracestate: psc.TraceState(),
		}
	}
	// An explicit marker is a deliberate per-call statement rather than a
	// re-roll of a decision the trace already made, so it outranks the
	// unsampled-parent drop below. This is the only supported way to say
	// "always keep this span"; SpanKind must never carry that meaning.
	if hasForceSample(p.Attributes) {
		return sdktrace.SamplingResult{
			Decision:   sdktrace.RecordAndSample,
			Tracestate: psc.TraceState(),
		}
	}
	// A valid unsampled parent already decided this trace. Do not let the
	// child's SpanKind re-roll — that would sample-in Producer children of
	// dropped Internal/Consumer work (e.g. kafka.submit under GraphQL).
	// Kind rates apply only to roots (no parent).
	if psc.IsValid() {
		return sdktrace.SamplingResult{
			Decision:   sdktrace.Drop,
			Tracestate: psc.TraceState(),
		}
	}
	bound, ok := ts.traceIDUpperBounds[p.Kind]
	if !ok {
		bound, ok = ts.traceIDUpperBounds[trace.SpanKindUnspecified]
		// If there are no bounds specified, then we sample all
		// Avoiding doing work here versus having default bounds which would
		// would require additional work per span.
		if !ok {
			return sdktrace.SamplingResult{
				Decision:   sdktrace.RecordAndSample,
				Tracestate: psc.TraceState(),
			}
		}
	}

	x := binary.BigEndian.Uint64(p.TraceID[8:16]) >> 1
	if x < bound {
		return sdktrace.SamplingResult{
			Decision:   sdktrace.RecordAndSample,
			Tracestate: psc.TraceState(),
		}
	}
	return sdktrace.SamplingResult{
		Decision:   sdktrace.Drop,
		Tracestate: psc.TraceState(),
	}
}

func (ts traceSampler) Description() string {
	return ts.description
}

// creates a per-span-kind sampler that samples each kind at a provided fraction.
func getSampler(rates map[trace.SpanKind]float64) traceSampler {
	return traceSampler{
		description: fmt.Sprintf("TraceIDRatioBased{%+v}", rates),
		traceIDUpperBounds: lo.MapEntries(rates, func(key trace.SpanKind, value float64) (trace.SpanKind, uint64) {
			return key, uint64(value * (1 << 63))
		}),
	}
}
