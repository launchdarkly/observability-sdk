require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

# Set LD_LAZY_INIT=1 to defer LaunchDarkly client creation until first use
# (see app/models/lazy_ld_client.rb) instead of creating it here during boot. This
# mirrors apps that build the client lazily — e.g. from a model on first request
# — and exercises the Railtie's boot-time auto-instrumentation install path.
unless ENV['LD_LAZY_INIT']
  observability_plugin = LaunchDarklyObservability::Plugin.new(
    otlp_endpoint: ENV.fetch('OTEL_EXPORTER_OTLP_ENDPOINT', LaunchDarklyObservability::DEFAULT_ENDPOINT),
    service_name: 'rails-demo-app',
    service_version: '1.0.0'
  )

  sdk_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY') do
    Rails.logger.warn '[LaunchDarkly] LAUNCHDARKLY_SDK_KEY not set, client will not connect'
    ''
  end

  config = LaunchDarkly::Config.new(plugins: [observability_plugin])
  Rails.configuration.ld_client = LaunchDarkly::LDClient.new(sdk_key, config)
end
