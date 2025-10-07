package ldobserve

import (
	"context"

	"go.opentelemetry.io/otel/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/defaults"

	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

// The trace SDK exports defaults and we can directly use them, but the log SDK
// does not.
// These are copied from the logs SDK.

// The maximum queue size for the log SDK. If more events than this are queued,
// then events will be dropped until a flush.
const defaultLogMaxQueueSize = 2048

// The maximum number of logs in a single batch export.
const defaultLogMaxExportBatchSize = 512

type observabilityConfig struct {
	serviceName            string
	serviceVersion         string
	environment            string
	backendURL             string
	otlpEndpoint           string
	manualStart            bool
	context                context.Context
	debug                  bool
	samplingRateMap        map[trace.SpanKind]float64
	spanMaxExportBatchSize int
	spanMaxQueueSize       int
	logMaxExportBatchSize  int
	logMaxQueueSize        int
}

func defaultConfig() observabilityConfig {
	return observabilityConfig{
		backendURL:             defaults.DefaultBackendURL,
		otlpEndpoint:           defaults.DefaultOTLPEndpoint,
		spanMaxExportBatchSize: sdktrace.DefaultMaxExportBatchSize,
		spanMaxQueueSize:       sdktrace.DefaultMaxQueueSize,
		logMaxExportBatchSize:  defaultLogMaxExportBatchSize,
		logMaxQueueSize:        defaultLogMaxQueueSize,
	}
}

// Option is a function that configures the observability plugin.
type Option func(*observabilityConfig)

// WithServiceName sets the service name for the observability plugin.
func WithServiceName(serviceName string) Option {
	return func(c *observabilityConfig) {
		c.serviceName = serviceName
	}
}

// WithServiceVersion sets the service version for the observability plugin.
func WithServiceVersion(serviceVersion string) Option {
	return func(c *observabilityConfig) {
		c.serviceVersion = serviceVersion
	}
}

// WithEnvironment sets the environment for the observability plugin.
func WithEnvironment(environment string) Option {
	return func(c *observabilityConfig) {
		c.environment = environment
	}
}

// WithBackendURL sets the backend URL for the observability plugin.
func WithBackendURL(backendURL string) Option {
	return func(c *observabilityConfig) {
		c.backendURL = backendURL
	}
}

// WithOTLPEndpoint sets the OTLP endpoint for the observability plugin.
func WithOTLPEndpoint(otlpEndpoint string) Option {
	return func(c *observabilityConfig) {
		c.otlpEndpoint = otlpEndpoint
	}
}

// WithManualStart indicates that the observability plugin should not start automatically.
// Instead, the plugin should be started manually by calling the Start function.
func WithManualStart() Option {
	return func(c *observabilityConfig) {
		c.manualStart = true
	}
}

// WithContext sets the context for the observability plugin.
// Cancelling the provided context will stop the observability plugin.
// Calling the Shutdown function is recommended and provides a greater level of control.
func WithContext(ctx context.Context) Option {
	return func(c *observabilityConfig) {
		c.context = ctx
	}
}

// WithDebug enables debug mode for the observability plugin.
// This is for use debugging the plugin itself, but should not be needed in regular use.
func WithDebug() Option {
	return func(c *observabilityConfig) {
		c.debug = true
	}
}

// WithSamplingRateMap sets the sampling rate for each span kind.
// This setting can influence the quality of metrics used for experiments and guarded
// releases and should only be adjusted with consultation.
// If the sampling rate is specified for the SpanKindUnspecified kind, then that
// rate will be used for any span kind for which a rate is not specified.
func WithSamplingRateMap(rates map[trace.SpanKind]float64) Option {
	return Option(func(conf *observabilityConfig) {
		conf.samplingRateMap = rates
	})
}

// WithSpanMaxExportBatchSize sets the maximum number of spans that can be exported in a single batch.
// This controls the batch size for span exports to the OTLP endpoint.
func WithSpanMaxExportBatchSize(size int) Option {
	return Option(func(conf *observabilityConfig) {
		conf.spanMaxExportBatchSize = size
	})
}

// WithSpanMaxQueueSize sets the maximum number of spans that can be queued for export.
func WithSpanMaxQueueSize(size int) Option {
	return Option(func(conf *observabilityConfig) {
		conf.spanMaxQueueSize = size
	})
}

// WithLogMaxExportBatchSize sets the maximum number of log records that can be exported in a single batch.
// This controls the batch size for log exports to the OTLP endpoint.
func WithLogMaxExportBatchSize(size int) Option {
	return Option(func(conf *observabilityConfig) {
		conf.logMaxExportBatchSize = size
	})
}

// WithLogMaxQueueSize sets the maximum number of log records that can be queued for export.
func WithLogMaxQueueSize(size int) Option {
	return Option(func(conf *observabilityConfig) {
		conf.logMaxQueueSize = size
	})
}
