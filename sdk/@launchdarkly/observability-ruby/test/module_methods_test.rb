# frozen_string_literal: true

require 'test_helper'

# Tests for LaunchDarklyObservability module-level convenience methods
class ModuleMethodsTest < Minitest::Test
  def setup
    @exporter = OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
    @span_processor = OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)

    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(@span_processor)
    end
  end

  def teardown
    @exporter.reset
  end

  def test_in_span_creates_span
    result = LaunchDarklyObservability.in_span('test-operation') do |span|
      span.set_attribute('test.key', 'test.value')
      'operation result'
    end

    assert_equal 'operation result', result

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'test-operation', span.name
    assert_equal 'test.value', span.attributes['test.key']
  end

  def test_in_span_with_initial_attributes
    LaunchDarklyObservability.in_span('test-operation', attributes: { 'initial.key' => 'initial.value' }) do |span|
      span.set_attribute('added.key', 'added.value')
    end

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'initial.value', span.attributes['initial.key']
    assert_equal 'added.value', span.attributes['added.key']
  end

  def test_in_span_nested_spans
    LaunchDarklyObservability.in_span('outer-span') do |outer|
      outer.set_attribute('level', 'outer')

      LaunchDarklyObservability.in_span('inner-span') do |inner|
        inner.set_attribute('level', 'inner')
      end
    end

    spans = @exporter.finished_spans
    assert_equal 2, spans.length

    inner_span = spans[0]
    outer_span = spans[1]

    assert_equal 'inner-span', inner_span.name
    assert_equal 'inner', inner_span.attributes['level']

    assert_equal 'outer-span', outer_span.name
    assert_equal 'outer', outer_span.attributes['level']

    # Inner span should be child of outer span
    assert_equal outer_span.span_id, inner_span.parent_span_id
  end

  def test_record_exception
    error = StandardError.new('Test error')

    LaunchDarklyObservability.in_span('test-operation') do |span|
      begin
        raise error
      rescue StandardError => e
        LaunchDarklyObservability.record_exception(e, attributes: { 'error.context' => 'test' })
      end
    end

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    events = span.events
    assert_equal 1, events.length

    event = events.first
    assert_equal 'exception', event.name
    assert_equal 'StandardError', event.attributes['exception.type']
    assert_equal 'Test error', event.attributes['exception.message']
    assert event.attributes['exception.stacktrace']

    # Check span status (OpenTelemetry::Trace::Status::ERROR = 2)
    assert_equal OpenTelemetry::Trace::Status::ERROR, span.status.code
    assert_equal 'Test error', span.status.description
  end

  def test_current_trace_id
    trace_id = nil

    LaunchDarklyObservability.in_span('test-operation') do |span|
      trace_id = LaunchDarklyObservability.current_trace_id
    end

    refute_nil trace_id
    assert_match(/^[0-9a-f]{32}$/, trace_id)

    spans = @exporter.finished_spans
    assert_equal 1, spans.length
    assert_equal trace_id, spans.first.hex_trace_id
  end

  def test_current_trace_id_without_span
    # When not in a span, should return nil
    trace_id = LaunchDarklyObservability.current_trace_id
    assert_nil trace_id
  end

  def test_in_span_without_block
    # Should not raise error if called without block
    result = LaunchDarklyObservability.in_span('test-operation')
    assert_nil result
  end

  def test_record_exception_without_span
    # Should not raise error if called outside a span
    error = StandardError.new('Test error')
    LaunchDarklyObservability.record_exception(error)

    # No spans should be created
    spans = @exporter.finished_spans
    assert_equal 0, spans.length
  end
end
