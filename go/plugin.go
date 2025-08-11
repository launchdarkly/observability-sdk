// package ldobserve provides LaunchDarkly observability functionality for Go applications.
//
// This package is currently in early access preview. APIs are subject to change
// until a 1.x version is released.
package ldobserve

import (
	"context"
	"net/http"

	"github.com/Khan/genqlient/graphql"

	"github.com/launchdarkly/observability-sdk/go/attributes"
	"github.com/launchdarkly/observability-sdk/go/internal/gql"
	"github.com/launchdarkly/observability-sdk/go/internal/logging"
	"github.com/launchdarkly/observability-sdk/go/internal/metadata"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.34.0"

	"github.com/launchdarkly/go-server-sdk/ldotel"
	"github.com/launchdarkly/go-server-sdk/v7/interfaces"
	"github.com/launchdarkly/go-server-sdk/v7/ldhooks"
	"github.com/launchdarkly/go-server-sdk/v7/ldplugins"
	"github.com/launchdarkly/observability-sdk/go/internal/otel"
)

// ObservabilityPlugin represents the LaunchDarkly observability plugin.
type ObservabilityPlugin struct {
	config observabilityConfig
	ldplugins.Unimplemented
}

// NewObservabilityPlugin creates a new observability plugin with the given configuration.
func NewObservabilityPlugin(opts ...Option) *ObservabilityPlugin {
	config := defaultConfig()
	for _, opt := range opts {
		opt(&config)
	}

	return &ObservabilityPlugin{
		config: config,
	}
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

func (p ObservabilityPlugin) getSamplingConfig(projectId string) (*gql.GetSamplingConfigResponse, error) {
	var ctx context.Context
	if p.config.context != nil {
		ctx = p.config.context
	} else {
		ctx = context.Background()
	}
	client := graphql.NewClient(p.config.backendURL, http.DefaultClient)
	return gql.GetSamplingConfig(ctx, client, projectId)
}

// Register registers the observability plugin with the LaunchDarkly client.
func (p ObservabilityPlugin) Register(client interfaces.LDClientInterface, ldmd ldplugins.EnvironmentMetadata) {
	attributes := []attribute.KeyValue{
		semconv.TelemetryDistroNameKey.String(metadata.InstrumentationName),
		semconv.TelemetryDistroVersionKey.String(metadata.InstrumentationVersion),
		attribute.String(attributes.ProjectIDAttribute, ldmd.SdkKey),
	}
	if p.config.environment != "" {
		attributes = append(attributes, semconv.DeploymentEnvironmentName(p.config.environment))
	}
	if p.config.serviceName != "" {
		attributes = append(attributes, semconv.ServiceNameKey.String(p.config.serviceName))
	}
	if p.config.serviceVersion != "" {
		attributes = append(attributes, semconv.ServiceVersionKey.String(p.config.serviceVersion))
	}
	if p.config.debug {
		logging.SetLogger(logging.ConsoleLogger{})
	}

	var s trace.Sampler
	if len(p.config.samplingRateMap) > 0 {
		s = getSampler(p.config.samplingRateMap)
	} else {
		s = nil
	}
	otel.SetConfig(otel.Config{
		OtlpEndpoint:       p.config.otlpEndpoint,
		ResourceAttributes: attributes,
		Sampler:            s,
	})
	if !p.config.manualStart {
		err := otel.StartOTLP()
		if err != nil {
			logging.GetLogger().Errorf("failed to start otel: %v", err)
		}
	}
	go func() {
		cfg, err := p.getSamplingConfig(ldmd.SdkKey)
		if err != nil {
			logging.GetLogger().Errorf("failed to get sampling config: %v", err)
			return
		}
		logging.GetLogger().Infof("got sampling config: %v", cfg)
		otel.SetSamplingConfig(cfg)
	}()
	if p.config.context != nil {
		go func() {
			<-p.config.context.Done()
			otel.Shutdown()
		}()
	}
}
