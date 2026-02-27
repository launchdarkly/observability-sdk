# frozen_string_literal: true

require 'test_helper'

# Tests to verify attribute naming consistency with OpenTelemetry semantic conventions
# and cross-SDK compatibility.
#
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

  # OpenTelemetry Semantic Convention Attribute Tests
  # These attributes should match the OTel spec exactly

  def test_feature_flag_key_follows_semconv
    # OTel semconv: feature_flag.key
    series_context = create_series_context(key: 'test-flag')
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.key'), 'Missing feature_flag.key attribute'
    assert_equal 'test-flag', span.attributes['feature_flag.key']
  end

  def test_feature_flag_provider_name_follows_semconv
    # OTel semconv: feature_flag.provider.name
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.provider.name'), 'Missing feature_flag.provider.name attribute'
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
  end

  def test_feature_flag_context_id_follows_semconv
    # OTel semconv: feature_flag.context.id
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
    # OTel semconv: feature_flag.result.value
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail(value: 'test-value'))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.value'), 'Missing feature_flag.result.value attribute'
    assert_equal 'test-value', span.attributes['feature_flag.result.value']
  end

  def test_feature_flag_result_variant_follows_semconv
    # OTel semconv: feature_flag.result.variant
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail(variation_index: 2))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.variant'), 'Missing feature_flag.result.variant attribute'
    assert_equal '2', span.attributes['feature_flag.result.variant']
  end

  def test_feature_flag_result_reason_follows_semconv
    # OTel semconv: feature_flag.result.reason
    # Maps LaunchDarkly reason.kind to semconv values
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('feature_flag.result.reason'), 'Missing feature_flag.result.reason attribute'
    # FALLTHROUGH maps to 'default' per semconv
    assert_equal 'default', span.attributes['feature_flag.result.reason']
  end

  def test_error_type_follows_semconv
    # OTel semconv: error.type
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('error.type'), 'Missing error.type attribute'
    assert_equal 'flag_not_found', span.attributes['error.type']
  end

  def test_error_message_follows_semconv
    # OTel semconv: error.message
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    assert span.attributes.key?('error.message'), 'Missing error.message attribute'
    assert_includes span.attributes['error.message'], 'FLAG_NOT_FOUND'
  end

  # LaunchDarkly-specific Attribute Tests
  # These should use the 'launchdarkly.*' namespace

  def test_launchdarkly_namespace_for_custom_attributes
    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    sleep(0.001) # Ensure measurable duration
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first

    # All LaunchDarkly-specific attributes should use launchdarkly.* namespace
    ld_attributes = span.attributes.keys.select { |k| k.start_with?('launchdarkly.') }
    
    assert_includes ld_attributes, 'launchdarkly.evaluation.method'
    assert_includes ld_attributes, 'launchdarkly.evaluation.duration_ms'
    assert_includes ld_attributes, 'launchdarkly.context.kind'
    assert_includes ld_attributes, 'launchdarkly.context.key'
    assert_includes ld_attributes, 'launchdarkly.reason.kind'
  end

  def test_launchdarkly_reason_kind_attribute
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    # LaunchDarkly-specific reason kind (raw value from SDK)
    assert span.attributes.key?('launchdarkly.reason.kind'), 'Missing launchdarkly.reason.kind attribute'
    assert_equal 'FALLTHROUGH', span.attributes['launchdarkly.reason.kind']
  end

  def test_launchdarkly_context_kind_attribute
    context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert span.attributes.key?('launchdarkly.context.kind'), 'Missing launchdarkly.context.kind attribute'
    assert_equal 'user', span.attributes['launchdarkly.context.kind']
  end

  def test_launchdarkly_error_attributes
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_error_detail(error_kind: :FLAG_NOT_FOUND))

    span = @exporter.finished_spans.first
    # LaunchDarkly-specific error kind
    assert span.attributes.key?('launchdarkly.reason.error_kind'), 'Missing launchdarkly.reason.error_kind attribute'
    assert_equal 'FLAG_NOT_FOUND', span.attributes['launchdarkly.reason.error_kind']
  end

  # Reason Mapping Tests
  # Verify LaunchDarkly reason kinds map to correct semconv values

  def test_reason_mapping_off_to_disabled
    reason = LaunchDarkly::EvaluationReason.off
    detail = LaunchDarkly::EvaluationDetail.new(false, 0, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'disabled', span.attributes['feature_flag.result.reason']
    assert_equal 'OFF', span.attributes['launchdarkly.reason.kind']
  end

  def test_reason_mapping_fallthrough_to_default
    reason = LaunchDarkly::EvaluationReason.fallthrough
    detail = LaunchDarkly::EvaluationDetail.new(true, 1, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'default', span.attributes['feature_flag.result.reason']
    assert_equal 'FALLTHROUGH', span.attributes['launchdarkly.reason.kind']
  end

  def test_reason_mapping_target_match_to_targeting_match
    reason = LaunchDarkly::EvaluationReason.target_match
    detail = LaunchDarkly::EvaluationDetail.new(true, 1, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'targeting_match', span.attributes['feature_flag.result.reason']
    assert_equal 'TARGET_MATCH', span.attributes['launchdarkly.reason.kind']
  end

  def test_reason_mapping_error_to_error
    reason = LaunchDarkly::EvaluationReason.error(:FLAG_NOT_FOUND)
    detail = LaunchDarkly::EvaluationDetail.new(nil, nil, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    assert_equal 'error', span.attributes['feature_flag.result.reason']
    assert_equal 'ERROR', span.attributes['launchdarkly.reason.kind']
  end

  # Error Type Mapping Tests
  # Verify LaunchDarkly error kinds map to correct semconv error types

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

  # Constants Verification Tests
  # Verify constant definitions match expected values

  def test_module_constants_match_semconv_spec
    # Standard OTel semconv attributes
    assert_equal 'feature_flag.key', LaunchDarklyObservability::FEATURE_FLAG_KEY
    assert_equal 'feature_flag.provider.name', LaunchDarklyObservability::FEATURE_FLAG_PROVIDER_NAME
    assert_equal 'feature_flag.context.id', LaunchDarklyObservability::FEATURE_FLAG_CONTEXT_ID
    assert_equal 'feature_flag.result.value', LaunchDarklyObservability::FEATURE_FLAG_RESULT_VALUE
    assert_equal 'feature_flag.result.variant', LaunchDarklyObservability::FEATURE_FLAG_RESULT_VARIANT
    assert_equal 'feature_flag.result.reason', LaunchDarklyObservability::FEATURE_FLAG_RESULT_REASON
    assert_equal 'error.type', LaunchDarklyObservability::ERROR_TYPE
    assert_equal 'error.message', LaunchDarklyObservability::ERROR_MESSAGE
  end

  def test_launchdarkly_constants_use_correct_namespace
    # LaunchDarkly-specific attributes should use launchdarkly.* namespace
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_EVALUATION_METHOD)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_EVALUATION_DURATION_MS)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_CONTEXT_KIND)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_CONTEXT_KEY)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_KIND)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_RULE_INDEX)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_RULE_ID)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_PREREQUISITE_KEY)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_IN_EXPERIMENT)
    assert_match(/^launchdarkly\./, LaunchDarklyObservability::LD_REASON_ERROR_KIND)
  end

  # Cross-SDK Compatibility Tests
  # Verify attribute names match other LaunchDarkly observability SDKs

  def test_span_name_matches_other_sdks
    # Android, Go, Node all use 'evaluation' as span name
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'evaluation', span.name, 'Span name should be "evaluation" to match other SDKs'
  end

  def test_provider_name_matches_other_sdks
    # All SDKs should report 'LaunchDarkly' as provider name
    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
  end

  # Feature Flag Event Tests
  # Verify the "feature_flag" event is added to spans (matching Android/Node SDKs)

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
    event = span.events.first
    event_attrs = event.attributes

    # Required semantic convention attributes
    assert_equal 'my-flag', event_attrs['feature_flag.key']
    assert_equal 'LaunchDarkly', event_attrs['feature_flag.provider.name']
    assert_equal 'user-123', event_attrs['feature_flag.context.id']
    assert_equal 'true', event_attrs['feature_flag.result.value']
    assert_equal '1', event_attrs['feature_flag.result.variant']
    assert_equal 'default', event_attrs['feature_flag.result.reason']
  end

  def test_feature_flag_event_includes_in_experiment_when_present
    # Create a reason with in_experiment flag
    reason = LaunchDarkly::EvaluationReason.fallthrough(true) # in_experiment = true
    detail = LaunchDarkly::EvaluationDetail.new(true, 1, reason)

    series_context = create_series_context
    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    event = span.events.first
    event_attrs = event.attributes

    assert event_attrs.key?('launchdarkly.reason.in_experiment'), 
           'Event should include in_experiment attribute'
    assert_equal true, event_attrs['launchdarkly.reason.in_experiment']
  end

  def test_feature_flag_event_handles_complex_values
    series_context = create_series_context
    detail = create_evaluation_detail(value: { nested: { key: 'value' }, array: [1, 2, 3] })

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    event = span.events.first
    event_attrs = event.attributes

    # Complex value should be JSON serialized
    assert_includes event_attrs['feature_flag.result.value'], 'nested'
    assert_includes event_attrs['feature_flag.result.value'], 'array'
  end

  def test_feature_flag_event_matches_android_sdk_pattern
    # Android SDK adds event named "feature_flag" with these attributes:
    # - feature_flag.key
    # - feature_flag.provider.name
    # - feature_flag.context.id
    # - feature_flag.result.value (if includeValue)
    # - feature_flag.result.variationIndex
    # - feature_flag.result.reason.inExperiment (if present)

    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'android-compat-flag', context, false, :variation
    )
    detail = create_evaluation_detail(value: 'enabled', variation_index: 2)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first
    event = span.events.first

    # Event name matches Android
    assert_equal 'feature_flag', event.name

    # Required attributes match Android pattern
    event_attrs = event.attributes
    assert event_attrs.key?('feature_flag.key')
    assert event_attrs.key?('feature_flag.provider.name')
    assert event_attrs.key?('feature_flag.context.id')
    assert event_attrs.key?('feature_flag.result.value')
    assert event_attrs.key?('feature_flag.result.variant')
  end
end
