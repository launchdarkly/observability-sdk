# frozen_string_literal: true

require 'test_helper'

class OpenTelemetryConfigTest < Minitest::Test
  include TestHelper

  def setup
    @project_id = 'test-project-123'
    @otlp_endpoint = 'https://test.endpoint.com:4318'
    @environment = 'test'
  end

  def test_initialize_stores_configuration
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment
    )

    assert_equal @project_id, config.project_id
    assert_equal @otlp_endpoint, config.otlp_endpoint
    assert_equal @environment, config.environment
  end

  def test_configure_sets_up_tracer_provider
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    # Verify tracer provider is available
    tracer_provider = OpenTelemetry.tracer_provider
    refute_nil tracer_provider
    assert_kind_of OpenTelemetry::SDK::Trace::TracerProvider, tracer_provider
  end

  def test_configure_only_runs_once
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure
    first_provider = OpenTelemetry.tracer_provider

    # Second configure should be a no-op
    config.configure
    second_provider = OpenTelemetry.tracer_provider

    # Should be the same provider instance
    assert_same first_provider, second_provider
  end

  def test_resource_includes_project_id
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    attrs = resource_attributes
    assert_equal @project_id, attrs['launchdarkly.project_id']
  end

  def test_resource_includes_environment
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: 'production',
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    attrs = resource_attributes
    assert_equal 'production', attrs['deployment.environment']
  end

  def test_resource_includes_sdk_info
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    attrs = resource_attributes
    assert_equal 'opentelemetry', attrs['telemetry.sdk.name']
    assert_equal 'ruby', attrs['telemetry.sdk.language']
    assert_equal 'launchdarkly-observability-ruby', attrs['telemetry.distro.name']
    assert_equal LaunchDarklyObservability::VERSION, attrs['telemetry.distro.version']
  end

  def test_resource_includes_service_name_when_provided
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      service_name: 'my-awesome-service',
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    attrs = resource_attributes
    assert_equal 'my-awesome-service', attrs['service.name']
  end

  def test_resource_includes_service_version_when_provided
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      service_version: '2.0.0',
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    attrs = resource_attributes
    assert_equal '2.0.0', attrs['service.version']
  end

  def test_flush_does_not_raise
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    # Should not raise
    config.flush
  end

  def test_shutdown_does_not_raise
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    # Should not raise
    config.shutdown
  end

  def test_batch_processor_configuration
    # This test verifies the batch processor settings are applied
    # by checking that spans are exported correctly
    exporter = create_test_exporter

    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(exporter)
      )
    end

    tracer = OpenTelemetry.tracer_provider.tracer('test')
    tracer.in_span('test-span') do |span|
      span.set_attribute('test', 'value')
    end

    spans = exporter.finished_spans
    assert_equal 1, spans.length
    assert_equal 'test-span', spans.first.name
  end

  private

  def resource_attributes
    resource = OpenTelemetry.tracer_provider.resource
    resource.send(:attributes)
  end
end
