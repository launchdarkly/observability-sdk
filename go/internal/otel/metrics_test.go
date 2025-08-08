package otel

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
)

// These tests do not test metrics end-to-end, but they do verify that the functions
// don't panic, and also should provide enough for the race detector to catch
// synchronization issues.

func TestRecordMetric(t *testing.T) {
	tests := []struct {
		name       string
		metricName string
		value      float64
		tags       []attribute.KeyValue
	}{
		{
			name:       "basic metric recording",
			metricName: "test.metric",
			value:      1.0,
		},
		{
			name:       "metric with tags",
			metricName: "test.metric.with.tags",
			value:      42.5,
			tags:       []attribute.KeyValue{attribute.String("service", "test"), attribute.Int("version", 1)},
		},
		{
			name:       "zero value metric",
			metricName: "test.zero.metric",
			value:      0.0,
		},
		{
			name:       "negative value metric",
			metricName: "test.negative.metric",
			value:      -10.5,
		},
	}

	// Clear the global gauges map before testing to ensure clean state
	float64GaugesLock.Lock()
	float64Gauges = make(map[string]metric.Float64Gauge, 1000)
	float64GaugesLock.Unlock()

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			// Test that the function can be called without panicking
			RecordMetric(ctx, tt.metricName, tt.value, tt.tags...)
			// If we get here, the function executed without errors
		})
	}

	// Verify that gauges were created for each test case
	float64GaugesLock.RLock()
	defer float64GaugesLock.RUnlock()

	if len(float64Gauges) != len(tests) {
		t.Errorf("Expected %d float64 gauges, got %d", len(tests), len(float64Gauges))
	}

	// Verify each expected gauge was created
	for _, tt := range tests {
		if gauge, exists := float64Gauges[tt.metricName]; !exists {
			t.Errorf("Expected gauge %s to be created", tt.metricName)
		} else if gauge == nil {
			t.Errorf("Gauge %s was created but is nil", tt.metricName)
		}
	}

	// Verify no unexpected gauges were created
	for gaugeName := range float64Gauges {
		found := false
		for _, tt := range tests {
			if tt.metricName == gaugeName {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Unexpected gauge created: %s", gaugeName)
		}
	}
}

func TestRecordHistogram(t *testing.T) {
	tests := []struct {
		name       string
		metricName string
		value      float64
		tags       []attribute.KeyValue
	}{
		{
			name:       "basic histogram recording",
			metricName: "test.histogram",
			value:      1.0,
		},
		{
			name:       "histogram with tags",
			metricName: "test.histogram.with.tags",
			value:      99.9,
			tags:       []attribute.KeyValue{attribute.String("operation", "query"), attribute.String("db", "postgres")},
		},
		{
			name:       "zero value histogram",
			metricName: "test.zero.histogram",
			value:      0.0,
		},
		{
			name:       "large value histogram",
			metricName: "test.large.histogram",
			value:      999999.99,
		},
	}

	// Clear the global histograms map before testing to ensure clean state
	float64HistogramsLock.Lock()
	float64Histograms = make(map[string]metric.Float64Histogram, 1000)
	float64HistogramsLock.Unlock()

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			// Test that the function can be called without panicking
			RecordHistogram(ctx, tt.metricName, tt.value, tt.tags...)
			// If we get here, the function executed without errors
		})
	}

	// Verify that histograms were created for each test case
	float64HistogramsLock.RLock()
	defer float64HistogramsLock.RUnlock()

	if len(float64Histograms) != len(tests) {
		t.Errorf("Expected %d float64 histograms, got %d", len(tests), len(float64Histograms))
	}

	// Verify each expected histogram was created
	for _, tt := range tests {
		if histogram, exists := float64Histograms[tt.metricName]; !exists {
			t.Errorf("Expected histogram %s to be created", tt.metricName)
		} else if histogram == nil {
			t.Errorf("Histogram %s was created but is nil", tt.metricName)
		}
	}

	// Verify no unexpected histograms were created
	for histogramName := range float64Histograms {
		found := false
		for _, tt := range tests {
			if tt.metricName == histogramName {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Unexpected histogram created: %s", histogramName)
		}
	}
}

func TestRecordCount(t *testing.T) {
	tests := []struct {
		name       string
		metricName string
		value      int64
		tags       []attribute.KeyValue
	}{
		{
			name:       "basic counter recording",
			metricName: "test.counter",
			value:      1,
		},
		{
			name:       "counter with tags",
			metricName: "test.counter.with.tags",
			value:      100,
			tags:       []attribute.KeyValue{attribute.String("endpoint", "/api/users"), attribute.String("method", "GET")},
		},
		{
			name:       "zero value counter",
			metricName: "test.zero.counter",
			value:      0,
		},
		{
			name:       "large value counter",
			metricName: "test.large.counter",
			value:      999999,
		},
	}

	// Clear the global counters map before testing to ensure clean state
	int64CountersLock.Lock()
	int64Counters = make(map[string]metric.Int64Counter, 1000)
	int64CountersLock.Unlock()

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			// Test that the function can be called without panicking
			RecordCount(ctx, tt.metricName, tt.value, tt.tags...)
			// If we get here, the function executed without errors
		})
	}

	// Verify that counters were created for each test case
	int64CountersLock.RLock()
	defer int64CountersLock.RUnlock()

	if len(int64Counters) != len(tests) {
		t.Errorf("Expected %d int64 counters, got %d", len(tests), len(int64Counters))
	}

	// Verify each expected counter was created
	for _, tt := range tests {
		if counter, exists := int64Counters[tt.metricName]; !exists {
			t.Errorf("Expected counter %s to be created", tt.metricName)
		} else if counter == nil {
			t.Errorf("Counter %s was created but is nil", tt.metricName)
		}
	}

	// Verify no unexpected counters were created
	for counterName := range int64Counters {
		found := false
		for _, tt := range tests {
			if tt.metricName == counterName {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Unexpected counter created: %s", counterName)
		}
	}
}

func TestRecordMetricConcurrent(t *testing.T) {
	const numGoroutines = 10
	const numRecords = 100

	// Test concurrent recording of the same metric
	t.Run("concurrent same metric", func(t *testing.T) {
		metricName := "concurrent.test.metric"
		ctx := context.Background()

		// Use a channel to signal completion
		done := make(chan bool, numGoroutines)

		for i := 0; i < numGoroutines; i++ {
			go func(id int) {
				defer func() { done <- true }()
				for j := 0; j < numRecords; j++ {
					RecordMetric(ctx, metricName, float64(id*numRecords+j))
				}
			}(i)
		}

		// Wait for all goroutines to complete
		for i := 0; i < numGoroutines; i++ {
			<-done
		}
	})

	// Test concurrent recording of different metrics
	t.Run("concurrent different metrics", func(t *testing.T) {
		ctx := context.Background()
		done := make(chan bool, numGoroutines)

		for i := 0; i < numGoroutines; i++ {
			go func(id int) {
				defer func() { done <- true }()
				metricName := "concurrent.metric." + string(rune(id))
				RecordMetric(ctx, metricName, float64(id))
			}(i)
		}

		// Wait for all goroutines to complete
		for i := 0; i < numGoroutines; i++ {
			<-done
		}
	})
}

func TestRecordHistogramConcurrent(t *testing.T) {
	const numGoroutines = 5
	const numRecords = 50

	// Test concurrent recording of the same histogram
	t.Run("concurrent same histogram", func(t *testing.T) {
		metricName := "concurrent.test.histogram"
		ctx := context.Background()
		done := make(chan bool, numGoroutines)

		for i := 0; i < numGoroutines; i++ {
			go func(id int) {
				defer func() { done <- true }()
				for j := 0; j < numRecords; j++ {
					RecordHistogram(ctx, metricName, float64(id*numRecords+j))
				}
			}(i)
		}

		// Wait for all goroutines to complete
		for i := 0; i < numGoroutines; i++ {
			<-done
		}
	})
}

func TestRecordCountConcurrent(t *testing.T) {
	const numGoroutines = 5
	const numRecords = 50

	// Test concurrent recording of the same counter
	t.Run("concurrent same counter", func(t *testing.T) {
		metricName := "concurrent.test.counter"
		ctx := context.Background()
		done := make(chan bool, numGoroutines)

		for i := 0; i < numGoroutines; i++ {
			go func(id int) {
				defer func() { done <- true }()
				for j := 0; j < numRecords; j++ {
					RecordCount(ctx, metricName, int64(id*numRecords+j))
				}
			}(i)
		}

		// Wait for all goroutines to complete
		for i := 0; i < numGoroutines; i++ {
			<-done
		}
	})
}

func TestRecordMetricReuse(t *testing.T) {
	ctx := context.Background()
	metricName := "reuse.test.metric"

	// Clear the global gauges map before testing to ensure clean state
	float64GaugesLock.Lock()
	float64Gauges = make(map[string]metric.Float64Gauge, 1000)
	float64GaugesLock.Unlock()

	// Record the same metric multiple times
	for i := 0; i < 5; i++ {
		RecordMetric(ctx, metricName, float64(i))
	}

	// Verify that only one gauge was created (reuse behavior)
	float64GaugesLock.RLock()
	defer float64GaugesLock.RUnlock()

	if len(float64Gauges) != 1 {
		t.Errorf("Expected 1 float64 gauge for reuse test, got %d", len(float64Gauges))
	}

	if gauge, exists := float64Gauges[metricName]; !exists {
		t.Errorf("Expected gauge %s to be created", metricName)
	} else if gauge == nil {
		t.Errorf("Gauge %s was created but is nil", metricName)
	}
}

func TestRecordHistogramReuse(t *testing.T) {
	ctx := context.Background()
	metricName := "reuse.test.histogram"

	// Clear the global histograms map before testing to ensure clean state
	float64HistogramsLock.Lock()
	float64Histograms = make(map[string]metric.Float64Histogram, 1000)
	float64HistogramsLock.Unlock()

	// Record the same histogram multiple times
	for i := 0; i < 5; i++ {
		RecordHistogram(ctx, metricName, float64(i*10))
	}

	// Verify that only one histogram was created (reuse behavior)
	float64HistogramsLock.RLock()
	defer float64HistogramsLock.RUnlock()

	if len(float64Histograms) != 1 {
		t.Errorf("Expected 1 float64 histogram for reuse test, got %d", len(float64Histograms))
	}

	if histogram, exists := float64Histograms[metricName]; !exists {
		t.Errorf("Expected histogram %s to be created", metricName)
	} else if histogram == nil {
		t.Errorf("Histogram %s was created but is nil", metricName)
	}
}

func TestRecordCountReuse(t *testing.T) {
	ctx := context.Background()
	metricName := "reuse.test.counter"

	// Clear the global counters map before testing to ensure clean state
	int64CountersLock.Lock()
	int64Counters = make(map[string]metric.Int64Counter, 1000)
	int64CountersLock.Unlock()

	// Record the same counter multiple times
	for i := 0; i < 5; i++ {
		RecordCount(ctx, metricName, int64(i*100))
	}

	// Verify that only one counter was created (reuse behavior)
	int64CountersLock.RLock()
	defer int64CountersLock.RUnlock()

	if len(int64Counters) != 1 {
		t.Errorf("Expected 1 int64 counter for reuse test, got %d", len(int64Counters))
	}

	if counter, exists := int64Counters[metricName]; !exists {
		t.Errorf("Expected counter %s to be created", metricName)
	} else if counter == nil {
		t.Errorf("Counter %s was created but is nil", metricName)
	}
}

func TestRecordMetricWithNilContext(t *testing.T) {
	// Test that functions handle nil context gracefully
	RecordMetric(nil, "nil.context.metric", 1.0)
}

func TestRecordHistogramWithNilContext(t *testing.T) {
	// Test that functions handle nil context gracefully
	RecordHistogram(nil, "nil.context.histogram", 1.0)
}

func TestRecordCountWithNilContext(t *testing.T) {
	// Test that functions handle nil context gracefully
	RecordCount(nil, "nil.context.counter", 1)
}

func TestRecordMetricWithEmptyName(t *testing.T) {
	ctx := context.Background()
	// Test that functions handle empty metric names gracefully
	RecordMetric(ctx, "", 1.0)
}

func TestRecordHistogramWithEmptyName(t *testing.T) {
	ctx := context.Background()
	// Test that functions handle empty metric names gracefully
	RecordHistogram(ctx, "", 1.0)
}

func TestRecordCountWithEmptyName(t *testing.T) {
	ctx := context.Background()
	// Test that functions handle empty metric names gracefully
	RecordCount(ctx, "", 1)
}
