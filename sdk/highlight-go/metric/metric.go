package hmetric

import (
	"context"
	highlight "github.com/highlight/highlight/sdk/highlight-go"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
	"math/rand"
)

// Histogram tracks the statistical distribution of a set of values on each host.
func Histogram(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordHistogram(ctx, name, value, tags...)
}

// HistogramWithOptions is Histogram with control over how the instrument is
// declared — most usefully its bucket boundaries.
//
// A histogram with no explicit boundaries inherits the OTEL default set, which tops
// out at 10000 and is shaped for millisecond latencies; values outside that range
// all land in the +Inf overflow bucket and no quantile can be computed. Declare
// boundaries whenever the metric is on a different scale.
//
// Options are read when the instrument is first built for a name and ignored
// afterwards, so pass the same ones at every call site for a given metric.
func HistogramWithOptions(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64, opts ...metric.Float64HistogramOption) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordHistogramWithOptions(ctx, name, value, tags, opts...)
}

// Incr is just Count of 1
// Count tracks how many times something happened per second.
func Incr(ctx context.Context, name string, tags []attribute.KeyValue, rate float64) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordCount(ctx, name, 1, tags...)
}

// IncrWithOptions is Incr with control over how the instrument is declared — its
// unit and description. See HistogramWithOptions for when the options take effect.
func IncrWithOptions(ctx context.Context, name string, tags []attribute.KeyValue, rate float64, opts ...metric.Int64CounterOption) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordCountWithOptions(ctx, name, 1, tags, opts...)
}

// Gauge measures the value of a metric at a particular time.
func Gauge(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordMetric(ctx, name, value, tags...)
}

// GaugeWithOptions is Gauge with control over how the instrument is declared — its
// unit and description. See HistogramWithOptions for when the options take effect.
func GaugeWithOptions(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64, opts ...metric.Float64GaugeOption) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordMetricWithOptions(ctx, name, value, tags, opts...)
}
