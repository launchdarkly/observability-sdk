package ldobserve

import (
	"context"

	"go.opentelemetry.io/otel/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/defaults"
)

type observabilityConfig struct {
	serviceName     string
	serviceVersion  string
	environment     string
	backendURL      string
	otlpEndpoint    string
	manualStart     bool
	context         context.Context
	debug           bool
	samplingRateMap map[trace.SpanKind]float64
}

func defaultConfig() observabilityConfig {
	return observabilityConfig{
		backendURL:   defaults.DefaultBackendURL,
		otlpEndpoint: defaults.DefaultOTLPEndpoint,
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
func WithSamplingRateMap(rates map[trace.SpanKind]float64) Option {
	return Option(func(conf *observabilityConfig) {
		conf.samplingRateMap = rates
	})
}
