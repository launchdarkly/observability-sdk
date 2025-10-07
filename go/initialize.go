package ldobserve

import (
	"context"
	"net/http"

	"github.com/Khan/genqlient/graphql"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.34.0"

	"github.com/launchdarkly/observability-sdk/go/attributes"
	"github.com/launchdarkly/observability-sdk/go/internal/gql"
	"github.com/launchdarkly/observability-sdk/go/internal/logging"
	"github.com/launchdarkly/observability-sdk/go/internal/metadata"
	"github.com/launchdarkly/observability-sdk/go/internal/otel"
)

func getSamplingConfig(projectId string, config observabilityConfig) (*gql.GetSamplingConfigResponse, error) {
	var ctx context.Context
	if config.context != nil {
		ctx = config.context
	} else {
		ctx = context.Background()
	}
	client := graphql.NewClient(config.backendURL, http.DefaultClient)
	return gql.GetSamplingConfig(ctx, client, projectId)
}

func setupOtel(sdkKey string, config observabilityConfig) {
	attributes := []attribute.KeyValue{
		semconv.TelemetryDistroNameKey.String(metadata.InstrumentationName),
		semconv.TelemetryDistroVersionKey.String(metadata.InstrumentationVersion),
		attribute.String(attributes.ProjectIDAttribute, sdkKey),
	}
	if config.environment != "" {
		attributes = append(attributes, semconv.DeploymentEnvironmentName(config.environment))
	}
	if config.serviceName != "" {
		attributes = append(attributes, semconv.ServiceNameKey.String(config.serviceName))
	}
	if config.serviceVersion != "" {
		attributes = append(attributes, semconv.ServiceVersionKey.String(config.serviceVersion))
	}
	if config.debug {
		logging.SetLogger(logging.ConsoleLogger{})
	}

	var s trace.Sampler
	if len(config.samplingRateMap) > 0 {
		s = getSampler(config.samplingRateMap)
	} else {
		s = nil
	}
	otel.SetConfig(otel.Config{
		OtlpEndpoint:           config.otlpEndpoint,
		ResourceAttributes:     attributes,
		Sampler:                s,
		SpanMaxExportBatchSize: config.spanMaxExportBatchSize,
		SpanMaxQueueSize:       config.spanMaxQueueSize,
		LogMaxExportBatchSize:  config.logMaxExportBatchSize,
		LogMaxQueueSize:        config.logMaxQueueSize,
	})
	if !config.manualStart {
		err := otel.StartOTLP()
		if err != nil {
			logging.GetLogger().Errorf("failed to start otel: %v", err)
		}
	}
	go func() {
		cfg, err := getSamplingConfig(sdkKey, config)
		if err != nil {
			logging.GetLogger().Errorf("failed to get sampling config: %v", err)
			return
		}
		logging.GetLogger().Infof("got sampling config: %v", cfg)
		otel.SetSamplingConfig(cfg)
	}()
	if config.context != nil {
		go func() {
			<-config.context.Done()
			otel.Shutdown()
		}()
	}
}

// PreInitialize initializes the observability plugin independently of the
// LaunchDarkly client.
//
// In most situations the plugin should be used instead of this function.
// In cases where the usage of observability needs to be flagged, the plugin
// can be used with the WithManualStart option.
//
// This function is provided for situations where the LaunchDarkly client is not
// readily available, or when observability needs to be initialized earlier than
// the LaunchDarkly client.
func PreInitialize(sdkKey string, opts ...Option) {
	config := defaultConfig()
	for _, opt := range opts {
		opt(&config)
	}

	setupOtel(sdkKey, config)
}
