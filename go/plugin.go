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
	"github.com/launchdarkly/observability-sdk/go/internal/otel"
	"go.opentelemetry.io/otel/sdk/trace"
)

// ObservabilityPlugin represents the LaunchDarkly observability plugin.
type ObservabilityPlugin struct {
	config observabilityConfig
	ldplugins.Unimplemented
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

func (p ObservabilityPlugin) GetHooks(_ ldplugins.EnvironmentMetadata) []ldhooks.Hook {
	return []ldhooks.Hook{
		ldotel.NewTracingHook(ldotel.WithValue()),
	}
}

func (p ObservabilityPlugin) Metadata() ldplugins.Metadata {
	return ldplugins.NewMetadata("launchdarkly-observability")
}

type allSampler struct{}

// Description implements trace.Sampler.
func (a *allSampler) Description() string {
	return "samples all traces"
}

// ShouldSample implements trace.Sampler.
func (a *allSampler) ShouldSample(parameters trace.SamplingParameters) trace.SamplingResult {
	return trace.SamplingResult{
		Decision: trace.RecordAndSample,
	}
}

func (p ObservabilityPlugin) Register(client interfaces.LDClientInterface, metadata ldplugins.EnvironmentMetadata) {
	var s trace.Sampler = &allSampler{}
	otel.StartOTLP(otel.Config{
		OtlpEndpoint: "http://localhost:4318",
	}, s)
	// TODO implement
}
