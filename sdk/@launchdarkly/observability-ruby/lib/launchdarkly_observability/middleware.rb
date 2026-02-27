# frozen_string_literal: true

module LaunchDarklyObservability
  # Rack middleware for request tracing
  #
  # This middleware wraps incoming HTTP requests in OpenTelemetry spans,
  # providing context for flag evaluations that occur during request processing.
  #
  # Works with any Rack-compatible framework (Rails, Sinatra, Grape, Hanami, etc.).
  # In Rails, the Railtie inserts this middleware automatically. For other frameworks,
  # add it manually to your middleware stack:
  #
  # @example Sinatra
  #   use LaunchDarklyObservability::Middleware
  #
  # @example Rack::Builder
  #   use LaunchDarklyObservability::Middleware
  #   run MyApp
  #
  class Middleware
    # Header for observability request context (session/request ID propagation)
    OBSERVABILITY_REQUEST_HEADER = 'HTTP_X_HIGHLIGHT_REQUEST'

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
      session_id, request_id = extract_observability_context(env)

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
        'launchdarkly-ruby',
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

    def extract_observability_context(env)
      header_value = env[OBSERVABILITY_REQUEST_HEADER]
      return [nil, nil] unless header_value

      parts = header_value.to_s.split('/')
      session_id = parts[0]&.then { |s| s.empty? ? nil : s }
      request_id = parts[1]&.then { |s| s.empty? ? nil : s }

      [session_id, request_id]
    end

    def set_baggage_context(session_id, request_id)
      ctx = OpenTelemetry::Context.current
      ctx = OpenTelemetry::Baggage.set_value(SESSION_BAGGAGE_KEY, session_id, context: ctx) if session_id
      ctx = OpenTelemetry::Baggage.set_value(REQUEST_BAGGAGE_KEY, request_id, context: ctx) if request_id
      ctx
    end
  end
end
