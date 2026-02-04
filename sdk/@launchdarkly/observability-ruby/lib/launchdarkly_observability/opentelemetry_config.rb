# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'
require 'opentelemetry/instrumentation/all'
require 'opentelemetry/semantic_conventions'

module LaunchDarklyObservability
  # Configures OpenTelemetry SDK with appropriate providers and exporters
  # for traces, logs, and metrics.
  #
  # This class handles the setup of:
  # - Tracer provider with OTLP exporter and batch processing
  # - Logger provider with OTLP log exporter (if available)
  # - Meter provider with OTLP metrics exporter (if available)
  # - Auto-instrumentation for Rails, ActiveRecord, Net::HTTP, etc.
  #
  class OpenTelemetryConfig
    # Default batch processor configuration
    BATCH_SCHEDULE_DELAY_MS = 1000
    BATCH_MAX_EXPORT_SIZE = 128
    BATCH_MAX_QUEUE_SIZE = 1024

    # Metrics export interval
    METRICS_EXPORT_INTERVAL_MS = 60_000

    # @return [String] The LaunchDarkly project ID
    attr_reader :project_id

    # @return [String] The OTLP endpoint
    attr_reader :otlp_endpoint

    # @return [String] The deployment environment
    attr_reader :environment

    # Initialize OpenTelemetry configuration
    #
    # @param project_id [String] LaunchDarkly project ID
    # @param otlp_endpoint [String] OTLP collector endpoint
    # @param environment [String] Deployment environment name
    # @param sdk_metadata [LaunchDarkly::Interfaces::Plugins::SdkMetadata, nil]
    # @param options [Hash] Additional options
    def initialize(project_id:, otlp_endpoint:, environment:, sdk_metadata: nil, **options)
      @project_id = project_id
      @otlp_endpoint = otlp_endpoint
      @environment = environment
      @sdk_metadata = sdk_metadata
      @options = options
      @configured = false
      @logger_provider = nil
      @meter_provider = nil
    end

    # Configure OpenTelemetry SDK
    #
    # Sets up tracer provider with OTLP exporter, and optionally
    # logger and meter providers if the required gems are available.
    def configure
      return if @configured

      configure_traces if @options.fetch(:enable_traces, true)
      configure_logs if @options.fetch(:enable_logs, true)
      configure_metrics if @options.fetch(:enable_metrics, true)

      setup_shutdown_hook

      @configured = true
    end

    # Flush all pending telemetry data
    def flush
      OpenTelemetry.tracer_provider&.force_flush
      @logger_provider&.force_flush
      @meter_provider&.force_flush
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error flushing telemetry: #{e.message}"
    end

    # Shutdown all providers
    def shutdown
      OpenTelemetry.tracer_provider&.shutdown
      @logger_provider&.shutdown
      @meter_provider&.shutdown
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error shutting down telemetry: #{e.message}"
    end

    private

    # Configure OpenTelemetry traces with OTLP exporter
    def configure_traces
      OpenTelemetry::SDK.configure do |c|
        c.resource = create_resource
        c.add_span_processor(create_batch_span_processor)

        # Enable auto-instrumentation
        configure_instrumentations(c)
      end
    end

    # Configure auto-instrumentations
    def configure_instrumentations(config)
      instrumentation_config = @options.fetch(:instrumentations, {})

      if instrumentation_config.empty?
        # Use all available instrumentations with sensible defaults
        config.use_all(
          'OpenTelemetry::Instrumentation::Rails' => { enable_recognize_route: true },
          'OpenTelemetry::Instrumentation::ActiveRecord' => { db_statement: :include },
          'OpenTelemetry::Instrumentation::Net::HTTP' => { untraced_hosts: [] },
          'OpenTelemetry::Instrumentation::Rack' => { untraced_endpoints: ['/health', '/healthz', '/ready'] }
        )
      else
        config.use_all(instrumentation_config)
      end
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error configuring instrumentations: #{e.message}"
    end

    # Configure OpenTelemetry logs with OTLP exporter
    def configure_logs
      # Check if logs SDK is available
      return unless logs_sdk_available?

      require 'opentelemetry-logs-sdk'
      require 'opentelemetry/exporter/otlp/logs'

      @logger_provider = OpenTelemetry::SDK::Logs::LoggerProvider.new(resource: create_resource)

      logs_processor = OpenTelemetry::SDK::Logs::Export::BatchLogRecordProcessor.new(
        create_logs_exporter,
        schedule_delay: BATCH_SCHEDULE_DELAY_MS
      )

      @logger_provider.add_log_record_processor(logs_processor)

      # Set global logger provider if the method exists
      OpenTelemetry.logger_provider = @logger_provider if OpenTelemetry.respond_to?(:logger_provider=)
    rescue LoadError
      # Logs SDK not available, skip log configuration
      nil
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error configuring logs: #{e.message}"
    end

    # Configure OpenTelemetry metrics with OTLP exporter
    def configure_metrics
      # Check if metrics SDK is available
      return unless metrics_sdk_available?

      require 'opentelemetry-metrics-sdk'
      require 'opentelemetry/exporter/otlp/metrics'

      metric_reader = OpenTelemetry::SDK::Metrics::Export::PeriodicMetricReader.new(
        create_metrics_exporter,
        export_interval_millis: METRICS_EXPORT_INTERVAL_MS
      )

      @meter_provider = OpenTelemetry::SDK::Metrics::MeterProvider.new(
        resource: create_resource,
        metric_readers: [metric_reader]
      )

      # Set global meter provider if the method exists
      OpenTelemetry.meter_provider = @meter_provider if OpenTelemetry.respond_to?(:meter_provider=)
    rescue LoadError
      # Metrics SDK not available, skip metrics configuration
      nil
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error configuring metrics: #{e.message}"
    end

    # Create OpenTelemetry resource with LaunchDarkly attributes
    def create_resource
      attrs = {
        PROJECT_ID_ATTRIBUTE => @project_id,
        SDK_NAME_ATTRIBUTE => 'opentelemetry',
        SDK_VERSION_ATTRIBUTE => OpenTelemetry::SDK::VERSION,
        SDK_LANGUAGE_ATTRIBUTE => 'ruby',
        DISTRO_NAME_ATTRIBUTE => 'launchdarkly-observability-ruby',
        DISTRO_VERSION_ATTRIBUTE => LaunchDarklyObservability::VERSION,
        OpenTelemetry::SemanticConventions::Resource::DEPLOYMENT_ENVIRONMENT => @environment
      }

      # Add service name
      service_name = @options[:service_name] || infer_service_name
      attrs[OpenTelemetry::SemanticConventions::Resource::SERVICE_NAME] = service_name if service_name

      # Add service version
      service_version = @options[:service_version]
      attrs[OpenTelemetry::SemanticConventions::Resource::SERVICE_VERSION] = service_version if service_version

      # Add SDK metadata if available
      if @sdk_metadata
        attrs['launchdarkly.sdk.name'] = @sdk_metadata.name if @sdk_metadata.respond_to?(:name)
        attrs['launchdarkly.sdk.version'] = @sdk_metadata.version if @sdk_metadata.respond_to?(:version)
      end

      OpenTelemetry::SDK::Resources::Resource.create(attrs)
    end

    # Create batch span processor with OTLP exporter
    def create_batch_span_processor
      OpenTelemetry::SDK::Trace::Export::BatchSpanProcessor.new(
        create_trace_exporter,
        schedule_delay: BATCH_SCHEDULE_DELAY_MS,
        max_export_batch_size: BATCH_MAX_EXPORT_SIZE,
        max_queue_size: BATCH_MAX_QUEUE_SIZE
      )
    end

    # Create OTLP trace exporter
    def create_trace_exporter
      OpenTelemetry::Exporter::OTLP::Exporter.new(
        endpoint: "#{@otlp_endpoint}/v1/traces",
        compression: 'gzip'
      )
    end

    # Create OTLP logs exporter
    def create_logs_exporter
      OpenTelemetry::Exporter::OTLP::Logs::LogsExporter.new(
        endpoint: "#{@otlp_endpoint}/v1/logs",
        compression: 'gzip'
      )
    end

    # Create OTLP metrics exporter
    def create_metrics_exporter
      OpenTelemetry::Exporter::OTLP::Metrics::MetricsExporter.new(
        endpoint: "#{@otlp_endpoint}/v1/metrics",
        compression: 'gzip'
      )
    end

    # Infer service name from Rails or environment
    def infer_service_name
      if defined?(::Rails) && ::Rails.respond_to?(:application)
        app_class = ::Rails.application.class
        if app_class.respond_to?(:module_parent_name)
          app_class.module_parent_name.underscore
        else
          app_class.parent_name&.underscore
        end
      else
        ENV.fetch('OTEL_SERVICE_NAME', nil)
      end
    end

    # Check if logs SDK gem is available
    def logs_sdk_available?
      Gem::Specification.find_by_name('opentelemetry-logs-sdk')
      true
    rescue Gem::MissingSpecError
      false
    end

    # Check if metrics SDK gem is available
    def metrics_sdk_available?
      Gem::Specification.find_by_name('opentelemetry-metrics-sdk')
      true
    rescue Gem::MissingSpecError
      false
    end

    # Setup graceful shutdown hook
    def setup_shutdown_hook
      at_exit { shutdown }
    end
  end
end
