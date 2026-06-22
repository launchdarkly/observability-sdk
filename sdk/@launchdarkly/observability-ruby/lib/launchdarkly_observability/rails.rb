# frozen_string_literal: true

require_relative 'middleware'

module LaunchDarklyObservability
  if defined?(::Rails::Railtie)
    # Controller and view helper modules are defined BEFORE the Railtie on purpose.
    #
    # The Railtie's `config.after_initialize` hook references ControllerHelpers. That
    # hook is registered via ActiveSupport's lazy load hooks, which run immediately if
    # the :after_initialize event has already fired. When the gem is required lazily
    # *after* Rails has booted (e.g. from an autoloaded model during a request), the
    # block executes synchronously while this file is still loading. If the helper
    # modules were defined further down the file, they would not exist yet and the
    # require would raise "uninitialized constant LaunchDarklyObservability::ControllerHelpers".
    # Defining them first makes the require order-independent.

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
      def with_launchdarkly_span(name, attributes: {}, &block)
        return yield unless defined?(OpenTelemetry) && OpenTelemetry.tracer_provider

        tracer = OpenTelemetry.tracer_provider.tracer(
          'launchdarkly-ruby-rails',
          LaunchDarklyObservability::VERSION
        )

        tracer.in_span(name, attributes: attributes, &block)
      end

      # @param exception [Exception] The exception to record
      # @param attributes [Hash] Additional attributes
      def record_launchdarkly_exception(exception, attributes: {})
        return unless defined?(OpenTelemetry)

        span = OpenTelemetry::Trace.current_span
        return unless span

        span.record_exception(exception, attributes: SourceContext.exception_attributes(exception).merge(attributes))
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

    # Rails Railtie for automatic integration
    #
    # This Railtie automatically:
    # - Inserts the LaunchDarkly middleware into the Rails middleware stack
    # - Bridges Rails.logger to the OpenTelemetry Logs pipeline (if logger provider is available)
    # - Provides helper methods for controllers and views
    #
    # @example The Railtie is automatically loaded when Rails is detected
    #   # In config/initializers/launchdarkly.rb
    #   LaunchDarklyObservability.init(project_id: ENV['LD_PROJECT_ID'])
    #
    class Railtie < ::Rails::Railtie
      # Private helpers are defined before `config.after_initialize` references them.
      # The after_initialize hook can run synchronously while this class body is still
      # evaluating (lazy require after Rails has booted — see the note above), so any
      # method it calls must already be defined at that point.
      class << self
        private

        def attach_otel_log_bridge
          return unless otel_logger_provider_available?

          bridge = LaunchDarklyObservability::OtelLogBridge.new(OpenTelemetry.logger_provider)

          if ::Rails.logger.respond_to?(:broadcast_to)
            ::Rails.logger.broadcast_to(bridge)
          elsif defined?(ActiveSupport::Logger) && ActiveSupport::Logger.respond_to?(:broadcast)
            ::Rails.logger.extend(ActiveSupport::Logger.broadcast(bridge))
          end
        rescue StandardError => e
          warn "[LaunchDarklyObservability] Could not attach log bridge to Rails.logger: #{e.message}"
        end

        # The availability check is inlined here rather than delegating to
        # LaunchDarklyObservability.otel_logger_provider_available? on purpose.
        # rails.rb is required from launchdarkly_observability.rb *before* that
        # file's `class << self` block (which defines the module method) has run.
        # When the gem is lazily required after Rails has booted, the
        # `config.after_initialize` hook above executes synchronously while this
        # file is still loading, so the module method does not exist yet and the
        # delegation raised "undefined method `otel_logger_provider_available?'".
        def otel_logger_provider_available?
          # `defined?` returns nil (not false) when the constant is absent, so
          # guard first and return an explicit boolean — callers (and the
          # railtie test) expect true/false, never nil.
          return false unless defined?(OpenTelemetry::SDK::Logs::LoggerProvider)

          OpenTelemetry.respond_to?(:logger_provider) &&
            OpenTelemetry.logger_provider.is_a?(OpenTelemetry::SDK::Logs::LoggerProvider)
        end
      end

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

        attach_otel_log_bridge
      end
    end

    if defined?(ActionView::Base)
      ActionView::Base.include(ViewHelpers)
    end
  end
end
