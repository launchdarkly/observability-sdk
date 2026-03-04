# frozen_string_literal: true

require 'json'
require 'launchdarkly-server-sdk'

module LaunchDarklyObservability
  # Evaluation hook that instruments LaunchDarkly flag evaluations with OpenTelemetry spans and events.
  #
  # This hook creates spans for each flag evaluation, capturing:
  # - Flag key and provider
  # - Context information
  # - Evaluation result (value, variation index)
  # - Any errors
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

    TRACER_NAME = 'launchdarkly-ruby'
    SPAN_NAME = 'evaluation'
    FEATURE_FLAG_EVENT_NAME = 'feature_flag'

    # @return [LaunchDarkly::Interfaces::Hooks::Metadata]
    def metadata
      LaunchDarkly::Interfaces::Hooks::Metadata.new('launchdarkly-observability-hook')
    end

    # Called before flag evaluation.
    # Creates an OpenTelemetry span and captures initial context information.
    #
    # @param series_context [LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext]
    # @param data [Hash] Data passed between hook stages
    # @return [Hash] Updated data hash with span information
    def before_evaluation(series_context, data)
      return data unless opentelemetry_available?

      tracer = OpenTelemetry.tracer_provider.tracer(TRACER_NAME, LaunchDarklyObservability::VERSION)

      span = tracer.start_span(SPAN_NAME, attributes: build_before_attributes(series_context))

      data.merge(__ld_observability_span: span)
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error in before_evaluation: #{e.message}"
      data
    end

    # Called after flag evaluation.
    # Completes the span with evaluation results and a "feature_flag" event.
    #
    # @param series_context [LaunchDarkly::Interfaces::Hooks::EvaluationSeriesContext]
    # @param data [Hash] Data passed between hook stages
    # @param detail [LaunchDarkly::EvaluationDetail] The evaluation result
    # @return [Hash] Updated data hash
    def after_evaluation(series_context, data, detail)
      span = data[:__ld_observability_span]
      return data unless span

      add_result_attributes(span, detail)
      handle_evaluation_error(span, detail)
      add_feature_flag_event(span, series_context, detail)

      span.finish

      data
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error in after_evaluation: #{e.message}"
      span&.finish
      data
    end

    private

    def opentelemetry_available?
      defined?(OpenTelemetry) && OpenTelemetry.tracer_provider
    end

    def build_before_attributes(series_context)
      attrs = {
        FEATURE_FLAG_KEY => series_context.key,
        FEATURE_FLAG_PROVIDER_NAME => 'LaunchDarkly'
      }

      context = series_context.context
      if context
        attrs[FEATURE_FLAG_CONTEXT_ID] = context.fully_qualified_key
      end

      attrs
    end

    def serialize_value(value)
      case value
      when String, Numeric, TrueClass, FalseClass, NilClass
        value.to_s
      when Hash, Array
        value.to_json
      else
        value.to_s
      end
    end

    def add_result_attributes(span, detail)
      if detail.variation_index
        span.set_attribute(FEATURE_FLAG_RESULT_VARIANT, detail.variation_index.to_s)
        span.set_attribute(FEATURE_FLAG_RESULT_VARIATION_INDEX, detail.variation_index.to_s)
      end

      span.set_attribute(FEATURE_FLAG_RESULT_VALUE, serialize_value(detail.value))
    end

    # Adds a "feature_flag" event with evaluation results.
    # Attribute names match the ClickHouse schema and other LaunchDarkly SDKs.
    def add_feature_flag_event(span, series_context, detail)
      event_attributes = {
        FEATURE_FLAG_KEY => series_context.key,
        FEATURE_FLAG_PROVIDER_NAME => 'LaunchDarkly'
      }

      context = series_context.context
      if context
        event_attributes[FEATURE_FLAG_CONTEXT_ID] = context.fully_qualified_key
      end

      if detail.variation_index
        event_attributes[FEATURE_FLAG_RESULT_VARIANT] = detail.variation_index.to_s
        event_attributes[FEATURE_FLAG_RESULT_VARIATION_INDEX] = detail.variation_index.to_s
      end

      event_attributes[FEATURE_FLAG_RESULT_VALUE] = serialize_value(detail.value)

      reason = detail.reason
      if reason&.respond_to?(:kind)
        event_attributes[FEATURE_FLAG_RESULT_REASON_KIND] = reason.kind.to_s

        add_reason_event_details(event_attributes, reason)
      end

      span.add_event(FEATURE_FLAG_EVENT_NAME, attributes: event_attributes)
    end

    def add_reason_event_details(event_attributes, reason)
      if reason.respond_to?(:in_experiment) && !reason.in_experiment.nil?
        event_attributes[FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT] = reason.in_experiment
      end

      case reason.kind
      when :RULE_MATCH
        if reason.respond_to?(:rule_index) && reason.rule_index
          event_attributes[FEATURE_FLAG_RESULT_REASON_RULE_INDEX] = reason.rule_index
        end
        if reason.respond_to?(:rule_id) && reason.rule_id
          event_attributes[FEATURE_FLAG_RESULT_REASON_RULE_ID] = reason.rule_id
        end
      when :ERROR
        if reason.respond_to?(:error_kind) && reason.error_kind
          event_attributes[FEATURE_FLAG_RESULT_REASON_ERROR_KIND] = reason.error_kind.to_s
        end
      end
    end

    def handle_evaluation_error(span, detail)
      reason = detail.reason
      return unless reason&.respond_to?(:kind) && reason.kind == :ERROR

      error_kind = reason.respond_to?(:error_kind) ? reason.error_kind.to_s : 'general'

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

      error_message = "Flag evaluation error: #{error_kind}"
      span.set_attribute(ERROR_MESSAGE, error_message)

      span.status = OpenTelemetry::Trace::Status.error(error_message)
    end
  end
end
