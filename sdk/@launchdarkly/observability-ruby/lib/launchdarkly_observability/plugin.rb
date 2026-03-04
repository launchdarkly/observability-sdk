# frozen_string_literal: true

require 'launchdarkly-server-sdk'

module LaunchDarklyObservability
  # LaunchDarkly SDK Plugin that provides observability instrumentation.
  #
  # This plugin integrates with the LaunchDarkly Ruby SDK to automatically
  # instrument flag evaluations with OpenTelemetry traces, logs, and metrics.
  #
  # @example Basic usage (SDK key and environment automatically extracted)
  #   plugin = LaunchDarklyObservability::Plugin.new
  #   config = LaunchDarkly::Config.new(plugins: [plugin])
  #   client = LaunchDarkly::LDClient.new(ENV['LAUNCHDARKLY_SDK_KEY'], config)
  #
  class Plugin
    include LaunchDarkly::Interfaces::Plugins::Plugin

    # @return [String] The LaunchDarkly project ID
    attr_reader :project_id

    # @return [String] The OTLP endpoint URL
    attr_reader :otlp_endpoint

    # @return [String] The deployment environment
    attr_reader :environment

    # @return [Hash] Additional options
    attr_reader :options

    # Initialize a new observability plugin
    #
    # @param project_id [String, nil] LaunchDarkly project ID for routing telemetry.
    #   If not provided, the SDK key from the client will be used automatically.
    # @param sdk_key [String, nil] LaunchDarkly SDK key (optional - will be extracted from client if not provided).
    #   The backend will derive the project and environment from the SDK key.
    # @param otlp_endpoint [String] OTLP collector endpoint (default: LaunchDarkly's endpoint)
    # @param environment [String, nil] Deployment environment name (optional - inferred from SDK key by default).
    #   Only specify this for advanced scenarios like deployment-specific suffixes (e.g., 'production-canary').
    # @param options [Hash] Additional configuration options
    # @option options [String] :service_name Service name for resource attributes
    # @option options [String] :service_version Service version for resource attributes
    # @option options [Hash] :instrumentations Configuration for OpenTelemetry auto-instrumentations
    # @option options [Boolean] :enable_traces Enable trace instrumentation (default: true)
    # @option options [Boolean] :enable_logs Enable log instrumentation (default: true)
    # @option options [Boolean] :enable_metrics Enable metrics instrumentation (default: true)
    def initialize(project_id: nil, sdk_key: nil, otlp_endpoint: DEFAULT_ENDPOINT, environment: nil, **options)
      @project_id = project_id || sdk_key
      @otlp_endpoint = otlp_endpoint
      @environment = environment&.to_s
      @options = default_options.merge(options)
      @hook = Hook.new
      @otel_config = nil
      @registered = false
    end

    # Returns metadata about this plugin
    #
    # @return [LaunchDarkly::Interfaces::Plugins::PluginMetadata]
    def metadata
      LaunchDarkly::Interfaces::Plugins::PluginMetadata.new('launchdarkly-observability')
    end

    # Returns the hooks provided by this plugin
    #
    # @param _environment_metadata [LaunchDarkly::Interfaces::Plugins::EnvironmentMetadata]
    # @return [Array<LaunchDarkly::Interfaces::Hooks::Hook>]
    def get_hooks(_environment_metadata)
      [@hook]
    end

    # Register the plugin with the LaunchDarkly client
    #
    # This method is called during SDK initialization. It sets up the
    # OpenTelemetry SDK with appropriate providers and exporters.
    #
    # @param _client [LaunchDarkly::LDClient] The LaunchDarkly client instance
    # @param environment_metadata [LaunchDarkly::Interfaces::Plugins::EnvironmentMetadata]
    def register(_client, environment_metadata)
      return if @registered

      # Use provided project_id, or extract SDK key from the client
      project_id = @project_id || environment_metadata&.sdk_key

      if project_id.nil? || project_id.empty?
        raise ArgumentError, 'Unable to determine project_id: no project_id or sdk_key provided, and client SDK key is unavailable'
      end

      @otel_config = OpenTelemetryConfig.new(
        project_id: project_id,
        otlp_endpoint: @otlp_endpoint,
        environment: @environment,
        sdk_metadata: environment_metadata&.sdk,
        **@options
      )

      @otel_config.configure

      @registered = true
    end

    # Check if the plugin has been registered
    #
    # @return [Boolean]
    def registered?
      @registered
    end

    # Flush all pending telemetry data
    def flush
      @otel_config&.flush
    end

    # Shutdown the plugin and flush remaining data
    def shutdown
      @otel_config&.shutdown
      @registered = false
    end

    private

    def default_options
      {
        enable_traces: true,
        enable_logs: true,
        enable_metrics: true,
        service_name: nil,
        service_version: nil,
        instrumentations: {}
      }
    end
  end
end
