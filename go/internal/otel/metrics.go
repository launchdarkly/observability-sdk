package otel

import (
	"context"
	"fmt"
	"sync"

	"github.com/launchdarkly/observability-sdk/go/internal/logging"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
)

var float64GaugesLock = sync.RWMutex{}
var float64Gauges = make(map[string]metric.Float64Gauge, 1000)
var float64HistogramsLock = sync.RWMutex{}
var float64Histograms = make(map[string]metric.Float64Histogram, 1000)
var int64CountersLock = sync.RWMutex{}
var int64Counters = make(map[string]metric.Int64Counter, 1000)

// RecordMetric is used to record arbitrary metrics in your golang backend.
// Highlight will process these metrics in the context of your session and expose them
// through dashboards. For example, you may want to record the latency of a DB query
// as a metric that you would like to graph and monitor. You'll be able to view the metric
// in the context of the session and network request and recorded it.
func RecordMetric(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	var err error
	float64GaugesLock.RLock()
	if g := float64Gauges[name]; g == nil {
		float64GaugesLock.RUnlock()
		float64GaugesLock.Lock()
		float64Gauges[name], err = GetMeter().Float64Gauge(name)
		float64GaugesLock.Unlock()
		if err != nil {
			logging.Log.Errorf("error creating float64 gauge %s: %v", name, err)
			return
		}
	} else {
		float64GaugesLock.RUnlock()
	}
	float64Gauges[name].Record(ctx, value, metric.WithAttributes(tags...))
}

func RecordHistogram(ctx context.Context, name string, value float64, tags ...attribute.KeyValue) {
	var err error
	float64HistogramsLock.RLock()
	if h := float64Histograms[name]; h == nil {
		float64HistogramsLock.RUnlock()
		float64HistogramsLock.Lock()
		float64Histograms[name], err = GetMeter().Float64Histogram(name)
		float64HistogramsLock.Unlock()
		if err != nil {
			fmt.Printf("error creating float64 histogram %s: %v", name, err)
			return
		}
	} else {
		float64HistogramsLock.RUnlock()
	}
	float64Histograms[name].Record(ctx, value, metric.WithAttributes(tags...))
}

func RecordCount(ctx context.Context, name string, value int64, tags ...attribute.KeyValue) {
	var err error
	int64CountersLock.RLock()
	if c := int64Counters[name]; c == nil {
		int64CountersLock.RUnlock()
		int64CountersLock.Lock()
		int64Counters[name], err = GetMeter().Int64Counter(name)
		int64CountersLock.Unlock()
		if err != nil {
			fmt.Printf("error creating float64 histogram %s: %v", name, err)
			return
		}
	} else {
		int64CountersLock.RUnlock()
	}
	int64Counters[name].Add(ctx, value, metric.WithAttributes(tags...))
}
