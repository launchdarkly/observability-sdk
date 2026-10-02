# frozen_string_literal: true

require 'test_helper'

# Tests to verify attribute naming consistency with OpenTelemetry semantic conventions,
# cross-SDK compatibility, and ClickHouse column coverage.
#
# ClickHouse schema: ../observability/backend/clickhouse/feature_flag.go
# OpenTelemetry Feature Flag Semantic Conventions:
# https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/
#
class AttributeNamingTest < Minitest::Test
  include TestHelper

  def setup
    @hook = LaunchDarklyObservability::Hook.new
    @exporter = create_test_exporter

    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)
      )
    end
  end

  def teardown
    @exporter.reset
  end

  # --- Span Attribute Tests ---

  def test_feature_flag_key_follows_semconv
    series_context = create_series_context(key: 'test-flag')
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.key'), 'Missing feature_flag.key attribute'
    assert_equal 'test-flag', span.attributes['feature_flag.key']
  end

  def test_feature_flag_provider_name_follows_semconv
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.provider.name'), 'Missing feature_flag.provider.name attribute'
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
  end

  def test_feature_flag_context_id_follows_semconv
    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.context.id'), 'Missing feature_flag.context.id attribute'
    assert_equal 'user-123', span.attributes['feature_flag.context.id']
  end

  def test_feature_flag_result_value_follows_semconv
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail(value: 'test-value'))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.value'), 'Missing feature_flag.result.value attribute'
    assert_equal 'test-value', span.attributes['feature_flag.result.value']
  end

  def test_feature_flag_result_variant_follows_semconv
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail(variation_index: 2))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.variant'), 'Missing feature_flag.result.variant attribute'
    assert_equal '2', span.attributes['feature_flag.result.variant']
  end

  def test_feature_flag_result_variation_index_matches_other_sdks
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail(variation_index: 2))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.variationIndex'), 'Missing feature_flag.result.variationIndex attribute'
    assert_equal '2', span.attributes['feature_flag.result.variationIndex']
  end

  def test_error_type_follows_semconv
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('error.type'), 'Missing error.type attribute'
    assert_equal 'flag_not_found', span.attributes['error.type']
  end

  def test_error_message_follows_semconv
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('error.message'), 'Missing error.message attribute'
    assert_includes span.attributes['error.message'], 'FLAG_NOT_FOUND'
  end

  def test_no_launchdarkly_namespace_attributes_on_span
    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    ld_attributes = span.attributes.keys.select { |k| k.start_with?('launchdarkly.') }
    assert_empty ld_attributes, "Unexpected launchdarkly.* attributes: #{ld_attributes.inspect}"
  end

  # --- Error Type Mapping Tests ---

  def test_error_type_mapping_flag_not_found
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    assert_equal 'flag_not_found', span.attributes['error.type']
  end

  def test_error_type_mapping_malformed_flag
    reason = LaunchDarkly::EvaluationReason.error(:MALFORMED_FLAG)
    detail = LaunchDarkly::EvaluationDetail.new(nil, nil, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'parse_error', span.attributes['error.type']
  end

  def test_error_type_mapping_client_not_ready
    reason = LaunchDarkly::EvaluationReason.error(:CLIENT_NOT_READY)
    detail = LaunchDarkly::EvaluationDetail.new(nil, nil, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'provider_not_ready', span.attributes['error.type']
  end

  def test_error_type_mapping_wrong_type
    reason = LaunchDarkly::EvaluationReason.error(:WRONG_TYPE)
    detail = LaunchDarkly::EvaluationDetail.new(nil, nil, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'type_mismatch', span.attributes['error.type']
  end

  # --- Constants Verification Tests ---

  def test_module_constants_match_expected_values
    assert_equal 'feature_flag.key', LaunchDarklyObservability::FEATURE_FLAG_KEY
    assert_equal 'feature_flag.provider.name', LaunchDarklyObservability::FEATURE_FLAG_PROVIDER_NAME
    assert_equal 'feature_flag.context.id', LaunchDarklyObservability::FEATURE_FLAG_CONTEXT_ID
    assert_equal 'feature_flag.set.id', LaunchDarklyObservability::FEATURE_FLAG_SET_ID
    assert_equal 'feature_flag.result.value', LaunchDarklyObservability::FEATURE_FLAG_RESULT_VALUE
    assert_equal 'feature_flag.result.variant', LaunchDarklyObservability::FEATURE_FLAG_RESULT_VARIANT
    assert_equal 'feature_flag.result.variationIndex', LaunchDarklyObservability::FEATURE_FLAG_RESULT_VARIATION_INDEX
    assert_equal 'feature_flag.result.reason.kind', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON_KIND
    assert_equal 'feature_flag.result.reason.inExperiment', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT
    assert_equal 'feature_flag.result.reason.errorKind', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON_ERROR_KIND
    assert_equal 'feature_flag.result.reason.ruleId', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON_RULE_ID
    assert_equal 'feature_flag.result.reason.ruleIndex', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON_RULE_INDEX
    assert_equal 'error.type', LaunchDarklyObservability::ERROR_TYPE
    assert_equal 'error.message', LaunchDarklyObservability::ERROR_MESSAGE
  end

  # --- Cross-SDK Compatibility Tests ---

  def test_span_name_matches_other_sdks
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'evaluation', span.name, 'Span name should be "evaluation" to match other SDKs'
  end

  def test_provider_name_matches_other_sdks
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
  end

  # --- Feature Flag Event Tests ---

  def test_feature_flag_event_is_added_to_span
    series_context = create_series_context(key: 'test-flag')
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    events = span.events

    assert_equal 1, events.length, 'Expected one feature_flag event'
    assert_equal 'feature_flag', events.first.name
  end

  def test_feature_flag_event_contains_required_attributes
    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', context, false, :variation
    )
    detail = create_evaluation_detail(value: true, variation_index: 1)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    event_attrs = span.events.first.attributes

    assert_equal 'my-flag', event_attrs['feature_flag.key']
    assert_equal 'LaunchDarkly', event_attrs['feature_flag.provider.name']
    assert_equal 'user-123', event_attrs['feature_flag.context.id']
    assert_equal 'true', event_attrs['feature_flag.result.value']
    assert_equal '1', event_attrs['feature_flag.result.variant']
    assert_equal '1', event_attrs['feature_flag.result.variationIndex']
    assert_equal 'FALLTHROUGH', event_attrs['feature_flag.result.reason.kind']
  end

  def test_feature_flag_event_reason_kind_is_raw
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})

    reason = LaunchDarkly::EvaluationReason.off
    detail = LaunchDarkly::EvaluationDetail.new(false, 0, reason)
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes
    assert_equal 'OFF', event_attrs['feature_flag.result.reason.kind']
  end

  def test_feature_flag_event_reason_kind_for_target_match
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})

    reason = LaunchDarkly::EvaluationReason.target_match
    detail = LaunchDarkly::EvaluationDetail.new(true, 1, reason)
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes
    assert_equal 'TARGET_MATCH', event_attrs['feature_flag.result.reason.kind']
  end

  def test_feature_flag_event_includes_in_experiment
    reason = LaunchDarkly::EvaluationReason.fallthrough(true)
    detail = LaunchDarkly::EvaluationDetail.new(true, 1, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes
    assert event_attrs.key?('feature_flag.result.reason.inExperiment'),
           'Event should include inExperiment attribute'
    assert_equal true, event_attrs['feature_flag.result.reason.inExperiment']
  end

  def test_feature_flag_event_includes_error_kind
    reason = LaunchDarkly::EvaluationReason.error(:FLAG_NOT_FOUND)
    detail = LaunchDarkly::EvaluationDetail.new(nil, nil, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes
    assert_equal 'ERROR', event_attrs['feature_flag.result.reason.kind']
    assert_equal 'FLAG_NOT_FOUND', event_attrs['feature_flag.result.reason.errorKind']
  end

  def test_feature_flag_event_handles_complex_values
    series_context = create_series_context
    detail = create_evaluation_detail(value: { nested: { key: 'value' }, array: [1, 2, 3] })

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes
    assert_includes event_attrs['feature_flag.result.value'], 'nested'
    assert_includes event_attrs['feature_flag.result.value'], 'array'
  end

  def test_feature_flag_event_matches_cross_sdk_pattern
    # All SDKs add a "feature_flag" event with:
    # - feature_flag.key
    # - feature_flag.provider.name
    # - feature_flag.context.id
    # - feature_flag.result.value
    # - feature_flag.result.variationIndex
    # - feature_flag.result.reason.inExperiment (if present)

    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'compat-flag', context, false, :variation
    )
    detail = create_evaluation_detail(value: 'enabled', variation_index: 2)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    event = @exporter.finished_spans.first.events.first
    event_attrs = event.attributes

    assert_equal 'feature_flag', event.name
    assert event_attrs.key?('feature_flag.key')
    assert event_attrs.key?('feature_flag.provider.name')
    assert event_attrs.key?('feature_flag.context.id')
    assert event_attrs.key?('feature_flag.result.value')
    assert event_attrs.key?('feature_flag.result.variant')
    assert event_attrs.key?('feature_flag.result.variationIndex')
    assert event_attrs.key?('feature_flag.result.reason.kind')
  end

  # --- ClickHouse Column Coverage Test ---
  # Verifies that the feature_flag event emits all attribute keys that map
  # to dedicated ClickHouse columns (per feature_flag.go in the backend).

  def test_feature_flag_event_covers_clickhouse_columns
    context = LaunchDarkly::LDContext.create({ key: 'user-789', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'ch-flag', context, false, :variation
    )
    reason = LaunchDarkly::EvaluationReason.fallthrough(true)
    detail = LaunchDarkly::EvaluationDetail.new('on', 1, reason)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    event_attrs = @exporter.finished_spans.first.events.first.attributes

    # These map to dedicated ClickHouse columns (feature_flag.set.id is injected by backend)
    assert event_attrs.key?('feature_flag.key'), 'Missing: FlagKey column'
    assert event_attrs.key?('feature_flag.result.value'), 'Missing: ResultValue column'
    assert event_attrs.key?('feature_flag.result.variant'), 'Missing: ResultVariant column'
    assert event_attrs.key?('feature_flag.result.variationIndex'), 'Missing: ResultVariationIndex column'
    assert event_attrs.key?('feature_flag.result.reason.kind'), 'Missing: ResultReasonKind column'
    assert event_attrs.key?('feature_flag.result.reason.inExperiment'), 'Missing: ResultReasonInExperiment column'
    assert event_attrs.key?('feature_flag.context.id'), 'Missing: ContextId column'
    assert event_attrs.key?('feature_flag.provider.name'), 'Missing: ProviderName column'
  end
end
