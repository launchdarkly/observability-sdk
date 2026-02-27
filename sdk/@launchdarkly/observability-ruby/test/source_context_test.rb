# frozen_string_literal: true

require 'test_helper'

class SourceContextTest < Minitest::Test
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

  def test_build_structured_stacktrace_includes_source_context
    exception = capture_exception_with_context
    frames = LaunchDarklyObservability::SourceContext.build_structured_stacktrace(exception)

    refute_nil frames
    refute_empty frames

    first = frames.first
    assert_equal __FILE__, first[:fileName]
    assert first[:lineNumber].is_a?(Integer)
    assert_includes first[:lineContent], "raise StandardError, 'context failure'"
    assert_includes first[:linesBefore], 'important_value = 42'
    assert first[:error].include?('context failure')
  end

  def test_build_structured_stacktrace_gracefully_handles_missing_files
    error = StandardError.new('missing file context')
    error.set_backtrace(["/definitely/missing/file.rb:77:in `explode'"])

    frames = LaunchDarklyObservability::SourceContext.build_structured_stacktrace(error)

    assert_equal 1, frames.length
    assert_equal '/definitely/missing/file.rb', frames[0][:fileName]
    assert_equal 77, frames[0][:lineNumber]
    assert_equal 'explode', frames[0][:functionName]
    assert_nil frames[0][:lineContent]
    assert_nil frames[0][:linesBefore]
    assert_nil frames[0][:linesAfter]
  end

  def test_build_structured_stacktrace_limits_frame_count
    error = StandardError.new('too many frames')
    backtrace = (1..25).map { |idx| "/tmp/f#{idx}.rb:#{idx}:in `frame#{idx}'" }
    error.set_backtrace(backtrace)

    frames = LaunchDarklyObservability::SourceContext.build_structured_stacktrace(error)

    assert_equal 20, frames.length
    assert_equal '/tmp/f1.rb', frames.first[:fileName]
    assert_equal '/tmp/f20.rb', frames.last[:fileName]
  end

  def test_record_exception_adds_structured_stacktrace_attribute
    LaunchDarklyObservability.in_span('record-exception-test') do
      begin
        raise StandardError, 'recorded with structured context'
      rescue StandardError => e
        LaunchDarklyObservability.record_exception(e)
      end
    end

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    event = spans.first.events.first
    structured = event.attributes['exception.structured_stacktrace']
    refute_nil structured
    parsed = JSON.parse(structured)
    assert parsed.is_a?(Array)
    refute_empty parsed
    assert parsed.first['lineContent']
  end

  private

  def capture_exception_with_context
    important_value = 42
    another_value = important_value + 1
    raise StandardError, 'context failure'
  rescue StandardError => e
    e
  end
end
