package observability

import (
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
