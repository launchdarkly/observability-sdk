# frozen_string_literal: true

require 'test_helper'

class PluginTest < Minitest::Test
  include TestHelper

  def setup
    @project_id = 'test-project-123'
  end

  def test_initialize_with_required_params
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)

    assert_equal @project_id, plugin.project_id
    assert_equal LaunchDarklyObservability::DEFAULT_ENDPOINT, plugin.otlp_endpoint
    assert_equal '', plugin.environment
  end

  def test_initialize_with_all_params
    endpoint = 'https://custom.endpoint.com:4318'
    environment = 'production'

    plugin = LaunchDarklyObservability::Plugin.new(
      project_id: @project_id,
      otlp_endpoint: endpoint,
      environment: environment,
      service_name: 'my-service',
      service_version: '1.0.0'
    )

    assert_equal @project_id, plugin.project_id
    assert_equal endpoint, plugin.otlp_endpoint
    assert_equal environment, plugin.environment
    assert_equal 'my-service', plugin.options[:service_name]
    assert_equal '1.0.0', plugin.options[:service_version]
  end

  def test_metadata_returns_plugin_metadata
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)
    metadata = plugin.metadata

    assert_instance_of LaunchDarkly::Interfaces::Plugins::PluginMetadata, metadata
    assert_equal 'launchdarkly-observability', metadata.name
  end

  def test_get_hooks_returns_evaluation_hook
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)
    hooks = plugin.get_hooks(nil)

    assert_equal 1, hooks.length
    assert_instance_of LaunchDarklyObservability::Hook, hooks.first
  end

  def test_includes_plugin_mixin
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)

    assert plugin.is_a?(LaunchDarkly::Interfaces::Plugins::Plugin)
  end

  def test_not_registered_initially
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)

    refute plugin.registered?
  end

  def test_default_options
    plugin = LaunchDarklyObservability::Plugin.new(project_id: @project_id)

    assert plugin.options[:enable_traces]
    assert plugin.options[:enable_logs]
    assert plugin.options[:enable_metrics]
    assert_nil plugin.options[:service_name]
    assert_nil plugin.options[:service_version]
    assert_equal({}, plugin.options[:instrumentations])
  end

  def test_environment_converted_to_string
    plugin = LaunchDarklyObservability::Plugin.new(
      project_id: @project_id,
      environment: :production
    )

    assert_equal 'production', plugin.environment
  end

  def test_options_can_disable_signals
    plugin = LaunchDarklyObservability::Plugin.new(
      project_id: @project_id,
      enable_traces: true,
      enable_logs: false,
      enable_metrics: false
    )

    assert plugin.options[:enable_traces]
    refute plugin.options[:enable_logs]
    refute plugin.options[:enable_metrics]
  end

  def test_custom_instrumentations
    custom_config = {
      'OpenTelemetry::Instrumentation::Rails' => { enabled: false }
    }

    plugin = LaunchDarklyObservability::Plugin.new(
      project_id: @project_id,
      instrumentations: custom_config
    )

    assert_equal custom_config, plugin.options[:instrumentations]
  end
end
