package hmetric

import (
	"context"
	highlight "github.com/highlight/highlight/sdk/highlight-go"
	"go.opentelemetry.io/otel/attribute"
	"math/rand"
)

// HistogramConfig configures histogram instrument creation for a single record call.
type HistogramConfig struct {
	Unit        string
	Description string
	Boundaries  []float64
}

// Histogram tracks the statistical distribution of a set of values on each host.
func Histogram(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64) {
	HistogramWithConfig(ctx, name, value, tags, rate, HistogramConfig{})
}

// HistogramWithConfig records a histogram value with explicit instrument options.
func HistogramWithConfig(
	ctx context.Context,
	name string,
	value float64,
	tags []attribute.KeyValue,
	rate float64,
	config HistogramConfig,
) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordHistogramWithConfig(ctx, name, value, highlight.HistogramConfig{
		Unit:        config.Unit,
		Description: config.Description,
		Boundaries:  config.Boundaries,
	}, tags...)
}

// Incr is just Count of 1
// Count tracks how many times something happened per second.
func Incr(ctx context.Context, name string, tags []attribute.KeyValue, rate float64) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordCount(ctx, name, 1, tags...)
}

// Gauge measures the value of a metric at a particular time.
func Gauge(ctx context.Context, name string, value float64, tags []attribute.KeyValue, rate float64) {
	if rand.Float64() > rate {
		return
	}
	highlight.RecordMetric(ctx, name, value, tags...)
}
