# frozen_string_literal: true

require_relative 'middleware'

module LaunchDarklyObservability
  if defined?(::Rails::Railtie)
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
        app.middleware.insert_before(0, LaunchDarklyObservability::Middleware)
      end

      config.after_initialize do
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

      # @return [String, nil] The current OpenTelemetry trace ID
      def launchdarkly_trace_id
        return nil unless defined?(OpenTelemetry)

        span = OpenTelemetry::Trace.current_span
        return nil unless span&.context&.valid?

        span.context.hex_trace_id
      end

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
      # @return [String] HTML meta tag with traceparent value
      def launchdarkly_traceparent_meta_tag
        traceparent = launchdarkly_traceparent
        return '' unless traceparent

        tag.meta(name: 'traceparent', content: traceparent)
      end

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

    if defined?(ActionView::Base)
      ActionView::Base.include(ViewHelpers)
    end
  end
end
