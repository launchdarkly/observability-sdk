package otel

import (
	"context"
	"sync"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"

	"github.com/launchdarkly/observability-sdk/go/internal/logging"
)

//nolint:gochecknoglobals
var float64GaugesLock = sync.RWMutex{}

//nolint:gochecknoglobals
var float64Gauges = make(map[string]metric.Float64Gauge, 1000)

//nolint:gochecknoglobals
var float64HistogramsLock = sync.RWMutex{}

//nolint:gochecknoglobals
var float64Histograms = make(map[string]metric.Float64Histogram, 1000)

//nolint:gochecknoglobals
var int64CountersLock = sync.RWMutex{}

//nolint:gochecknoglobals
var int64Counters = make(map[string]metric.Int64Counter, 1000)

// RecordMetric is used to record arbitrary metrics in your golang backend.
func RecordMetric(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	var err error
	float64GaugesLock.RLock()
	if g := float64Gauges[name]; g == nil {
		float64GaugesLock.RUnlock()
		float64GaugesLock.Lock()
		// Between releasing the read lock and acquiring the write lock,
		// another goroutine could have created the gauge.
		if g := float64Gauges[name]; g == nil {
			float64Gauges[name], err = GetMeter().Float64Gauge(name)
		}
		float64GaugesLock.Unlock()
		if err != nil {
			logging.GetLogger().Errorf("error creating float64 gauge %s: %v", name, err)
			return
		}
		// Re-lock for reading.
		float64GaugesLock.RLock()
	}

	float64Gauges[name].Record(ctx, value, metric.WithAttributes(tags...))
	float64GaugesLock.RUnlock()
}

// RecordHistogram is used to record arbitrary histograms in your golang backend.
func RecordHistogram(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	var err error
	float64HistogramsLock.RLock()
	if h := float64Histograms[name]; h == nil {
		float64HistogramsLock.RUnlock()
		float64HistogramsLock.Lock()
		// Between releasing the read lock and acquiring the write lock,
		// another goroutine could have created the histogram.
		if h := float64Histograms[name]; h == nil {
			float64Histograms[name], err = GetMeter().Float64Histogram(name)
		}
		float64HistogramsLock.Unlock()
		if err != nil {
			logging.GetLogger().Errorf("error creating float64 histogram %s: %v", name, err)
			return
		}
		// Re-lock for reading.
		float64HistogramsLock.RLock()
	}

	float64Histograms[name].Record(ctx, value, metric.WithAttributes(tags...))
	float64HistogramsLock.RUnlock()
}

// RecordCount is used to record arbitrary counts in your golang backend.
func RecordCount(ctx context.Context, name string, value int64, tags ...attribute.KeyValue) {
	var err error
	int64CountersLock.RLock()
	if c := int64Counters[name]; c == nil {
		int64CountersLock.RUnlock()
		int64CountersLock.Lock()
		// Between releasing the read lock and acquiring the write lock,
		// another goroutine could have created the counter.
		if c := int64Counters[name]; c == nil {
			int64Counters[name], err = GetMeter().Int64Counter(name)
		}
		int64CountersLock.Unlock()
		if err != nil {
			logging.GetLogger().Errorf("error creating int64 counter %s: %v", name, err)
			return
		}
		// Re-lock for reading.
		int64CountersLock.RLock()
	}

	int64Counters[name].Add(ctx, value, metric.WithAttributes(tags...))
	int64CountersLock.RUnlock()
}
