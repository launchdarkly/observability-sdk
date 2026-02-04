# frozen_string_literal: true

require 'launchdarkly-server-sdk'

module LaunchDarklyObservability
  # Evaluation hook that instruments LaunchDarkly flag evaluations with OpenTelemetry spans.
  #
  # This hook creates spans for each flag evaluation, capturing:
  # - Flag key and evaluation method
  # - Context information (kind, key)
  # - Evaluation result (value, variation index, reason)
  # - Duration and any errors
  #
  # @example The hook is automatically registered when using the Plugin
  #   plugin = LaunchDarklyObservability::Plugin.new(project_id: 'my-project')
  #   config = LaunchDarkly::Config.new(plugins: [plugin])
  #   client = LaunchDarkly::LDClient.new('sdk-key', config)
  #
  #   # Flag evaluations are now automatically traced
  #   client.variation('my-flag', context, false)
  #
  class Hook
    include LaunchDarkly::Interfaces::Hooks::Hook

    # Tracer name for OpenTelemetry spans
    TRACER_NAME = 'launchdarkly-ruby'

    # Span name prefix
    SPAN_PREFIX = 'launchdarkly'

    # Returns metadata about this hook
    #
    # @return [LaunchDarkly::Interfaces::Hooks::Metadata]
    def metadata
      LaunchDarkly::Interfaces::Hooks::Metadata.new('launchdarkly-observability-hook')
    end

    # Called before flag evaluation
    #
    # Creates an OpenTelemetry span and captures initial context information.
    #
    # @param series_context [LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext]
    # @param data [Hash] Data passed between hook stages
    # @return [Hash] Updated data hash with span information
    def before_evaluation(series_context, data)
      return data unless opentelemetry_available?

      tracer = OpenTelemetry.tracer_provider.tracer(TRACER_NAME, LaunchDarklyObservability::VERSION)
      span_name = "#{SPAN_PREFIX}.#{series_context.method}"

      span = tracer.start_span(span_name, attributes: build_before_attributes(series_context))

      data.merge(
        __ld_observability_span: span,
        __ld_observability_start_time: monotonic_time
      )
    rescue StandardError => e
      # Don't let instrumentation errors affect the evaluation
      warn "[LaunchDarklyObservability] Error in before_evaluation: #{e.message}"
      data
    end

    # Called after flag evaluation
    #
    # Completes the span with evaluation results and timing information.
    #
    # @param series_context [LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext]
    # @param data [Hash] Data passed between hook stages
    # @param detail [LaunchDarkly::EvaluationDetail] The evaluation result
    # @return [Hash] Updated data hash
    def after_evaluation(series_context, data, detail)
      span = data[:__ld_observability_span]
      return data unless span

      start_time = data[:__ld_observability_start_time]

      # Add result attributes
      add_result_attributes(span, detail)

      # Add duration if we have a start time
      if start_time
        duration_ms = ((monotonic_time - start_time) * 1000).round(3)
        span.set_attribute('feature_flag.evaluation.duration_ms', duration_ms)
      end

      # Handle errors
      handle_evaluation_error(span, detail)

      span.finish

      data
    rescue StandardError => e
      # Don't let instrumentation errors affect the evaluation
      warn "[LaunchDarklyObservability] Error in after_evaluation: #{e.message}"
      span&.finish
      data
    end

    private

    def opentelemetry_available?
      defined?(OpenTelemetry) && OpenTelemetry.tracer_provider
    end

    def monotonic_time
      Process.clock_gettime(Process::CLOCK_MONOTONIC)
    end

    def build_before_attributes(series_context)
      attrs = {
        FEATURE_FLAG_KEY => series_context.key,
        FEATURE_FLAG_PROVIDER => 'LaunchDarkly',
        'feature_flag.evaluation.method' => series_context.method.to_s
      }

      # Add context information safely
      context = series_context.context
      if context
        attrs['feature_flag.context.kind'] = extract_context_kind(context)
        attrs['feature_flag.context.key'] = extract_context_key(context)
      end

      # Add default value type
      default_value = series_context.default_value
      attrs['feature_flag.default_value.type'] = default_value.class.name unless default_value.nil?

      attrs
    end

    def extract_context_kind(context)
      if context.respond_to?(:kind)
        context.kind.to_s
      elsif context.respond_to?(:kinds)
        context.kinds.join(',')
      else
        'unknown'
      end
    end

    def extract_context_key(context)
      if context.respond_to?(:key)
        context.key.to_s
      elsif context.respond_to?(:keys)
        # For multi-context, join the keys
        context.keys.values.join(',')
      else
        'unknown'
      end
    end

    def add_result_attributes(span, detail)
      # Variation index (if available)
      span.set_attribute(FEATURE_FLAG_VARIANT, detail.variation_index.to_s) if detail.variation_index

      # Value - convert to string for safe attribute value
      value = detail.value
      value_str = case value
                  when String, Numeric, TrueClass, FalseClass, NilClass
                    value.to_s
                  when Hash, Array
                    value.to_json
                  else
                    value.to_s
                  end
      span.set_attribute('feature_flag.value', value_str)
      span.set_attribute('feature_flag.value.type', value.class.name)

      # Evaluation reason
      reason = detail.reason
      return unless reason

      span.set_attribute('feature_flag.reason.kind', reason.kind.to_s) if reason.respond_to?(:kind)

      # Additional reason details based on kind
      add_reason_details(span, reason)
    end

    def add_reason_details(span, reason)
      return unless reason.respond_to?(:kind)

      case reason.kind
      when :RULE_MATCH
        span.set_attribute('feature_flag.reason.rule_index', reason.rule_index) if reason.respond_to?(:rule_index)
        span.set_attribute('feature_flag.reason.rule_id', reason.rule_id) if reason.respond_to?(:rule_id)
      when :PREREQUISITE_FAILED
        if reason.respond_to?(:prerequisite_key)
          span.set_attribute('feature_flag.reason.prerequisite_key',
                             reason.prerequisite_key)
        end
      when :ERROR
        span.set_attribute('feature_flag.reason.error_kind', reason.error_kind.to_s) if reason.respond_to?(:error_kind)
      end

      # In experiment flag
      span.set_attribute('feature_flag.reason.in_experiment', reason.in_experiment) if reason.respond_to?(:in_experiment)
    end

    def handle_evaluation_error(span, detail)
      reason = detail.reason
      return unless reason&.respond_to?(:kind) && reason.kind == :ERROR

      error_kind = reason.respond_to?(:error_kind) ? reason.error_kind.to_s : 'UNKNOWN'
      span.set_attribute('feature_flag.error', error_kind)
      span.status = OpenTelemetry::Trace::Status.error("Flag evaluation error: #{error_kind}")
    end
  end
end
