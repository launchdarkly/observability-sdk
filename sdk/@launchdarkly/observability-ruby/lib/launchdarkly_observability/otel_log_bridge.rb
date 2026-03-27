# frozen_string_literal: true

module LaunchDarklyObservability
  # A Logger that forwards messages to the OpenTelemetry Logs pipeline.
  #
  # Designed to be broadcast-attached to Rails.logger so every Rails log
  # entry is automatically emitted as an OTLP LogRecord with trace/span
  # correlation from the current OpenTelemetry context.
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
    def initialize(logger_provider)
      super(File::NULL)
      @otel_logger = logger_provider.logger(
        name: 'launchdarkly-observability-ruby',
        version: LaunchDarklyObservability::VERSION
      )
    end

    # Core method that debug/info/warn/error/fatal all delegate to.
    def add(severity, message = nil, progname = nil)
      severity ||= ::Logger::UNKNOWN
      return true if severity < level

      if message.nil?
        message = if block_given?
                    yield
                  else
                    progname
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

      true
    rescue StandardError
      true
    end
  end
end
