# frozen_string_literal: true

require 'test_helper'
require 'rack'
require 'rack/test'
require_relative '../lib/launchdarkly_observability/middleware'

class MiddlewareTest < Minitest::Test
  include Rack::Test::Methods
  include TestHelper

  def setup
    @exporter = create_test_exporter

    # Configure OpenTelemetry with test exporter
    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)
      )
    end
  end

  def teardown
    @exporter.reset
  end

  def app
    inner_app = lambda do |_env|
      [200, { 'Content-Type' => 'text/plain' }, ['OK']]
    end

    LaunchDarklyObservability::Middleware.new(inner_app)
  end

  def test_middleware_creates_span_for_request
    get '/test-path'

    assert last_response.ok?

    spans = @exporter.finished_spans
    assert_equal 1, spans.length

    span = spans.first
    assert_equal 'GET /test-path', span.name
    assert_equal 'GET', span.attributes['http.method']
    assert_includes span.attributes['http.url'], '/test-path'
  end

  def test_middleware_records_status_code
    get '/test-path'

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 200, span.attributes['http.status_code']
  end

  def test_middleware_extracts_observability_context
    header 'X-Highlight-Request', 'session-123/request-456'
    get '/with-context'

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 'session-123', span.attributes['launchdarkly.session_id']
    assert_equal 'request-456', span.attributes['launchdarkly.request_id']
  end

  def test_middleware_handles_missing_observability_header
    get '/no-context'

    spans = @exporter.finished_spans
    span = spans.first

    refute span.attributes.key?('launchdarkly.session_id')
    refute span.attributes.key?('launchdarkly.request_id')
  end

  def test_middleware_records_error_status
    error_app = lambda do |_env|
      [500, { 'Content-Type' => 'text/plain' }, ['Internal Server Error']]
    end

    middleware = LaunchDarklyObservability::Middleware.new(error_app)
    status, = middleware.call(Rack::MockRequest.env_for('/error'))

    assert_equal 500, status

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal 500, span.attributes['http.status_code']
    assert_equal OpenTelemetry::Trace::Status::ERROR, span.status.code
  end

  def test_middleware_captures_request_attributes
    header 'User-Agent', 'Test-Agent/1.0'
    get '/full-attributes'

    spans = @exporter.finished_spans
    span = spans.first

    assert_equal '/full-attributes', span.attributes['http.target']
    assert_equal 'Test-Agent/1.0', span.attributes['http.user_agent']
  end

  def test_middleware_handles_app_exception
    error_app = lambda do |_env|
      raise StandardError, 'Test error'
    end

    middleware = LaunchDarklyObservability::Middleware.new(error_app)

    assert_raises(StandardError) do
      middleware.call(Rack::MockRequest.env_for('/exception'))
    end
  end

  def test_middleware_continues_without_otel
    # Temporarily disable OpenTelemetry
    original_provider = OpenTelemetry.tracer_provider
    OpenTelemetry.instance_variable_set(:@tracer_provider, nil)

    begin
      get '/no-otel'
      assert last_response.ok?
    ensure
      OpenTelemetry.instance_variable_set(:@tracer_provider, original_provider)
    end
  end
end
