// package ldobserve provides LaunchDarkly observability functionality for Go applications.
//
// This package is currently in early access preview. APIs are subject to change
// until a 1.x version is released.
package ldobserve

import (
	"github.com/launchdarkly/go-server-sdk/ldotel"
	"github.com/launchdarkly/go-server-sdk/v7/interfaces"
	"github.com/launchdarkly/go-server-sdk/v7/ldhooks"
	"github.com/launchdarkly/go-server-sdk/v7/ldplugins"
)

// ObservabilityPlugin represents the LaunchDarkly observability plugin.
type ObservabilityPlugin struct {
	config *observabilityConfig
	ldplugins.Unimplemented
}

// NewObservabilityPlugin creates a new observability plugin with the given configuration.
func NewObservabilityPlugin(opts ...Option) *ObservabilityPlugin {
	config := defaultConfig()
	for _, opt := range opts {
		opt(&config)
	}

	return &ObservabilityPlugin{
		config: &config,
	}
}

// NewObservabilityPluginWithoutInit creates a new observability plugin without
// for performing initialization.
// This method generally does not need to be used, and should only be used in
// conjunction with InitializeWithoutPlugin.
func NewObservabilityPluginWithoutInit() *ObservabilityPlugin {
	return &ObservabilityPlugin{}
}

// GetHooks returns the hooks for the observability plugin.
func (p ObservabilityPlugin) GetHooks(_ ldplugins.EnvironmentMetadata) []ldhooks.Hook {
	return []ldhooks.Hook{
		ldotel.NewTracingHook(ldotel.WithValue()),
	}
}

// Metadata returns the metadata for the observability plugin.
func (p ObservabilityPlugin) Metadata() ldplugins.Metadata {
	return ldplugins.NewMetadata("launchdarkly-observability")
}

// Register registers the observability plugin with the LaunchDarkly client.
func (p ObservabilityPlugin) Register(client interfaces.LDClientInterface, ldmd ldplugins.EnvironmentMetadata) {
	if p.config == nil {
		return
	}
	setupOtel(ldmd.SdkKey, *p.config)
}
