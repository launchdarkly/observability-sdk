require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

observability_plugin = LaunchDarklyObservability::Plugin.new(
  otlp_endpoint: ENV.fetch('OTEL_EXPORTER_OTLP_ENDPOINT', LaunchDarklyObservability::DEFAULT_ENDPOINT),
  service_name: 'launchdarkly-ruby-demo-backend',
  service_version: '1.0.0'
)

sdk_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY') do
  Rails.logger.warn '[LaunchDarkly] LAUNCHDARKLY_SDK_KEY not set, client will not connect'
  nil
end

config = LaunchDarkly::Config.new(plugins: [observability_plugin])

if sdk_key
  Rails.configuration.ld_client = LaunchDarkly::LDClient.new(sdk_key, config)
  at_exit { Rails.configuration.ld_client&.close }
else
  Rails.configuration.ld_client = nil
end
