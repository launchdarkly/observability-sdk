# frozen_string_literal: true

module LaunchDarklyObservability
  # Wraps the OpenTelemetry logger to suppress the per-instrumentation install
  # chatter ("... was successfully installed" / "... failed to install") and
  # record the names of instrumentations that failed to install. Everything else
  # (including level / level=) is delegated to the real logger.
  #
  # OpenTelemetryConfig uses this to replace the SDK's flurry of
  # per-instrumentation warnings with a single actionable summary.
  class InstrumentationLogFilter
    FAILED_PATTERN = /Instrumentation: (\S+) failed to install/

    # Run the block with OpenTelemetry.logger swapped for a filter that
    # suppresses per-instrumentation install chatter, returning the names of any
    # instrumentations that reported "failed to install". The SDK installs
    # instrumentations after a configure block returns, so wrap the whole
    # OpenTelemetry::SDK.configure call — not just use_all.
    def self.capture_failures
      original = OpenTelemetry.logger
      failed = []
      OpenTelemetry.logger = new(original, failed)
      yield
      failed
    ensure
      OpenTelemetry.logger = original
    end

    # Build ONE actionable warning naming the instrumentations that could not
    # attach and how to resolve it. Telemetry that does not depend on those
    # instrumentations (flag-eval spans, manual instrumentation, logs, errors)
    # keeps working regardless.
    def self.failure_warning(failed)
      names = failed.map { |n| n.sub('OpenTelemetry::Instrumentation::', '') }.uniq
      rails = defined?(::Rails) && ::Rails.respond_to?(:version) ? " on Rails #{::Rails.version}" : ''
      "[LaunchDarklyObservability] #{names.size} OpenTelemetry instrumentation(s) could not attach" \
        "#{rails} (Ruby #{RUBY_VERSION}): #{names.join(', ')}. Those libraries will not be " \
        'auto-instrumented; flag-eval spans, manual instrumentation, logs and error capture are ' \
        'unaffected. This usually means an instrumentation gem dropped support for your framework ' \
        'version — upgrade the framework, or pin the instrumentation gem to a compatible release ' \
        '(e.g. gem "opentelemetry-instrumentation-rails", "~> 0.41").'
    end

    def initialize(delegate, failed)
      @delegate = delegate
      @failed = failed
    end

    # OTel logs installs via OpenTelemetry.logger.info / .warn, so intercept the
    # level methods (not just #add) — otherwise the calls fall through to
    # method_missing and bypass the filter.
    %i[debug info warn error fatal unknown].each do |level|
      define_method(level) do |message = nil, &block|
        forward?(message || block&.call) ? @delegate.public_send(level, message, &block) : true
      end
    end

    def add(severity, message = nil, progname = nil, &block)
      forward?(message || progname || block&.call) ? @delegate.add(severity, message, progname, &block) : true
    end

    def method_missing(name, ...)
      @delegate.send(name, ...)
    end

    def respond_to_missing?(name, include_private = false)
      @delegate.respond_to?(name, include_private) || super
    end

    private

    # Returns false when the message is install chatter that should be suppressed
    # (recording failed-instrumentation names as a side effect), true when it
    # should be forwarded to the real logger.
    def forward?(message)
      text = message.to_s
      if (match = text.match(FAILED_PATTERN))
        @failed << match[1]
        return false
      end

      !text.include?('was successfully installed')
    end
  end
end
