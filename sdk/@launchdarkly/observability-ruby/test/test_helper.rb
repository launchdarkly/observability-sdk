# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'minitest/pride'

# Mock OpenTelemetry before loading our gem
require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'

# Load our gem
require 'launchdarkly_observability'

# Test helper module
module TestHelper
  # Create a mock EvaluationSeriesContext
  def create_series_context(key: 'test-flag', method: :variation, default_value: false)
    context = create_ld_context
    LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext.new(key, context, default_value, method)
  end

  # Create a mock LDContext
  def create_ld_context(key: 'user-123', kind: 'user')
    LaunchDarkly::LDContext.create({ key: key, kind: kind })
  end

  # Create a mock EvaluationDetail
  def create_evaluation_detail(value: true, variation_index: 1, reason: nil)
    reason ||= LaunchDarkly::EvaluationReason.fallthrough
    LaunchDarkly::EvaluationDetail.new(value, variation_index, reason)
  end

  # Create an error EvaluationDetail
  def create_error_detail(error_kind: :FLAG_NOT_FOUND)
    reason = LaunchDarkly::EvaluationReason.error(error_kind)
    LaunchDarkly::EvaluationDetail.new(nil, nil, reason)
  end

  # Reset OpenTelemetry state between tests
  def reset_opentelemetry
    # Reset the tracer provider to a new SDK instance
    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(
                             OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
                           ))
    end
  end

  # Get an in-memory span exporter for testing
  def create_test_exporter
    OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
  end
end
