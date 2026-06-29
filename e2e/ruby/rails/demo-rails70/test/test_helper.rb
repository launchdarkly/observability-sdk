# frozen_string_literal: true

ENV['RAILS_ENV'] ||= 'test'

# The observability plugin only configures OpenTelemetry (and installs the Rails
# auto-instrumentation) when the LaunchDarkly client registers it, which requires
# a non-empty SDK key. Set a dummy key BEFORE the app boots so the instrumentation
# attaches during initialization. The key is invalid, so the client never connects
# (background connection attempts fail gracefully and do not affect tests).
ENV['LAUNCHDARKLY_SDK_KEY'] ||= 'sdk-test-0000000000000000000000'

# Point the OTLP exporter at the in-process sink (test/support/otlp_sink.rb)
# BEFORE the app boots — the plugin reads this when it builds the exporters at
# boot. This keeps the E2E test fully self-contained: no external collector,
# no network egress, no LaunchDarkly backend.
OTLP_SINK_PORT = (ENV['OTLP_SINK_PORT'] || '4327').to_i
ENV['OTEL_EXPORTER_OTLP_ENDPOINT'] ||= "http://127.0.0.1:#{OTLP_SINK_PORT}"

require_relative '../config/environment'
require 'rails/test_help'
require_relative 'support/otlp_sink'

# Start the sink once for the whole suite and tear it down at exit.
OTLP_SINK = OtlpSink::Server.new(port: OTLP_SINK_PORT).start
Minitest.after_run { OTLP_SINK.stop }

module ActiveSupport
  class TestCase
    # Run tests in a single process: the OTLP sink binds a port in THIS process,
    # so forked parallel workers would not share its collected telemetry.
    parallelize(workers: 1)

    # Setup all fixtures in test/fixtures/*.yml for all tests in alphabetical order.
    fixtures :all

    # Add more helper methods to be used by all tests here...
  end
end
