# frozen_string_literal: true

require 'launchdarkly-server-sdk'

module LaunchDarklyObservability
  # Evaluation hook that instruments LaunchDarkly flag evaluations with OpenTelemetry spans and events.
  #
  # This hook creates spans for each flag evaluation, capturing:
  # - Flag key and evaluation method
  # - Context information (kind, key)
  # - Evaluation result (value, variation index, reason)
  # - Duration and any errors
  #
  # Additionally, a "feature_flag" event is added to the span with evaluation results,
  # following the OpenTelemetry semantic conventions for feature flags and matching
  # the pattern used by other LaunchDarkly observability SDKs (Android, Node).
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

    # Span name for feature flag evaluations
    SPAN_NAME = 'evaluation'

    # Event name for feature flag evaluation results (matches Android/Node SDKs)
    FEATURE_FLAG_EVENT_NAME = 'feature_flag'

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

      span = tracer.start_span(SPAN_NAME, attributes: build_before_attributes(series_context))

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
    # Also adds a "feature_flag" event with evaluation results, matching
    # the pattern used by Android and Node SDKs.
    #
    # @param series_context [LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext]
    # @param data [Hash] Data passed between hook stages
    # @param detail [LaunchDarkly::EvaluationDetail] The evaluation result
    # @return [Hash] Updated data hash
    def after_evaluation(series_context, data, detail)
      span = data[:__ld_observability_span]
      return data unless span

      start_time = data[:__ld_observability_start_time]

      # Add result attributes to span
      add_result_attributes(span, detail)

      # Add duration if we have a start time
      if start_time
        duration_ms = ((monotonic_time - start_time) * 1000).round(3)
        span.set_attribute(LD_EVALUATION_DURATION_MS, duration_ms)
      end

      # Handle errors
      handle_evaluation_error(span, detail)

      # Add feature_flag event with evaluation results (matches Android/Node SDKs)
      add_feature_flag_event(span, series_context, detail)

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
        FEATURE_FLAG_PROVIDER_NAME => 'LaunchDarkly',
        LD_EVALUATION_METHOD => series_context.method.to_s
      }

      # Add context information safely
      context = series_context.context
      if context
        # Use semantic convention for context.id (the primary identifier)
        attrs[FEATURE_FLAG_CONTEXT_ID] = extract_context_key(context)
        # Use LaunchDarkly-specific attributes for additional context details
        attrs[LD_CONTEXT_KIND] = extract_context_kind(context)
        attrs[LD_CONTEXT_KEY] = extract_context_key(context)
      end

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
      # Use semantic convention for result.variant (variation index as string)
      if detail.variation_index
        span.set_attribute(FEATURE_FLAG_RESULT_VARIANT, detail.variation_index.to_s)
      end

      # Use semantic convention for result.value
      value = detail.value
      value_str = case value
                  when String, Numeric, TrueClass, FalseClass, NilClass
                    value.to_s
                  when Hash, Array
                    value.to_json
                  else
                    value.to_s
                  end
      span.set_attribute(FEATURE_FLAG_RESULT_VALUE, value_str)

      # Evaluation reason - use semantic convention
      reason = detail.reason
      return unless reason

      # Map LaunchDarkly reason.kind to semantic convention result.reason
      if reason.respond_to?(:kind)
        reason_value = map_reason_kind_to_semconv(reason.kind)
        span.set_attribute(FEATURE_FLAG_RESULT_REASON, reason_value)
        
        # Also add LaunchDarkly-specific reason.kind for compatibility
        span.set_attribute(LD_REASON_KIND, reason.kind.to_s)
      end

      # Additional reason details based on kind
      add_reason_details(span, reason)
    end

    def add_reason_details(span, reason)
      return unless reason.respond_to?(:kind)

      # LaunchDarkly-specific reason details (custom attributes)
      case reason.kind
      when :RULE_MATCH
        span.set_attribute(LD_REASON_RULE_INDEX, reason.rule_index) if reason.respond_to?(:rule_index)
        span.set_attribute(LD_REASON_RULE_ID, reason.rule_id) if reason.respond_to?(:rule_id)
      when :PREREQUISITE_FAILED
        if reason.respond_to?(:prerequisite_key)
          span.set_attribute(LD_REASON_PREREQUISITE_KEY, reason.prerequisite_key)
        end
      when :ERROR
        span.set_attribute(LD_REASON_ERROR_KIND, reason.error_kind.to_s) if reason.respond_to?(:error_kind)
      end

      # In experiment flag (LaunchDarkly-specific)
      if reason.respond_to?(:in_experiment) && !reason.in_experiment.nil?
        span.set_attribute(LD_REASON_IN_EXPERIMENT, reason.in_experiment)
      end
    end

    # Add a "feature_flag" event with evaluation results
    # This matches the pattern used by Android and Node SDKs for cross-SDK consistency
    # See: https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/
    def add_feature_flag_event(span, series_context, detail)
      event_attributes = {
        FEATURE_FLAG_KEY => series_context.key,
        FEATURE_FLAG_PROVIDER_NAME => 'LaunchDarkly',
        FEATURE_FLAG_CONTEXT_ID => extract_context_key(series_context.context)
      }

      # Add variation index if available
      if detail.variation_index
        event_attributes[FEATURE_FLAG_RESULT_VARIANT] = detail.variation_index.to_s
      end

      # Add value (serialized to string for complex types)
      value = detail.value
      value_str = case value
                  when String, Numeric, TrueClass, FalseClass, NilClass
                    value.to_s
                  when Hash, Array
                    value.to_json
                  else
                    value.to_s
                  end
      event_attributes[FEATURE_FLAG_RESULT_VALUE] = value_str

      # Add reason if available
      reason = detail.reason
      if reason&.respond_to?(:kind)
        event_attributes[FEATURE_FLAG_RESULT_REASON] = map_reason_kind_to_semconv(reason.kind)

        # Add in_experiment flag if present (matches Android SDK)
        if reason.respond_to?(:in_experiment) && !reason.in_experiment.nil?
          event_attributes[LD_REASON_IN_EXPERIMENT] = reason.in_experiment
        end
      end

      span.add_event(FEATURE_FLAG_EVENT_NAME, attributes: event_attributes)
    end

    # Map LaunchDarkly reason kinds to OpenTelemetry semantic convention values
    # See: https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/
    def map_reason_kind_to_semconv(kind)
      case kind
      when :OFF
        'disabled'
      when :FALLTHROUGH
        'default'
      when :TARGET_MATCH
        'targeting_match'
      when :RULE_MATCH
        'targeting_match'
      when :PREREQUISITE_FAILED
        'default'
      when :ERROR
        'error'
      else
        'unknown'
      end
    end

    def handle_evaluation_error(span, detail)
      reason = detail.reason
      return unless reason&.respond_to?(:kind) && reason.kind == :ERROR

      # Use semantic convention for error.type
      error_kind = reason.respond_to?(:error_kind) ? reason.error_kind.to_s : 'general'
      
      # Map LaunchDarkly error kinds to semantic convention values
      error_type = case error_kind.upcase
                   when 'FLAG_NOT_FOUND'
                     'flag_not_found'
                   when 'MALFORMED_FLAG'
                     'parse_error'
                   when 'USER_NOT_SPECIFIED', 'CLIENT_NOT_READY'
                     'provider_not_ready'
                   when 'WRONG_TYPE'
                     'type_mismatch'
                   else
                     'general'
                   end
      
      span.set_attribute(ERROR_TYPE, error_type)
      
      # Add human-readable error message
      error_message = "Flag evaluation error: #{error_kind}"
      span.set_attribute(ERROR_MESSAGE, error_message)
      
      span.status = OpenTelemetry::Trace::Status.error(error_message)
    end
  end
end
