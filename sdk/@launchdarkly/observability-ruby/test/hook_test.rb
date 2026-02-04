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
    assert result.key?(:__ld_observability_start_time)
    refute_nil result[:__ld_observability_span]
    assert_kind_of Numeric, result[:__ld_observability_start_time]
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

    # Run before to create span
    data = @hook.before_evaluation(series_context, {})

    # Run after to finish span
    result = @hook.after_evaluation(series_context, data, detail)

    assert_equal data, result

    # Check that span was exported
    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'launchdarkly.variation', span.name
  end

  def test_after_evaluation_adds_result_attributes
    series_context = create_series_context
    detail = create_evaluation_detail(value: true, variation_index: 1)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 'true', span.attributes['feature_flag.value']
    assert_equal '1', span.attributes['feature_flag.variant']
    assert_equal 'FALLTHROUGH', span.attributes['feature_flag.reason.kind']
  end

  def test_after_evaluation_handles_error_reason
    series_context = create_series_context
    detail = create_error_detail(error_kind: :FLAG_NOT_FOUND)

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 'ERROR', span.attributes['feature_flag.reason.kind']
    assert_equal 'FLAG_NOT_FOUND', span.attributes['feature_flag.error']
    assert_equal OpenTelemetry::Trace::Status::ERROR, span.status.code
  end

  def test_after_evaluation_records_duration
    series_context = create_series_context
    detail = create_evaluation_detail

    data = @hook.before_evaluation(series_context, {})

    # Small delay to ensure measurable duration
    sleep(0.001)

    @hook.after_evaluation(series_context, data, detail)

    spans = @exporter.finished_spans
    span = spans.first

    duration = span.attributes['feature_flag.evaluation.duration_ms']
    assert duration.positive?, "Duration should be positive, got #{duration}"
  end

  def test_before_evaluation_captures_context_info
    context = LaunchDarkly::LDContext.create({ key: 'user-456', kind: 'user' })
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'my-flag', context, false, :variation
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 'my-flag', span.attributes['feature_flag.key']
    assert_equal 'user', span.attributes['feature_flag.context.kind']
    assert_equal 'user-456', span.attributes['feature_flag.context.key']
    assert_equal 'LaunchDarkly', span.attributes['feature_flag.provider_name']
  end

  def test_after_evaluation_handles_hash_value
    series_context = create_series_context
    detail = create_evaluation_detail(value: { foo: 'bar', count: 42 })

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, detail)

    spans = @exporter.finished_spans
    span = spans.first

    # Hash should be JSON serialized
    assert_includes span.attributes['feature_flag.value'], 'foo'
    assert_equal 'Hash', span.attributes['feature_flag.value.type']
  end

  def test_after_evaluation_handles_missing_span
    series_context = create_series_context
    detail = create_evaluation_detail
    data = {} # No span in data

    # Should not raise
    result = @hook.after_evaluation(series_context, data, detail)
    assert_equal data, result
  end

  def test_span_name_includes_method
    series_context = LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(
      'flag', create_ld_context, false, :variation_detail
    )

    data = @hook.before_evaluation(series_context, {})
    @hook.after_evaluation(series_context, data, create_evaluation_detail)

    spans = @exporter.finished_spans
    assert_equal 'launchdarkly.variation_detail', spans.first.name
  end
end
