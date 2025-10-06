package ldobserve

import (
	"context"
	"testing"

	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

func TestDefaultConfig(t *testing.T) {
	config := defaultConfig()

	// Test default values
	if config.backendURL != "https://pub.observability.app.launchdarkly.com" {
		t.Errorf("Expected default backendURL to be 'https://pub.observability.app.launchdarkly.com', got '%s'", config.backendURL)
	}

	if config.otlpEndpoint != "https://otel.observability.app.launchdarkly.com:4318" {
		t.Errorf("Expected default otlpEndpoint to be 'https://otel.observability.app.launchdarkly.com:4318', got '%s'", config.otlpEndpoint)
	}

	// Values are hard-coded in this test such that we know when they are changed. If we end up working with multiple
	// otel versions, having different defaults, then we may need to adjust.
	if config.spanMaxExportBatchSize != 512 {
		t.Errorf("Expected default spanMaxExportBatchSize to be %d, got %d", sdktrace.DefaultMaxExportBatchSize, config.spanMaxExportBatchSize)
	}

	if config.spanMaxQueueSize != 2048 {
		t.Errorf("Expected default spanMaxQueueSize to be %d, got %d", sdktrace.DefaultMaxQueueSize, config.spanMaxQueueSize)
	}

	if config.logMaxExportBatchSize != 2048 {
		t.Errorf("Expected default logMaxExportBatchSize to be %d, got %d", defaultLogMaxQueueSize, config.logMaxExportBatchSize)
	}

	if config.logMaxQueueSize != 512 {
		t.Errorf("Expected default logMaxQueueSize to be %d, got %d", defaultLogMaxExportBatchSize, config.logMaxQueueSize)
	}

	// Test that optional fields are zero values
	if config.serviceName != "" {
		t.Errorf("Expected default serviceName to be empty, got '%s'", config.serviceName)
	}

	if config.serviceVersion != "" {
		t.Errorf("Expected default serviceVersion to be empty, got '%s'", config.serviceVersion)
	}

	if config.environment != "" {
		t.Errorf("Expected default environment to be empty, got '%s'", config.environment)
	}

	if config.manualStart != false {
		t.Errorf("Expected default manualStart to be false, got %t", config.manualStart)
	}

	if config.context != nil {
		t.Errorf("Expected default context to be nil, got %v", config.context)
	}

	if config.debug != false {
		t.Errorf("Expected default debug to be false, got %t", config.debug)
	}

	if config.samplingRateMap != nil {
		t.Errorf("Expected default samplingRateMap to be nil, got %v", config.samplingRateMap)
	}
}

func TestWithServiceName(t *testing.T) {
	config := defaultConfig()
	serviceName := "test-service"

	WithServiceName(serviceName)(&config)

	if config.serviceName != serviceName {
		t.Errorf("Expected serviceName to be '%s', got '%s'", serviceName, config.serviceName)
	}
}

func TestWithServiceVersion(t *testing.T) {
	config := defaultConfig()
	serviceVersion := "1.0.0"

	WithServiceVersion(serviceVersion)(&config)

	if config.serviceVersion != serviceVersion {
		t.Errorf("Expected serviceVersion to be '%s', got '%s'", serviceVersion, config.serviceVersion)
	}
}

func TestWithEnvironment(t *testing.T) {
	config := defaultConfig()
	environment := "production"

	WithEnvironment(environment)(&config)

	if config.environment != environment {
		t.Errorf("Expected environment to be '%s', got '%s'", environment, config.environment)
	}
}

func TestWithBackendURL(t *testing.T) {
	config := defaultConfig()
	backendURL := "https://custom-backend.com"

	WithBackendURL(backendURL)(&config)

	if config.backendURL != backendURL {
		t.Errorf("Expected backendURL to be '%s', got '%s'", backendURL, config.backendURL)
	}
}

func TestWithOTLPEndpoint(t *testing.T) {
	config := defaultConfig()
	otlpEndpoint := "https://custom-otlp.com:4318"

	WithOTLPEndpoint(otlpEndpoint)(&config)

	if config.otlpEndpoint != otlpEndpoint {
		t.Errorf("Expected otlpEndpoint to be '%s', got '%s'", otlpEndpoint, config.otlpEndpoint)
	}
}

func TestWithManualStart(t *testing.T) {
	config := defaultConfig()

	WithManualStart()(&config)

	if config.manualStart != true {
		t.Errorf("Expected manualStart to be true, got %t", config.manualStart)
	}
}

func TestWithContext(t *testing.T) {
	config := defaultConfig()
	ctx := context.Background()

	WithContext(ctx)(&config)

	if config.context != ctx {
		t.Errorf("Expected context to be %v, got %v", ctx, config.context)
	}
}

func TestWithDebug(t *testing.T) {
	config := defaultConfig()

	WithDebug()(&config)

	if config.debug != true {
		t.Errorf("Expected debug to be true, got %t", config.debug)
	}
}

func TestWithSamplingRateMap(t *testing.T) {
	config := defaultConfig()
	rates := map[trace.SpanKind]float64{
		trace.SpanKindClient: 0.5,
		trace.SpanKindServer: 0.8,
	}

	WithSamplingRateMap(rates)(&config)

	if config.samplingRateMap == nil {
		t.Error("Expected samplingRateMap to be set, got nil")
		return
	}

	if len(config.samplingRateMap) != len(rates) {
		t.Errorf("Expected samplingRateMap to have %d entries, got %d", len(rates), len(config.samplingRateMap))
	}

	for kind, expectedRate := range rates {
		if actualRate, exists := config.samplingRateMap[kind]; !exists {
			t.Errorf("Expected samplingRateMap to contain %v, but it doesn't", kind)
		} else if actualRate != expectedRate {
			t.Errorf("Expected samplingRateMap[%v] to be %f, got %f", kind, expectedRate, actualRate)
		}
	}
}

func TestWithSpanMaxExportBatchSize(t *testing.T) {
	config := defaultConfig()
	size := 1000

	WithSpanMaxExportBatchSize(size)(&config)

	if config.spanMaxExportBatchSize != size {
		t.Errorf("Expected spanMaxExportBatchSize to be %d, got %d", size, config.spanMaxExportBatchSize)
	}
}

func TestWithSpanMaxQueueSize(t *testing.T) {
	config := defaultConfig()
	size := 2000

	WithSpanMaxQueueSize(size)(&config)

	if config.spanMaxQueueSize != size {
		t.Errorf("Expected spanMaxQueueSize to be %d, got %d", size, config.spanMaxQueueSize)
	}
}

func TestWithLogMaxExportBatchSize(t *testing.T) {
	config := defaultConfig()
	size := 1000

	WithLogMaxExportBatchSize(size)(&config)

	if config.logMaxExportBatchSize != size {
		t.Errorf("Expected logMaxExportBatchSize to be %d, got %d", size, config.logMaxExportBatchSize)
	}
}

func TestWithLogMaxQueueSize(t *testing.T) {
	config := defaultConfig()
	size := 2000

	WithLogMaxQueueSize(size)(&config)

	if config.logMaxQueueSize != size {
		t.Errorf("Expected logMaxQueueSize to be %d, got %d", size, config.logMaxQueueSize)
	}
}

func TestMultipleOptions(t *testing.T) {
	config := defaultConfig()
	serviceName := "multi-test-service"
	serviceVersion := "2.0.0"
	environment := "staging"
	backendURL := "https://multi-backend.com"
	otlpEndpoint := "https://multi-otlp.com:4318"
	ctx := context.Background()
	rates := map[trace.SpanKind]float64{
		trace.SpanKindClient: 0.3,
	}

	// Apply multiple options
	WithServiceName(serviceName)(&config)
	WithServiceVersion(serviceVersion)(&config)
	WithEnvironment(environment)(&config)
	WithBackendURL(backendURL)(&config)
	WithOTLPEndpoint(otlpEndpoint)(&config)
	WithManualStart()(&config)
	WithContext(ctx)(&config)
	WithDebug()(&config)
	WithSamplingRateMap(rates)(&config)
	WithSpanMaxExportBatchSize(500)(&config)
	WithSpanMaxQueueSize(1000)(&config)
	WithLogMaxExportBatchSize(250)(&config)
	WithLogMaxQueueSize(500)(&config)

	// Verify all options were applied
	if config.serviceName != serviceName {
		t.Errorf("Expected serviceName to be '%s', got '%s'", serviceName, config.serviceName)
	}
	if config.serviceVersion != serviceVersion {
		t.Errorf("Expected serviceVersion to be '%s', got '%s'", serviceVersion, config.serviceVersion)
	}
	if config.environment != environment {
		t.Errorf("Expected environment to be '%s', got '%s'", environment, config.environment)
	}
	if config.backendURL != backendURL {
		t.Errorf("Expected backendURL to be '%s', got '%s'", backendURL, config.backendURL)
	}
	if config.otlpEndpoint != otlpEndpoint {
		t.Errorf("Expected otlpEndpoint to be '%s', got '%s'", otlpEndpoint, config.otlpEndpoint)
	}
	if config.manualStart != true {
		t.Errorf("Expected manualStart to be true, got %t", config.manualStart)
	}
	if config.context != ctx {
		t.Errorf("Expected context to be %v, got %v", ctx, config.context)
	}
	if config.debug != true {
		t.Errorf("Expected debug to be true, got %t", config.debug)
	}
	if config.samplingRateMap == nil {
		t.Error("Expected samplingRateMap to be set, got nil")
	}
	if config.spanMaxExportBatchSize != 500 {
		t.Errorf("Expected spanMaxExportBatchSize to be 500, got %d", config.spanMaxExportBatchSize)
	}
	if config.spanMaxQueueSize != 1000 {
		t.Errorf("Expected spanMaxQueueSize to be 1000, got %d", config.spanMaxQueueSize)
	}
	if config.logMaxExportBatchSize != 250 {
		t.Errorf("Expected logMaxExportBatchSize to be 250, got %d", config.logMaxExportBatchSize)
	}
	if config.logMaxQueueSize != 500 {
		t.Errorf("Expected logMaxQueueSize to be 500, got %d", config.logMaxQueueSize)
	}
}

func TestEmptySamplingRateMap(t *testing.T) {
	config := defaultConfig()
	emptyRates := map[trace.SpanKind]float64{}

	WithSamplingRateMap(emptyRates)(&config)

	if config.samplingRateMap == nil {
		t.Error("Expected samplingRateMap to be set even when empty, got nil")
	}

	if len(config.samplingRateMap) != 0 {
		t.Errorf("Expected samplingRateMap to be empty, got %d entries", len(config.samplingRateMap))
	}
}

func TestNilSamplingRateMap(t *testing.T) {
	config := defaultConfig()

	WithSamplingRateMap(nil)(&config)

	if config.samplingRateMap != nil {
		t.Errorf("Expected samplingRateMap to be nil when nil is passed, got %v", config.samplingRateMap)
	}
}
