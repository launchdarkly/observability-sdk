// package ldobserve provides LaunchDarkly observability functionality for Go applications.
//
// This package is currently in early access preview. APIs are subject to change
// until a 1.x version is released.
package ldobserve

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
