# frozen_string_literal: true

module LaunchDarklyObservability
  # Rack middleware for request tracing
  #
  # This middleware wraps incoming HTTP requests in OpenTelemetry spans,
  # providing context for flag evaluations that occur during request processing.
  #
  # The middleware is automatically installed by the Railtie when Rails is detected.
  #
  # @note This middleware is complementary to OpenTelemetry's Rails instrumentation.
  #   It adds LaunchDarkly-specific context propagation and request ID tracking.
  #
  class Middleware
    # Header for highlight/observability request context
    HIGHLIGHT_REQUEST_HEADER = 'HTTP_X_HIGHLIGHT_REQUEST'

    # Baggage keys for context propagation
    SESSION_BAGGAGE_KEY = 'launchdarkly.session_id'
    REQUEST_BAGGAGE_KEY = 'launchdarkly.request_id'

    def initialize(app)
      @app = app
    end

    # Process the request with tracing context
    #
    # @param env [Hash] Rack environment
    # @return [Array] Rack response tuple
    def call(env)
      return @app.call(env) unless tracing_available?

      request = Rack::Request.new(env)

      # Extract session/request IDs from headers (if present)
      session_id, request_id = extract_highlight_context(env)

      # Set baggage for downstream spans
      ctx = set_baggage_context(session_id, request_id)

      OpenTelemetry::Context.with_current(ctx) do
        tracer.in_span(span_name(request), attributes: request_attributes(request)) do |span|
          # Add session/request IDs as span attributes
          span.set_attribute(SESSION_BAGGAGE_KEY, session_id) if session_id
          span.set_attribute(REQUEST_BAGGAGE_KEY, request_id) if request_id

          status, headers, body = @app.call(env)

          # Add response attributes
          span.set_attribute('http.status_code', status)
          span.status = OpenTelemetry::Trace::Status.error("HTTP #{status}") if status >= 500

          [status, headers, body]
        end
      end
    rescue StandardError => e
      # Don't let middleware errors break the request
      warn "[LaunchDarklyObservability] Middleware error: #{e.message}"
      @app.call(env)
    end

    private

    def tracing_available?
      defined?(OpenTelemetry) && OpenTelemetry.tracer_provider
    end

    def tracer
      @tracer ||= OpenTelemetry.tracer_provider.tracer(
        'launchdarkly-ruby-rails',
        LaunchDarklyObservability::VERSION
      )
    end

    def span_name(request)
      "#{request.request_method} #{request.path}"
    end

    def request_attributes(request)
      {
        'http.method' => request.request_method,
        'http.url' => request.url,
        'http.target' => request.path,
        'http.host' => request.host,
        'http.scheme' => request.scheme,
        'http.user_agent' => request.user_agent
      }.compact
    end

    def extract_highlight_context(env)
      header_value = env[HIGHLIGHT_REQUEST_HEADER]
      return [nil, nil] unless header_value

      parts = header_value.to_s.split('/')
      session_id = parts[0].presence
      request_id = parts[1].presence

      [session_id, request_id]
    end

    def set_baggage_context(session_id, request_id)
      ctx = OpenTelemetry::Context.current
      ctx = OpenTelemetry::Baggage.set_value(SESSION_BAGGAGE_KEY, session_id, context: ctx) if session_id
      ctx = OpenTelemetry::Baggage.set_value(REQUEST_BAGGAGE_KEY, request_id, context: ctx) if request_id
      ctx
    end
  end

  # Rails Railtie for automatic integration
  #
  # This Railtie automatically:
  # - Inserts the LaunchDarkly middleware into the Rails middleware stack
  # - Configures Rails.logger to export to OpenTelemetry (if logger provider is available)
  # - Provides helper methods for controllers
  #
  # @example The Railtie is automatically loaded when Rails is detected
  #   # In config/initializers/launchdarkly.rb
  #   LaunchDarklyObservability.init(project_id: ENV['LD_PROJECT_ID'])
  #
  class Railtie < ::Rails::Railtie
    initializer 'launchdarkly_observability.configure_rails' do |app|
      # Insert middleware early in the stack for context propagation
      app.middleware.insert_before(0, LaunchDarklyObservability::Middleware)
    end

    config.after_initialize do
      # Include controller helpers in ActionController
      if defined?(ActionController::Base)
        ActionController::Base.include(LaunchDarklyObservability::ControllerHelpers)
      end

      if defined?(ActionController::API)
        ActionController::API.include(LaunchDarklyObservability::ControllerHelpers)
      end
    end
  end

  # Controller helper methods for Rails
  #
  # These helpers provide convenient access to observability features
  # within Rails controllers.
  #
  module ControllerHelpers
    extend ActiveSupport::Concern

    included do
      helper_method :launchdarkly_trace_id if respond_to?(:helper_method)
    end

    # Get the current trace ID (useful for logging/debugging)
    #
    # @return [String, nil] The current OpenTelemetry trace ID
    def launchdarkly_trace_id
      return nil unless defined?(OpenTelemetry)

      span = OpenTelemetry::Trace.current_span
      return nil unless span&.context&.valid?

      span.context.hex_trace_id
    end

    # Create a custom span for tracing specific operations
    #
    # @param name [String] The span name
    # @param attributes [Hash] Span attributes
    # @yield [span] Block to execute within the span
    # @return The result of the block
    def with_launchdarkly_span(name, attributes: {})
      return yield unless defined?(OpenTelemetry) && OpenTelemetry.tracer_provider

      tracer = OpenTelemetry.tracer_provider.tracer(
        'launchdarkly-ruby-rails',
        LaunchDarklyObservability::VERSION
      )

      tracer.in_span(name, attributes: attributes) do |span|
        yield(span)
      end
    end

    # Record an exception in the current span
    #
    # @param exception [Exception] The exception to record
    # @param attributes [Hash] Additional attributes
    def record_launchdarkly_exception(exception, attributes: {})
      return unless defined?(OpenTelemetry)

      span = OpenTelemetry::Trace.current_span
      return unless span

      span.record_exception(exception, attributes: attributes)
      span.status = OpenTelemetry::Trace::Status.error(exception.message)
    end
  end

  # View helpers for Rails
  #
  # These helpers can be used in views to inject tracing context
  # into the rendered HTML for client-side correlation.
  #
  module ViewHelpers
    # Generate a meta tag with the current traceparent
    #
    # This is useful for correlating server-side traces with client-side
    # observability data.
    #
    # @return [String] HTML meta tag with traceparent value
    def launchdarkly_traceparent_meta_tag
      traceparent = launchdarkly_traceparent
      return '' unless traceparent

      tag.meta(name: 'traceparent', content: traceparent)
    end

    # Get the current W3C traceparent value
    #
    # @return [String, nil] The traceparent header value
    def launchdarkly_traceparent
      return nil unless defined?(OpenTelemetry)

      span = OpenTelemetry::Trace.current_span
      return nil unless span&.context&.valid?

      trace_id = span.context.hex_trace_id
      span_id = span.context.hex_span_id
      trace_flags = span.context.trace_flags.sampled? ? '01' : '00'

      "00-#{trace_id}-#{span_id}-#{trace_flags}"
    end
  end

  # Include view helpers in ActionView if available
  if defined?(ActionView::Base)
    ActionView::Base.include(ViewHelpers)
  end
end
