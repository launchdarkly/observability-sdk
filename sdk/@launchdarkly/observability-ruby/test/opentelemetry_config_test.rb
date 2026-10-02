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

  # Regression: in the lazy-init path the Railtie installs instrumentation at
  # boot with no plugin options, so the trace provider's resource carries the
  # inferred service name. When the client is later created with service_name,
  # configure must update the existing trace resource too — otherwise spans keep
  # the boot identity while logs/metrics use the configured one (mismatch across
  # signals). See configure_traces' boot-installed branch.
  def test_boot_installed_traces_resource_picks_up_configured_service_name
    # Simulate boot-time install: a resource with the inferred/boot service name.
    boot = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      service_name: 'boot-inferred-service'
    )
    boot.install_instrumentation_only
    LaunchDarklyObservability.instance_variable_set(:@instrumentation_installed_at_boot, true)
    assert_equal 'boot-inferred-service', resource_attributes['service.name']

    # Later: the client is created and the plugin configures with its own name.
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      service_name: 'lazy-configured-service',
      enable_logs: false,
      enable_metrics: false
    )
    config.configure

    # Trace spans now report the configured identity, matching logs/metrics.
    assert_equal 'lazy-configured-service', resource_attributes['service.name']
  ensure
    LaunchDarklyObservability.reset_instrumentation_state!
  end

  # Install-time options (custom instrumentations config, disabling traces) can't
  # be applied once instrumentation has attached at boot, so the lazy-init path
  # warns instead of silently dropping them.
  def test_warns_about_options_that_cannot_apply_in_boot_installed_path
    LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint
    ).install_instrumentation_only
    LaunchDarklyObservability.instance_variable_set(:@instrumentation_installed_at_boot, true)

    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      instrumentations: { 'OpenTelemetry::Instrumentation::Redis' => { enabled: false } },
      enable_traces: false,
      enable_logs: false,
      enable_metrics: false
    )

    _out, err = capture_io { config.configure }

    assert_match(/cannot be applied and will be ignored/, err)
    assert_match(/instrumentations/, err)
    assert_match(/enable_traces: false/, err)
  ensure
    LaunchDarklyObservability.reset_instrumentation_state!
  end

  def test_does_not_warn_about_options_when_not_installed_at_boot
    LaunchDarklyObservability.reset_instrumentation_state!

    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      instrumentations: { 'OpenTelemetry::Instrumentation::Redis' => { enabled: false } },
      enable_logs: false,
      enable_metrics: false
    )

    _out, err = capture_io { config.configure }

    refute_match(/will be ignored/, err)
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

  def test_configure_sets_up_logger_provider_by_default
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_metrics: false
    )

    config.configure

    refute_nil config.logger_provider
    assert_kind_of OpenTelemetry::SDK::Logs::LoggerProvider, config.logger_provider
  end

  def test_configure_skips_logger_provider_when_disabled
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_logs: false,
      enable_metrics: false
    )

    config.configure

    assert_nil config.logger_provider
  end

  def test_logger_provider_sets_global_provider
    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: @project_id,
      otlp_endpoint: @otlp_endpoint,
      environment: @environment,
      enable_metrics: false
    )

    config.configure

    return unless OpenTelemetry.respond_to?(:logger_provider)
    assert_kind_of OpenTelemetry::SDK::Logs::LoggerProvider, OpenTelemetry.logger_provider
  end

  private

  def resource_attributes
    resource = OpenTelemetry.tracer_provider.resource
    resource.send(:attributes)
  end
end
