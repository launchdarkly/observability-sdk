# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'
require 'opentelemetry/semantic_conventions'
require_relative 'instrumentations'

module LaunchDarklyObservability
  # Configures OpenTelemetry SDK with appropriate providers and exporters
  # for traces, logs, and metrics.
  #
  # This class handles the setup of:
  # - Tracer provider with OTLP exporter and batch processing
  # - Logger provider with OTLP log exporter (included by default)
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

    # Wraps the OpenTelemetry logger to suppress the per-instrumentation install
    # chatter ("... was successfully installed" / "... failed to install") and
    # record the names of instrumentations that failed to install. Everything
    # else (including level / level=) is delegated to the real logger.
    class InstrumentationLogFilter
      FAILED_PATTERN = /Instrumentation: (\S+) failed to install/.freeze

      def initialize(delegate, failed)
        @delegate = delegate
        @failed = failed
      end

      # OTel logs installs via OpenTelemetry.logger.info / .warn, so intercept
      # the level methods (not just #add) — otherwise the calls fall through to
      # method_missing and bypass the filter.
      %i[debug info warn error fatal unknown].each do |level|
        define_method(level) do |message = nil, &block|
          forward?(message || (block && block.call)) ? @delegate.public_send(level, message, &block) : true
        end
      end

      def add(severity, message = nil, progname = nil, &block)
        forward?(message || progname || (block&.call)) ? @delegate.add(severity, message, progname, &block) : true
      end

      def method_missing(name, *args, &block)
        @delegate.send(name, *args, &block)
      end

      def respond_to_missing?(name, include_private = false)
        @delegate.respond_to?(name, include_private) || super
      end

      private

      # Returns false when the message is install chatter that should be
      # suppressed (recording failed-instrumentation names as a side effect),
      # true when it should be forwarded to the real logger.
      def forward?(message)
        text = message.to_s
        if (match = text.match(FAILED_PATTERN))
          @failed << match[1]
          return false
        end

        !text.include?('was successfully installed')
      end
    end

    # @return [String] The LaunchDarkly project ID
    attr_reader :project_id

    # @return [String] The OTLP endpoint
    attr_reader :otlp_endpoint

    # @return [String] The deployment environment
    attr_reader :environment

    # @return [OpenTelemetry::SDK::Logs::LoggerProvider, nil] The logger provider (nil if logs disabled or setup failed)
    attr_reader :logger_provider

    # Initialize OpenTelemetry configuration
    #
    # @param project_id [String] LaunchDarkly project ID
    # @param otlp_endpoint [String] OTLP collector endpoint
    # @param environment [String, nil] Deployment environment name (optional - inferred from SDK key if not provided)
    # @param sdk_metadata [LaunchDarkly::Interfaces::Plugins::SdkMetadata, nil]
    # @param options [Hash] Additional options
    def initialize(project_id:, otlp_endpoint:, environment: nil, sdk_metadata: nil, **options)
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
    # Sets up tracer provider with OTLP exporter, logger provider
    # for OTLP log export, and optionally meter provider if available.
    def configure
      return if @configured

      configure_traces if @options.fetch(:enable_traces, true)
      configure_logs if @options.fetch(:enable_logs, true)
      configure_metrics if @options.fetch(:enable_metrics, true)

      setup_shutdown_hook

      @configured = true
    end

    # Install the SDK tracer provider and auto-instrumentation WITHOUT exporters.
    #
    # Called from the Rails Railtie during boot so the Rails-family
    # instrumentations (which patch via ActiveSupport.on_load hooks that fire
    # during boot) attach even when the LaunchDarkly client — and therefore
    # #register / #configure — is created lazily afterward. Exporters are added
    # later by #configure_traces when the client registers the plugin.
    def install_instrumentation_only
      OpenTelemetry::SDK.configure do |c|
        c.resource = create_resource
        configure_instrumentations(c)
      end
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

    # Configure OpenTelemetry traces with OTLP exporter.
    #
    # If auto-instrumentation was already installed during Rails boot (see
    # LaunchDarklyObservability.install_rails_instrumentation), the SDK tracer
    # provider already exists with instrumentation attached — so we only add the
    # OTLP span exporter. Re-running OpenTelemetry::SDK.configure here would
    # replace that provider and, in the lazy-init case, drop the Rails-family
    # instrumentation that can only attach during boot.
    def configure_traces
      if LaunchDarklyObservability.instrumentation_installed_at_boot?
        OpenTelemetry.tracer_provider.add_span_processor(create_batch_span_processor)
        return
      end

      OpenTelemetry::SDK.configure do |c|
        c.resource = create_resource
        c.add_span_processor(create_batch_span_processor)

        # Enable auto-instrumentation
        configure_instrumentations(c)
      end

      warn_if_rails_instrumentation_missed
    end

    # Emit a single actionable warning when the plugin is registered after Rails
    # has finished booting and boot-time instrumentation install did not run
    # (e.g. LAUNCHDARKLY_SDK_KEY was not set in the environment at boot). In that
    # case the OTel Rails-family instrumentations log a flurry of
    # "failed to install" warnings because their load hooks have already fired.
    def warn_if_rails_instrumentation_missed
      return unless defined?(::Rails) && ::Rails.respond_to?(:application)
      return unless ::Rails.application.respond_to?(:initialized?) && ::Rails.application.initialized?

      warn '[LaunchDarklyObservability] The LaunchDarkly client was created after Rails finished ' \
           'booting, so the Rails auto-instrumentation (ActionPack, ActiveRecord, ...) could not be ' \
           'installed. To enable it, set LAUNCHDARKLY_SDK_KEY in the environment before Rails boots, ' \
           'or create the LaunchDarkly client from a config/initializer.'
    end

    # Configure auto-instrumentations with sensible defaults.
    # User-provided instrumentation config is merged on top of defaults,
    # so users only need to specify the instrumentations they want to override.
    def configure_instrumentations(config)
      # Only pass options that the instrumentations actually accept. Unknown
      # options are not fatal but emit a warning on every boot ("ignored the
      # following unknown configuration options [...]"):
      # - `enable_recognize_route` is not an option on the Rails, Rack, or
      #   ActionPack instrumentations; route-based span naming (http.route) is
      #   handled automatically by the ActionPack instrumentation.
      # - ActiveRecord has no `db_statement` option; SQL capture comes from the
      #   database adapter instrumentations (Mysql2, PG, ...) which default to
      #   obfuscating statements.
      defaults = {
        'OpenTelemetry::Instrumentation::Net::HTTP' => { untraced_hosts: [] },
        'OpenTelemetry::Instrumentation::Rack' => { untraced_endpoints: ['/health', '/healthz', '/ready'] }
      }

      user_config = @options.fetch(:instrumentations, {})

      # Replace the OTel SDK's per-instrumentation install logging (a flurry of
      # "Instrumentation: <X> failed to install" WARN lines when instrumentations
      # are incompatible with the framework version — e.g. the Rails family on a
      # Rails version below its floor) with a single, actionable summary.
      failed = with_captured_instrumentation_failures do
        config.use_all(defaults.merge(user_config))
      end
      warn_failed_instrumentations(failed) unless failed.empty?
    rescue StandardError => e
      warn "[LaunchDarklyObservability] Error configuring instrumentations: #{e.message}"
    end

    # Temporarily swap OpenTelemetry.logger for a filter that suppresses the
    # per-instrumentation install chatter and records the names of any
    # instrumentations that report "failed to install", returning them so the
    # caller can emit a single summary instead of a noisy flurry.
    def with_captured_instrumentation_failures
      original = OpenTelemetry.logger
      failed = []
      OpenTelemetry.logger = InstrumentationLogFilter.new(original, failed)
      yield
      failed
    ensure
      OpenTelemetry.logger = original
    end

    # Emit ONE actionable warning naming the instrumentations that could not
    # attach and how to resolve it. Telemetry that does not depend on those
    # instrumentations (flag-eval spans, manual instrumentation, logs, errors)
    # keeps working regardless.
    def warn_failed_instrumentations(failed)
      names = failed.map { |n| n.sub('OpenTelemetry::Instrumentation::', '') }.uniq
      rails_part = defined?(::Rails) && ::Rails.respond_to?(:version) ? " on Rails #{::Rails.version}" : ''
      warn "[LaunchDarklyObservability] #{names.size} OpenTelemetry instrumentation(s) could not " \
           "attach#{rails_part} (Ruby #{RUBY_VERSION}): #{names.join(', ')}. Those libraries will not " \
           'be auto-instrumented; flag-eval spans, manual instrumentation, logs and error capture are ' \
           'unaffected. This usually means an instrumentation gem dropped support for your framework ' \
           'version — upgrade the framework, or pin the instrumentation gem to a compatible release ' \
           '(e.g. gem "opentelemetry-instrumentation-rails", "~> 0.41").'
    end

    # Configure OpenTelemetry logs with OTLP exporter.
    # The log gems are runtime dependencies, so require should always succeed.
    # If anything goes wrong, we warn once and leave traces unaffected.
    def configure_logs
      require 'opentelemetry-logs-sdk'
      require 'opentelemetry-exporter-otlp-logs'

      @logger_provider = OpenTelemetry::SDK::Logs::LoggerProvider.new(resource: create_resource)

      logs_processor = OpenTelemetry::SDK::Logs::Export::BatchLogRecordProcessor.new(
        create_logs_exporter,
        schedule_delay: BATCH_SCHEDULE_DELAY_MS
      )

      @logger_provider.add_log_record_processor(logs_processor)

      OpenTelemetry.logger_provider = @logger_provider if OpenTelemetry.respond_to?(:logger_provider=)
    rescue LoadError => e
      warn "[LaunchDarklyObservability] Log gems not available, skipping log configuration: #{e.message}"
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
        DISTRO_VERSION_ATTRIBUTE => LaunchDarklyObservability::VERSION
      }

      # Only set deployment.environment if explicitly provided
      # Otherwise, backend infers it from the SDK key
      if @environment && !@environment.empty?
        attrs[OpenTelemetry::SemanticConventions::Resource::DEPLOYMENT_ENVIRONMENT] = @environment
      end

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

    # Check if metrics SDK gem is available
    def metrics_sdk_available?
      Gem::Specification.find_by_name('opentelemetry-metrics-sdk')
      true
    rescue Gem::MissingSpecError
      false
    end

    # Setup graceful exit hook to flush pending telemetry data.
    #
    # Only flushes (not shuts down) because frameworks like Sinatra start
    # their servers inside at_exit handlers. Since at_exit runs in LIFO
    # order, a shutdown registered after the framework's handler would
    # execute BEFORE the server starts, stopping the TracerProvider and
    # causing all spans to be non-recording for the server's lifetime.
    def setup_shutdown_hook
      at_exit { flush }
    end
  end
end
