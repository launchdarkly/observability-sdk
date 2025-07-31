// Package observability provides LaunchDarkly observability functionality for Go applications.
//
// This package is currently in early access preview. APIs are subject to change
// until a 1.x version is released.
package observability

// observabilityConfig holds configuration for the observability plugin.
type observabilityConfig struct {
	serviceName    string
	serviceVersion string
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

// ObservabilityPlugin represents the LaunchDarkly observability plugin.
type ObservabilityPlugin struct {
	config observabilityConfig
}

// NewObservabilityPlugin creates a new observability plugin with the given configuration.
func NewObservabilityPlugin(opts ...Option) *ObservabilityPlugin {
	config := observabilityConfig{}
	for _, opt := range opts {
		opt(&config)
	}

	return &ObservabilityPlugin{
		config: config,
	}
}
