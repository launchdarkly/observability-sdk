# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'
require 'opentelemetry/instrumentation/all'
require 'opentelemetry/semantic_conventions'

require_relative 'launchdarkly_observability/version'
require_relative 'launchdarkly_observability/hook'
require_relative 'launchdarkly_observability/opentelemetry_config'
require_relative 'launchdarkly_observability/plugin'
require_relative 'launchdarkly_observability/source_context'

require_relative 'launchdarkly_observability/middleware'
require_relative 'launchdarkly_observability/rails'

module LaunchDarklyObservability
  # Default OTLP endpoint for LaunchDarkly Observability
  DEFAULT_ENDPOINT = 'https://otel.observability.app.launchdarkly.com:4318'

  # Resource attribute keys
  PROJECT_ID_ATTRIBUTE = 'launchdarkly.project_id'
  SDK_NAME_ATTRIBUTE = 'telemetry.sdk.name'
  SDK_VERSION_ATTRIBUTE = 'telemetry.sdk.version'
  SDK_LANGUAGE_ATTRIBUTE = 'telemetry.sdk.language'
  DISTRO_NAME_ATTRIBUTE = 'telemetry.distro.name'
  DISTRO_VERSION_ATTRIBUTE = 'telemetry.distro.version'

  # OpenTelemetry semantic convention attribute keys for feature flags
  # See: https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/

  # Standard semantic conventions (Release Candidate)
  FEATURE_FLAG_KEY = 'feature_flag.key'
  FEATURE_FLAG_PROVIDER_NAME = 'feature_flag.provider.name'
  FEATURE_FLAG_CONTEXT_ID = 'feature_flag.context.id'
  FEATURE_FLAG_RESULT_VALUE = 'feature_flag.result.value'
  FEATURE_FLAG_RESULT_VARIANT = 'feature_flag.result.variant'
  FEATURE_FLAG_RESULT_REASON = 'feature_flag.result.reason'
  FEATURE_FLAG_SET_ID = 'feature_flag.set.id'
  FEATURE_FLAG_VERSION = 'feature_flag.version'
  ERROR_TYPE = 'error.type'
  ERROR_MESSAGE = 'error.message'

  # LaunchDarkly-specific custom attributes (not in OTel spec)
  LD_EVALUATION_METHOD = 'launchdarkly.evaluation.method'
  LD_EVALUATION_DURATION_MS = 'launchdarkly.evaluation.duration_ms'
  LD_CONTEXT_KIND = 'launchdarkly.context.kind'
  LD_CONTEXT_KEY = 'launchdarkly.context.key'
  LD_REASON_KIND = 'launchdarkly.reason.kind'
  LD_REASON_RULE_INDEX = 'launchdarkly.reason.rule_index'
  LD_REASON_RULE_ID = 'launchdarkly.reason.rule_id'
  LD_REASON_PREREQUISITE_KEY = 'launchdarkly.reason.prerequisite_key'
  LD_REASON_IN_EXPERIMENT = 'launchdarkly.reason.in_experiment'
  LD_REASON_ERROR_KIND = 'launchdarkly.reason.error_kind'

  class << self
    # @return [Plugin, nil] The current plugin instance
    attr_reader :instance

    # Initialize the observability plugin
    #
    # @param project_id [String, nil] LaunchDarkly project ID (optional - SDK key will be extracted from client if not provided)
    # @param sdk_key [String, nil] LaunchDarkly SDK key (optional - will be extracted from client if not provided)
    # @param options [Hash] Additional configuration options
    # @option options [String] :otlp_endpoint Custom OTLP endpoint URL
    # @option options [String] :environment Deployment environment (optional - inferred from SDK key by default)
    # @option options [String] :service_name Service name for traces
    # @option options [String] :service_version Service version
    # @option options [Hash] :instrumentations Configuration for auto-instrumentations
    # @return [Plugin] The initialized plugin
    def init(project_id: nil, sdk_key: nil, **options)
      @instance = Plugin.new(project_id: project_id, sdk_key: sdk_key, **options)
    end

    # Check if the plugin has been initialized
    #
    # @return [Boolean] true if initialized
    def initialized?
      !@instance.nil?
    end

    # Create a custom span for manual instrumentation
    #
    # This method matches the OpenTelemetry API naming convention for consistency.
    #
    # @param name [String] The span name
    # @param attributes [Hash] Optional span attributes
    # @yield [span] Block to execute within the span context
    # @return The result of the block
    #
    # @example Create a custom span
    #   LaunchDarklyObservability.in_span('database-query') do |span|
    #     span.set_attribute('db.table', 'users')
    #     perform_query
    #   end
    def in_span(name, attributes: {})
      unless defined?(OpenTelemetry) && OpenTelemetry.tracer_provider
        return yield if block_given?
        return
      end

      tracer = OpenTelemetry.tracer_provider.tracer(
        'launchdarkly-observability',
        LaunchDarklyObservability::VERSION
      )

      tracer.in_span(name, attributes: attributes) do |span|
        yield(span) if block_given?
      end
    end

    # Record an exception in the current span
    #
    # @param exception [Exception] The exception to record
    # @param attributes [Hash] Additional attributes
    #
    # @example Record an exception
    #   begin
    #     risky_operation
    #   rescue => e
    #     LaunchDarklyObservability.record_exception(e, foo: 'bar')
    #     raise
    #   end
    def record_exception(exception, attributes: {})
      return unless defined?(OpenTelemetry)

      span = OpenTelemetry::Trace.current_span
      return unless span

      extra_attributes = {}
      structured_stacktrace = SourceContext.build_structured_stacktrace(exception)
      if structured_stacktrace
        extra_attributes['exception.structured_stacktrace'] = structured_stacktrace.to_json
      end

      span.record_exception(exception, attributes: extra_attributes.merge(attributes))
      span.status = OpenTelemetry::Trace::Status.error(exception.message)
    end

    # Get the current trace ID
    #
    # @return [String, nil] The current trace ID in hex format
    #
    # @example Get trace ID for logging
    #   trace_id = LaunchDarklyObservability.current_trace_id
    #   logger.info "Processing request: #{trace_id}"
    def current_trace_id
      return nil unless defined?(OpenTelemetry)

      span = OpenTelemetry::Trace.current_span
      return nil unless span&.context&.valid?

      span.context.hex_trace_id
    end

    # Flush all pending telemetry data
    def flush
      @instance&.flush
    end

    # Shutdown the plugin and flush remaining data
    def shutdown
      @instance&.shutdown
      @instance = nil
    end
  end
end
