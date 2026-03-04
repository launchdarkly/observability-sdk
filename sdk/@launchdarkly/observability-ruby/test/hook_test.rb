# frozen_string_literal: true

require 'test_helper'

class HookTest < Minitest::Test
  include TestHelper

  def setup
    @hook = LaunchDarklyObservability::Hook.new
    @exporter = create_test_exporter

    # Configure OpenTelemetry with in-memory exporter for testing
    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)
      )
    end
  end

  def teardown
    @exporter.reset
  end

  def test_metadata_returns_correct_name
    metadata = @hook.metadata
    assert_instance_of LaunchDarkly::Interfaces::Hooks::Metadata, metadata
    assert_equal 'launchdarkly-observability-hook', metadata.name
  end

  def test_before_evaluation_creates_span_data
    series_context = create_series_context
    data = {}

    result = @hook.before_evaluation(series_context, data)

    assert result.key?(:__ld_observability_span)
    refute_nil result[:__ld_observability_span]
  end

  def test_before_evaluation_returns_data_when_otel_not_available
    series_context = create_series_context
    data = { existing: 'data' }

    # Temporarily remove OpenTelemetry
    original_provider = OpenTelemetry.tracer_provider
    OpenTelemetry.instance_variable_set(:@tracer_provider, nil)

    begin
      result = @hook.before_evaluation(series_context, data)
      assert_equal data, result
    ensure
      OpenTelemetry.instance_variable_set(:@tracer_provider, original_provider)
    end
  end

  def test_after_evaluation_finishes_span
    series_context = create_series_context
    detail = create_evaluation_detail

    data = @hook.before_evaluation(series_context, {})
    result = @hook.after_evaluation(series_context, data, detail)

    assert_equal data, result

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'evaluation', span.name
  end

  def test_after_evaluation_adds_result_attributes
    series_context = create_series_context
    detail = create_evaluation_detail(value: true, variation_index: 1)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first

    assert_equal 'true', span.attributes['feature_flag.result.value']
    assert_equal '1', span.attributes['feature_flag.result.variant']
    assert_equal '1', span.attributes['feature_flag.result.variationIndex']
  end

  def test_after_evaluation_handles_error_reason
    series_context = create_series_context
    detail = create_error_detail(error_kind: :FLAG_NOT_FOUND)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first

    assert_equal 'flag_not_found', span.attributes['error.type']
    assert_includes span.attributes['error.message'], 'FLAG_NOT_FOUND'
    assert_equal OpenTelemetry::Trace::Status::ERROR, span.status.code

    event = span.events.first
    assert_equal 'ERROR', event.attributes['feature_flag.result.reason.kind']
    assert_equal 'FLAG_NOT_FOUND', event.attributes['feature_flag.result.reason.errorKind']
  end

  def test_before_evaluation_captures_context_info
    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first

    assert_equal 'my-flag', span.attributes['feature_flag.key']
    assert_equal 'user-456', span.attributes['feature_flag.context.id']
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider.name']
  end

  def test_after_evaluation_handles_hash_value
    series_context = create_series_context
    detail = create_evaluation_detail(value: { foo: 'bar', count: 42 })

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    span = @exporter.finished_spans.first

    assert_includes span.attributes['feature_flag.result.value'], 'foo'
  end

  def test_after_evaluation_handles_missing_span
    series_context = create_series_context
    detail = create_evaluation_detail
    data = {}

    result = @hook.after_evaluation(series_context, data, detail)
    assert_equal data, result
  end

  def test_span_name_is_evaluation
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', create_ld_context, false, :variation_detail
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    spans = @exporter.finished_spans
    assert_equal 'evaluation', spans.first.name
  end

  def test_context_id_uses_fully_qualified_key_for_user
    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'user-456', span.attributes['feature_flag.context.id']
  end

  def test_context_id_uses_fully_qualified_key_for_non_user_kind
    context = LaunchDarkly::LDContext.create({ key: 'org-789', kind: 'org' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'org:org-789', span.attributes['feature_flag.context.id']
  end

  def test_context_id_uses_fully_qualified_key_for_multi_kind
    user_ctx = LaunchDarkly::LDContext.create({ key: 'user-1', kind: 'user' })
    org_ctx = LaunchDarkly::LDContext.create({ key: 'org-1', kind: 'org' })
    multi = LaunchDarkly::LDContext.create_multi([user_ctx, org_ctx])

    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', multi, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    span = @exporter.finished_spans.first
    assert_equal 'org:org-1:user:user-1', span.attributes['feature_flag.context.id']
  end

  def test_nil_context_does_not_raise_and_still_records_event
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', nil, false, :variation
    )
    detail = create_evaluation_detail

    data = @hook.before_evaluation(series_context, {})
    result = @hook.after_evaluation(series_context, data, detail)

    assert_equal data, result

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'evaluation', span.name

    flag_event = span.events.find { |e| e.name == 'feature_flag' }
    refute_nil flag_event, 'Expected a feature_flag event even with nil context'
    assert_equal 'my-flag', flag_event.attributes['feature_flag.key']
    assert_equal 'LaunchDarkly', flag_event.attributes['feature_flag.provider.name']
    assert_nil flag_event.attributes['feature_flag.context.id']
  end
end
