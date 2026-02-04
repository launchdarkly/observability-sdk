# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'
require 'opentelemetry/instrumentation/all'
require 'opentelemetry/semantic_conventions'

require_relative 'launchdarkly_observability/version'
require_relative 'launchdarkly_observability/hook'
require_relative 'launchdarkly_observability/opentelemetry_config'
require_relative 'launchdarkly_observability/plugin'

# Load Rails integration if Rails is available
require_relative 'launchdarkly_observability/rails' if defined?(::Rails::Railtie)

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

  # Semantic convention attribute keys for feature flags
  FEATURE_FLAG_KEY = 'feature_flag.key'
  FEATURE_FLAG_VARIANT = 'feature_flag.variant'
  FEATURE_FLAG_PROVIDER = 'feature_flag.provider_name'

  class << self
    # @return [Plugin, nil] The current plugin instance
    attr_reader :instance

    # Initialize the observability plugin
    #
    # @param project_id [String] LaunchDarkly project ID (required)
    # @param options [Hash] Additional configuration options
    # @option options [String] :otlp_endpoint Custom OTLP endpoint URL
    # @option options [String] :environment Deployment environment name
    # @option options [String] :service_name Service name for traces
    # @option options [String] :service_version Service version
    # @option options [Hash] :instrumentations Configuration for auto-instrumentations
    # @return [Plugin] The initialized plugin
    def init(project_id:, **options)
      @instance = Plugin.new(project_id: project_id, **options)
    end

    # Check if the plugin has been initialized
    #
    # @return [Boolean] true if initialized
    def initialized?
      !@instance.nil?
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
