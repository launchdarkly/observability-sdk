# frozen_string_literal: true

require 'test_helper'

# End-to-end proof that the Rails 7.0 app EXPORTS telemetry over the real OTLP
# protobuf pipeline to a local sink (test/support/otlp_sink.rb), which test_helper
# points the exporter at via OTEL_EXPORTER_OTLP_ENDPOINT.
#
# This is the CardFlight repro's headline assertion. On Rails 7.0 with the
# unpinned gem, the Rails-family OTel instrumentations "failed to install", so no
# autoinstrumented HTTP *server* span is ever produced or exported — assertion
# (1) below fails. After the gem is fixed to pin the Rails-family instrumentation
# to a Rails-7.0-compatible version, the server span appears and the test passes.
class OtlpExportE2ETest < ActionDispatch::IntegrationTest
  # OTLP Span.kind for SERVER (proto enum symbol or its int value).
  SERVER_KINDS = [:SPAN_KIND_SERVER, 2].freeze

  def setup
    OTLP_SINK.reset
  end

  test 'traces, a log, and a captured exception are exported to the OTLP sink' do
    post traces_path # manual spans + an autoinstrumented HTTP server span
    post logs_path   # Rails.logger.info "hello, world! foo=bar"
    post errors_path # 1 / 0 -> LaunchDarklyObservability.record_exception
    flush_telemetry

    # 1) TRACES — an autoinstrumented HTTP *server* span proves the Rails/Rack
    #    instrumentation attached. This is the assertion that is RED on Rails 7.0
    #    with the unpinned gem and GREEN after the fix.
    assert wait_until { OTLP_SINK.spans.any? { |s| SERVER_KINDS.include?(s.kind) } },
           'expected an autoinstrumented HTTP server span at the OTLP sink ' \
           "(got span names: #{OTLP_SINK.spans.map(&:name).inspect})"

    # 2) LOGS — the info log reached the sink via the OTel log bridge.
    assert wait_until { OTLP_SINK.logs.any? { |l| l.body.to_s.include?('hello, world! foo=bar') } },
           "expected the info log at the OTLP sink (got log bodies: #{OTLP_SINK.logs.map(&:body).inspect})"

    # 3) EXCEPTION — the ZeroDivisionError was recorded as an exception event.
    exception_events = OTLP_SINK.spans.flat_map(&:events).select { |e| e[:name] == 'exception' }
    refute_empty exception_events, 'expected a recorded exception event at the OTLP sink'
    assert exception_events.any? { |e| e[:attributes]['exception.type'].to_s.include?('ZeroDivisionError') },
           'expected a ZeroDivisionError exception event ' \
           "(got types: #{exception_events.map { |e| e[:attributes]['exception.type'] }.inspect})"
  end

  private

  def flush_telemetry
    OpenTelemetry.tracer_provider.force_flush
    return unless OpenTelemetry.respond_to?(:logger_provider) &&
                  OpenTelemetry.logger_provider.respond_to?(:force_flush)

    OpenTelemetry.logger_provider.force_flush
  end

  # Poll until the block is truthy or the timeout elapses; returns the final value.
  def wait_until(timeout: 5.0)
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + timeout
    loop do
      result = yield
      return result if result
      break if Process.clock_gettime(Process::CLOCK_MONOTONIC) > deadline

      sleep 0.1
    end
    yield
  end
end
