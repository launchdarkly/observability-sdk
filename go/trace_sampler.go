package ldobserve

import (
	"encoding/binary"
	"fmt"

	"github.com/samber/lo"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

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
