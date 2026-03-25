# frozen_string_literal: true

module LaunchDarklyObservability
  # A Logger that forwards messages to the OpenTelemetry Logs pipeline.
  #
  # When used as a broadcast target (Rails), pass only the logger_provider.
  # When used standalone (Sinatra, plain Ruby), pass `io:` to also write
  # to a local destination such as $stdout.
  #
  # @example Standalone usage (non-Rails)
  #   logger = LaunchDarklyObservability.logger          # writes to $stdout + OTel
  #
  # @example Manually attaching (the Railtie does this automatically)
  #   bridge = LaunchDarklyObservability::OtelLogBridge.new(logger_provider)
  #   Rails.logger.broadcast_to(bridge)   # Rails >= 7.1
  #
  class OtelLogBridge < ::Logger
    # OpenTelemetry severity numbers (base value per level).
    # See: https://opentelemetry.io/docs/specs/otel/logs/data-model/#severity-fields
    SEVERITY_NUMBER = {
      ::Logger::DEBUG   => 5,
      ::Logger::INFO    => 9,
      ::Logger::WARN    => 13,
      ::Logger::ERROR   => 17,
      ::Logger::FATAL   => 21,
      ::Logger::UNKNOWN => 0
    }.freeze

    SEVERITY_TEXT = {
      ::Logger::DEBUG   => 'DEBUG',
      ::Logger::INFO    => 'INFO',
      ::Logger::WARN    => 'WARN',
      ::Logger::ERROR   => 'ERROR',
      ::Logger::FATAL   => 'FATAL',
      ::Logger::UNKNOWN => 'UNKNOWN'
    }.freeze

    # @param logger_provider [OpenTelemetry::SDK::Logs::LoggerProvider]
    # @param io [IO, nil] Optional IO for local output (e.g. $stdout).
    #   When nil the bridge only emits to OTel (suitable for broadcast).
    def initialize(logger_provider, io: nil)
      super(File::NULL)
      @otel_logger = logger_provider.logger(
        name: 'launchdarkly-observability-ruby',
        version: LaunchDarklyObservability::VERSION
      )
      @local_logger = io ? ::Logger.new(io) : nil
    end

    # Propagate level changes to the local logger so filtering stays in sync.
    def level=(severity)
      super
      @local_logger&.level = severity
    end

    # Propagate formatter changes to the local logger.
    def formatter=(formatter)
      super
      @local_logger&.formatter = formatter
    end

    # Core method that debug/info/warn/error/fatal all delegate to.
    def add(severity, message = nil, progname = nil)
      severity ||= ::Logger::UNKNOWN
      return true if severity < level

      if message.nil?
        if block_given?
          message = yield
        else
          message = progname
          progname = nil
        end
      end

      return true if message.nil?

      attributes = {}
      if message.is_a?(Hash)
        attributes = message.each_with_object({}) { |(k, v), h| h[k.to_s] = v.to_s }
        body = message.inspect
      else
        body = message.to_s
      end

      @otel_logger.on_emit(
        body: body,
        severity_number: SEVERITY_NUMBER.fetch(severity, 0),
        severity_text: SEVERITY_TEXT.fetch(severity, 'UNKNOWN'),
        timestamp: Time.now,
        context: OpenTelemetry::Context.current,
        attributes: attributes
      )

      begin
        @local_logger&.add(severity, message, progname)
      rescue StandardError
        # Local IO failures must not propagate — OTel export already succeeded.
      end

      true
    rescue StandardError
      true
    end
  end
end
