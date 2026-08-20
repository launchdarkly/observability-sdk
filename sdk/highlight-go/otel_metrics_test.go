package highlight

import (
	"context"
	"fmt"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.opentelemetry.io/otel/metric"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
)

// newTestMeter points the package-level instruments at a manual reader so a test can
// inspect exactly what would be exported, and gives each test its own instrument
// caches since instruments are memoized for the process lifetime.
func newTestMeter(t *testing.T) *sdkmetric.ManualReader {
	t.Helper()

	reader := sdkmetric.NewManualReader()
	provider := sdkmetric.NewMeterProvider(sdkmetric.WithReader(reader))

	prevMeter := defaultMeter
	prevGauges, prevHistograms, prevCounters := float64Gauges, float64Histograms, int64Counters

	defaultMeter = provider.Meter("test")
	float64Gauges = newInstrumentCache[metric.Float64Gauge]("float64 gauge")
	float64Histograms = newInstrumentCache[metric.Float64Histogram]("float64 histogram")
	int64Counters = newInstrumentCache[metric.Int64Counter]("int64 counter")

	t.Cleanup(func() {
		defaultMeter = prevMeter
		float64Gauges, float64Histograms, int64Counters = prevGauges, prevHistograms, prevCounters
	})

	return reader
}

func collectMetric(t *testing.T, reader *sdkmetric.ManualReader, name string) metricdata.Metrics {
	t.Helper()

	var rm metricdata.ResourceMetrics
	require.NoError(t, reader.Collect(context.Background(), &rm))

	for _, scope := range rm.ScopeMetrics {
		for _, m := range scope.Metrics {
			if m.Name == name {
				return m
			}
		}
	}

	t.Fatalf("metric %q was not collected", name)
	return metricdata.Metrics{}
}

func histogramPoint(t *testing.T, m metricdata.Metrics) metricdata.HistogramDataPoint[float64] {
	t.Helper()

	hist, ok := m.Data.(metricdata.Histogram[float64])
	require.True(t, ok, "expected a float64 histogram, got %T", m.Data)
	require.Len(t, hist.DataPoints, 1)
	return hist.DataPoints[0]
}

// The OTEL default boundaries top out at 10000 and are shaped for millisecond
// latencies. A metric on a different scale collapses into the +Inf overflow bucket,
// which is what RecordHistogramWithOptions exists to avoid.
func TestRecordHistogram_DefaultBoundsOverflowOnLargeValues(t *testing.T) {
	reader := newTestMeter(t)

	// Three values spanning hours to decades — all distinguishable, none below 10000.
	for _, value := range []float64{20000, 86400, 1787086902} {
		RecordHistogram(context.Background(), "seconds.of.skew", value)
	}

	point := histogramPoint(t, collectMetric(t, reader, "seconds.of.skew"))
	require.NotEmpty(t, point.Bounds)
	assert.Equal(t, float64(10000), point.Bounds[len(point.Bounds)-1], "OTEL's default boundaries end at 10000")
	assert.Equal(t, uint64(3), point.BucketCounts[len(point.BucketCounts)-1],
		"every value collapses into the unbounded +Inf bucket, so no quantile is computable")
}

func TestRecordHistogramWithOptions_AppliesExplicitBounds(t *testing.T) {
	reader := newTestMeter(t)
	bounds := []float64{7200, 21600, 86400, 604800}

	for _, value := range []float64{7300, 100000, 2000000} {
		RecordHistogramWithOptions(context.Background(), "seconds.of.skew", value, nil,
			metric.WithExplicitBucketBoundaries(bounds...))
	}

	point := histogramPoint(t, collectMetric(t, reader, "seconds.of.skew"))
	assert.Equal(t, bounds, point.Bounds)
	// One observation per bucket above the first boundary, none below it.
	assert.Equal(t, []uint64{0, 1, 0, 1, 1}, point.BucketCounts)
	assert.Equal(t, uint64(3), point.Count)
}

func TestRecordHistogramWithOptions_SetsUnitAndDescription(t *testing.T) {
	reader := newTestMeter(t)

	RecordHistogramWithOptions(context.Background(), "seconds.of.skew", 7300, nil,
		metric.WithUnit("s"),
		metric.WithDescription("Distance between an ingested timestamp and server time."))

	m := collectMetric(t, reader, "seconds.of.skew")
	assert.Equal(t, "s", m.Unit)
	assert.Equal(t, "Distance between an ingested timestamp and server time.", m.Description)
}

// Instruments are built once and memoized, so the first record for a name decides
// its configuration. Callers must pass the same options everywhere.
func TestRecordHistogramWithOptions_FirstCallDecidesConfiguration(t *testing.T) {
	reader := newTestMeter(t)
	first := []float64{7200, 86400}

	RecordHistogramWithOptions(context.Background(), "seconds.of.skew", 7300, nil,
		metric.WithExplicitBucketBoundaries(first...), metric.WithUnit("s"))
	RecordHistogramWithOptions(context.Background(), "seconds.of.skew", 7400, nil,
		metric.WithExplicitBucketBoundaries(1, 2, 3), metric.WithUnit("ms"))

	m := collectMetric(t, reader, "seconds.of.skew")
	assert.Equal(t, "s", m.Unit)
	assert.Equal(t, first, histogramPoint(t, m).Bounds)
	assert.Equal(t, uint64(2), histogramPoint(t, m).Count, "both observations still record")
}

func TestRecordCountWithOptions_SetsUnitAndDescription(t *testing.T) {
	reader := newTestMeter(t)

	RecordCountWithOptions(context.Background(), "timestamps.clamped", 2, nil,
		metric.WithUnit("{clamp}"), metric.WithDescription("Ingested timestamps rewritten to server time."))

	m := collectMetric(t, reader, "timestamps.clamped")
	assert.Equal(t, "{clamp}", m.Unit)
	assert.Equal(t, "Ingested timestamps rewritten to server time.", m.Description)

	sum, ok := m.Data.(metricdata.Sum[int64])
	require.True(t, ok, "expected an int64 sum, got %T", m.Data)
	require.Len(t, sum.DataPoints, 1)
	assert.Equal(t, int64(2), sum.DataPoints[0].Value)
}

func TestRecordMetricWithOptions_SetsUnitAndDescription(t *testing.T) {
	reader := newTestMeter(t)

	RecordMetricWithOptions(context.Background(), "queue.depth", 7, nil,
		metric.WithUnit("{item}"), metric.WithDescription("Pending items."))

	m := collectMetric(t, reader, "queue.depth")
	assert.Equal(t, "{item}", m.Unit)
	assert.Equal(t, "Pending items.", m.Description)
}

// The instrument caches are read on every record and written on first use of each
// name. Under -race this fails if the read is not synchronized with the write.
func TestRecordInstruments_ConcurrentFirstUseIsSafe(t *testing.T) {
	reader := newTestMeter(t)

	const names = 24
	const writersPerName = 8

	var wg sync.WaitGroup
	for i := range names {
		for range writersPerName {
			wg.Add(1)
			go func() {
				defer wg.Done()
				ctx := context.Background()
				RecordHistogram(ctx, fmt.Sprintf("histogram.%d", i), 1)
				RecordCount(ctx, fmt.Sprintf("counter.%d", i), 1)
				RecordMetric(ctx, fmt.Sprintf("gauge.%d", i), 1)
			}()
		}
	}
	wg.Wait()

	// Every name resolved to a single instrument, so no measurement was dropped on
	// an instrument that lost a registration race.
	for i := range names {
		hist := histogramPoint(t, collectMetric(t, reader, fmt.Sprintf("histogram.%d", i)))
		assert.Equal(t, uint64(writersPerName), hist.Count, "histogram.%d", i)
	}
}
