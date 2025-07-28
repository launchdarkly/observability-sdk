package ldobserve



// observabilityConfig holds configuration for the observability plugin.
type observabilityConfig struct {
	serviceName    string
	serviceVersion string
	environment    string
	backendURL     string
	otlpEndpoint   string
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
