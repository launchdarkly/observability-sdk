# frozen_string_literal: true

require 'test_helper'

# Integration tests that verify the full flow from LaunchDarkly client
# through to OpenTelemetry span export.
#
# These tests use a real (test-configured) LaunchDarkly client with
# test data to verify the complete instrumentation pipeline.
#
class IntegrationTest < Minitest::Test
  include TestHelper

  def setup
    @exporter = create_test_exporter
    @project_id = 'integration-test-project'

    # Create plugin with test configuration
    @plugin = LaunchDarklyObservability::Plugin.new(
      project_id: @project_id,
      otlp_endpoint: 'http://localhost:4318',
      environment: 'test',
      service_name: 'integration-test-service',
      service_version: '1.0.0',
      enable_logs: false,
      enable_metrics: false
    )
  end

  def teardown
    @exporter.reset
    @client&.close
  end

  def test_full_evaluation_creates_span
    td = LaunchDarkly::Integrations::TestData.data_source
    td.update(td.flag('test-flag').variations(false, true).variation_for_all(1))

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    result = @client.variation('test-flag', context, false)

    assert result, 'Expected flag to return true'

    # Verify span was created
    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'evaluation', span.name

    # Semantic convention attributes
    assert_equal 'test-flag', span.attributes['feature_flag.key']
    assert_equal 'user-123', span.attributes['feature_flag.context.id']
    assert_equal 'true', span.attributes['feature_flag.result.value']
    assert_equal '1', span.attributes['feature_flag.result.variant']
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']

    # LaunchDarkly-specific attributes
    assert_equal 'user', span.attributes['launchdarkly.context.kind']
    assert_equal 'user-123', span.attributes['launchdarkly.context.key']
  end

  def test_variation_detail_creates_span_with_reason
    td = LaunchDarkly::Integrations::TestData.data_source
    td.update(td.flag('detail-flag').variations('off', 'on').variation_for_all(1))

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    detail = @client.variation_detail('detail-flag', context, 'default')

    assert_equal 'on', detail.value

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 'evaluation', span.name

    assert_equal 'default', span.attributes['feature_flag.result.reason']
    assert_equal 'FALLTHROUGH', span.attributes['launchdarkly.reason.kind']
  end

  def test_error_evaluation_creates_error_span
    td = LaunchDarkly::Integrations::TestData.data_source

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'user-789', kind: 'user' })
    detail = @client.variation_detail('nonexistent-flag', context, 'default')

    assert_equal 'default', detail.value
    assert_equal :ERROR, detail.reason.kind

    spans = @exporter.finished_spans
    span = spans.first

    # Semantic convention attributes
    assert_equal 'flag_not_found', span.attributes['error.type']
    assert_includes span.attributes['error.message'], 'FLAG_NOT_FOUND'

    # LaunchDarkly-specific attributes
    assert_equal 'FLAG_NOT_FOUND', span.attributes['launchdarkly.reason.error_kind']

    assert_equal OpenTelemetry::Trace::Status::ERROR, span.status.code
  end

  def test_multiple_evaluations_create_multiple_spans
    td = LaunchDarkly::Integrations::TestData.data_source
    td.update(td.flag('flag-a').variations(false, true).variation_for_all(1))
    td.update(td.flag('flag-b').variations('a', 'b', 'c').variation_for_all(2))
    td.update(td.flag('flag-c').variations(0, 1, 2).variation_for_all(1))

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'multi-user', kind: 'user' })

    @client.variation('flag-a', context, false)
    @client.variation('flag-b', context, 'default')
    @client.variation('flag-c', context, 0)

    spans = @exporter.finished_spans
    assert_equal 3, spans.length

    # All spans should have semantic convention attributes
    flag_keys = spans.map { |s| s.attributes['feature_flag.key'] }
    assert_includes flag_keys, 'flag-a'
    assert_includes flag_keys, 'flag-b'
    assert_includes flag_keys, 'flag-c'

    # Verify all spans have provider name
    spans.each do |span|
      assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
    end
  end

  def test_multi_context_evaluation
    td = LaunchDarkly::Integrations::TestData.data_source
    td.update(td.flag('multi-ctx-flag').variations(false, true).variation_for_all(1))

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    # Create multi-context
    user_context = LaunchDarkly::LDContext.create({ key: 'user-1', kind: 'user' })
    org_context = LaunchDarkly::LDContext.create({ key: 'org-1', kind: 'organization' })
    multi_context = LaunchDarkly::LDContext.create_multi([user_context, org_context])

    @client.variation('multi-ctx-flag', multi_context, false)

    spans = @exporter.finished_spans
    span = spans.first

    # Multi-context should have kinds joined (LaunchDarkly-specific)
    context_kind = span.attributes['launchdarkly.context.kind']
    assert_includes context_kind, 'user'
    assert_includes context_kind, 'organization'
  end

  def test_json_flag_value_serialization
    td = LaunchDarkly::Integrations::TestData.data_source
    json_value = { 'feature' => 'enabled', 'settings' => { 'limit' => 100 } }
    td.update(td.flag('json-flag').variations({}, json_value).variation_for_all(1))

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'user-json', kind: 'user' })
    result = @client.variation('json-flag', context, {})

    assert_equal json_value, result

    spans = @exporter.finished_spans
    span = spans.first

    # JSON value should be serialized in result.value
    assert_includes span.attributes['feature_flag.result.value'], 'feature'
  end

  def test_plugin_hook_registered_via_config
    td = LaunchDarkly::Integrations::TestData.data_source
    td.update(td.flag('config-hook-flag').variations(false, true).variation_for_all(1))

    # Verify hook is properly registered through plugin mechanism
    hooks = @plugin.get_hooks(nil)
    assert_equal 1, hooks.length
    assert_instance_of LaunchDarklyObservability::Hook, hooks.first

    config = LaunchDarkly::Config.new(
      data_source: td,
      send_events: false,
      plugins: [@plugin]
    )

    @client = LaunchDarkly::LDClient.new('fake-sdk-key', config)
    inject_test_exporter

    context = LaunchDarkly::LDContext.create({ key: 'hook-user', kind: 'user' })
    @client.variation('config-hook-flag', context, false)

    # Verify span was created (hook was invoked)
    spans = @exporter.finished_spans
    assert_equal 1, spans.length
  end

  private

  # Add the test exporter's processor to the tracer provider that the plugin
  # created during registration. Must be called after LDClient.new, which
  # triggers plugin.register -> OpenTelemetryConfig.configure.
  def inject_test_exporter
    OpenTelemetry.tracer_provider.add_span_processor(
      OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)
    )
  end
end
