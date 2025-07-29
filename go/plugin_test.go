package ldobserve

import (
	"context"
	"testing"
)

func TestNewObservabilityPlugin(t *testing.T) {
	expectedServiceName := "test-service"
	expectedServiceVersion := "1.0.0"

	plugin := NewObservabilityPlugin(WithServiceName(expectedServiceName), WithServiceVersion(expectedServiceVersion))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.serviceName != expectedServiceName {
		t.Errorf("Expected service name %s, got %s", expectedServiceName, plugin.config.serviceName)
	}

	if plugin.config.serviceVersion != expectedServiceVersion {
		t.Errorf("Expected service version %s, got %s", expectedServiceVersion, plugin.config.serviceVersion)
	}
}

func TestNewObservabilityPlugin_WithDefaults(t *testing.T) {
	plugin := NewObservabilityPlugin()

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	// Test that default values are empty strings
	if plugin.config.serviceName != "" {
		t.Errorf("Expected empty service name, got %s", plugin.config.serviceName)
	}

	if plugin.config.serviceVersion != "" {
		t.Errorf("Expected empty service version, got %s", plugin.config.serviceVersion)
	}
}

func TestNewObservabilityPlugin_WithPartialOptions(t *testing.T) {
	expectedServiceName := "partial-service"

	plugin := NewObservabilityPlugin(WithServiceName(expectedServiceName))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.serviceName != expectedServiceName {
		t.Errorf("Expected service name %s, got %s", expectedServiceName, plugin.config.serviceName)
	}

	// Service version should be empty when not provided
	if plugin.config.serviceVersion != "" {
		t.Errorf("Expected empty service version, got %s", plugin.config.serviceVersion)
	}
}

func TestNewObservabilityPlugin_WithEnvironment(t *testing.T) {
	expectedEnvironment := "production"

	plugin := NewObservabilityPlugin(WithEnvironment(expectedEnvironment))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.environment != expectedEnvironment {
		t.Errorf("Expected environment %s, got %s", expectedEnvironment, plugin.config.environment)
	}
}

func TestNewObservabilityPlugin_WithBackendURL(t *testing.T) {
	expectedBackendURL := "https://custom-backend.example.com"

	plugin := NewObservabilityPlugin(WithBackendURL(expectedBackendURL))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.backendURL != expectedBackendURL {
		t.Errorf("Expected backend URL %s, got %s", expectedBackendURL, plugin.config.backendURL)
	}
}

func TestNewObservabilityPlugin_WithOTLPEndpoint(t *testing.T) {
	expectedOTLPEndpoint := "https://custom-otlp.example.com:4317"

	plugin := NewObservabilityPlugin(WithOTLPEndpoint(expectedOTLPEndpoint))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.otlpEndpoint != expectedOTLPEndpoint {
		t.Errorf("Expected OTLP endpoint %s, got %s", expectedOTLPEndpoint, plugin.config.otlpEndpoint)
	}
}

func TestNewObservabilityPlugin_WithManualStart(t *testing.T) {
	plugin := NewObservabilityPlugin(WithManualStart())

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if !plugin.config.manualStart {
		t.Error("Expected manual start to be enabled, got false")
	}
}

func TestNewObservabilityPlugin_WithContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	plugin := NewObservabilityPlugin(WithContext(ctx))

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.context != ctx {
		t.Error("Expected context to be set, got different context")
	}
}

func TestNewObservabilityPlugin_WithDebug(t *testing.T) {
	plugin := NewObservabilityPlugin(WithDebug())

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if !plugin.config.debug {
		t.Error("Expected debug to be enabled, got false")
	}
}

func TestNewObservabilityPlugin_WithAllOptions(t *testing.T) {
	expectedServiceName := "full-service"
	expectedServiceVersion := "2.0.0"
	expectedEnvironment := "staging"
	expectedBackendURL := "https://staging-backend.example.com"
	expectedOTLPEndpoint := "https://staging-otlp.example.com:4317"
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	plugin := NewObservabilityPlugin(
		WithServiceName(expectedServiceName),
		WithServiceVersion(expectedServiceVersion),
		WithEnvironment(expectedEnvironment),
		WithBackendURL(expectedBackendURL),
		WithOTLPEndpoint(expectedOTLPEndpoint),
		WithManualStart(),
		WithContext(ctx),
		WithDebug(),
	)

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	if plugin.config.serviceName != expectedServiceName {
		t.Errorf("Expected service name %s, got %s", expectedServiceName, plugin.config.serviceName)
	}

	if plugin.config.serviceVersion != expectedServiceVersion {
		t.Errorf("Expected service version %s, got %s", expectedServiceVersion, plugin.config.serviceVersion)
	}

	if plugin.config.environment != expectedEnvironment {
		t.Errorf("Expected environment %s, got %s", expectedEnvironment, plugin.config.environment)
	}

	if plugin.config.backendURL != expectedBackendURL {
		t.Errorf("Expected backend URL %s, got %s", expectedBackendURL, plugin.config.backendURL)
	}

	if plugin.config.otlpEndpoint != expectedOTLPEndpoint {
		t.Errorf("Expected OTLP endpoint %s, got %s", expectedOTLPEndpoint, plugin.config.otlpEndpoint)
	}

	if !plugin.config.manualStart {
		t.Error("Expected manual start to be enabled, got false")
	}

	if plugin.config.context != ctx {
		t.Error("Expected context to be set, got different context")
	}

	if !plugin.config.debug {
		t.Error("Expected debug to be enabled, got false")
	}
}

func TestNewObservabilityPlugin_DefaultValues(t *testing.T) {
	plugin := NewObservabilityPlugin()

	if plugin == nil {
		t.Fatal("Expected plugin to be created, got nil")
	}

	// Test default values
	if plugin.config.serviceName != "" {
		t.Errorf("Expected empty service name, got %s", plugin.config.serviceName)
	}

	if plugin.config.serviceVersion != "" {
		t.Errorf("Expected empty service version, got %s", plugin.config.serviceVersion)
	}

	if plugin.config.environment != "" {
		t.Errorf("Expected empty environment, got %s", plugin.config.environment)
	}

	if plugin.config.manualStart {
		t.Error("Expected manual start to be disabled by default, got true")
	}

	if plugin.config.context != nil {
		t.Error("Expected context to be nil by default, got non-nil")
	}

	if plugin.config.debug {
		t.Error("Expected debug to be disabled by default, got true")
	}
}
