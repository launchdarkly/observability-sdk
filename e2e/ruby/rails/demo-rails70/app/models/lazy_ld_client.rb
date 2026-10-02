# frozen_string_literal: true

# Mirrors an application that creates the LaunchDarkly client lazily — e.g. from
# a model on first use — instead of during boot in a config/initializer. Enabled
# with LD_LAZY_INIT=1 (see config/initializers/launchdarkly.rb) and exercised by
# the lazy-init e2e test, which verifies the Railtie installs OpenTelemetry
# auto-instrumentation at boot even though the client is created afterward.
class LazyLdClient
  def self.instance
    @instance ||= begin
      plugin = LaunchDarklyObservability::Plugin.new(
        otlp_endpoint: ENV.fetch('OTEL_EXPORTER_OTLP_ENDPOINT', LaunchDarklyObservability::DEFAULT_ENDPOINT),
        service_name: 'rails7-demo-app',
        service_version: '1.0.0'
      )
      config = LaunchDarkly::Config.new(plugins: [plugin])
      LaunchDarkly::LDClient.new(ENV.fetch('LAUNCHDARKLY_SDK_KEY', ''), config)
    end
  end
end
