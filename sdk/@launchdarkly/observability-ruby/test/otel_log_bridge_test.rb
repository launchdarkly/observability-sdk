# frozen_string_literal: true

require 'test_helper'

class OtelLogBridgeTest < Minitest::Test
  include TestHelper

  def setup
    require 'opentelemetry-logs-sdk'

    resource = OpenTelemetry::SDK::Resources::Resource.create({})
    @logger_provider = OpenTelemetry::SDK::Logs::LoggerProvider.new(resource: resource)
    @log_exporter = OpenTelemetry::SDK::Logs::Export::InMemoryLogRecordExporter.new
    processor = OpenTelemetry::SDK::Logs::Export::SimpleLogRecordProcessor.new(@log_exporter)
    @logger_provider.add_log_record_processor(processor)

    @bridge = LaunchDarklyObservability::OtelLogBridge.new(@logger_provider)
  end

  def teardown
    @log_exporter.reset
  end

  def test_info_log_emits_otel_record
    @bridge.info 'hello world'

    records = @log_exporter.emitted_log_records
    assert_equal 1, records.length

    record = records.first
    assert_equal 'hello world', record.body
    assert_equal 9, record.severity_number
    assert_equal 'INFO', record.severity_text
  end

  def test_warn_log_emits_correct_severity
    @bridge.warn 'watch out'

    record = @log_exporter.emitted_log_records.first
    assert_equal 'watch out', record.body
    assert_equal 13, record.severity_number
    assert_equal 'WARN', record.severity_text
  end

  def test_error_log_emits_correct_severity
    @bridge.error 'something broke'

    record = @log_exporter.emitted_log_records.first
    assert_equal 17, record.severity_number
    assert_equal 'ERROR', record.severity_text
  end

  def test_debug_log_emits_correct_severity
    @bridge.level = ::Logger::DEBUG
    @bridge.debug 'debug info'

    record = @log_exporter.emitted_log_records.first
    assert_equal 5, record.severity_number
    assert_equal 'DEBUG', record.severity_text
  end

  def test_debug_log_filtered_by_level
    @bridge.level = ::Logger::INFO
    @bridge.debug 'should be dropped'

    assert_empty @log_exporter.emitted_log_records
  end

  def test_hash_message_becomes_attributes
    @bridge.info(test: 'ing', foo: 'bar')

    record = @log_exporter.emitted_log_records.first
    assert_includes record.body, 'test'
    assert_includes record.body, 'foo'
    assert_equal({ 'test' => 'ing', 'foo' => 'bar' }, record.attributes)
  end

  def test_nil_message_is_skipped
    @bridge.info(nil)

    assert_empty @log_exporter.emitted_log_records
  end

  def test_block_message_is_evaluated
    @bridge.info { 'lazy message' }

    record = @log_exporter.emitted_log_records.first
    assert_equal 'lazy message', record.body
  end

  def test_trace_correlation_when_span_active
    exporter = create_test_exporter
    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(exporter)
      )
    end

    tracer = OpenTelemetry.tracer_provider.tracer('test')
    tracer.in_span('test-span') do |span|
      @bridge.info 'inside span'

      record = @log_exporter.emitted_log_records.first
      assert_equal span.context.trace_id, record.trace_id
      assert_equal span.context.span_id, record.span_id
    end
  end

  def test_no_crash_when_otel_context_unavailable
    @bridge.info 'no context'

    records = @log_exporter.emitted_log_records
    assert_equal 1, records.length
  end

  def test_multiple_logs_all_emitted
    @bridge.info 'first'
    @bridge.warn 'second'
    @bridge.error 'third'

    assert_equal 3, @log_exporter.emitted_log_records.length
  end
end
