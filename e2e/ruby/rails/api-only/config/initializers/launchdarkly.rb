# frozen_string_literal: true

require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

# Create observability plugin (SDK key and environment automatically inferred)
observability_plugin = LaunchDarklyObservability::Plugin.new(
  otlp_endpoint: ENV.fetch('OTEL_EXPORTER_OTLP_ENDPOINT', 'http://localhost:4318'),
  service_name: 'launchdarkly-ruby-api-only',
  service_version: '1.0.0'
)

# Initialize LaunchDarkly client with real SDK key
sdk_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY') do
  Rails.logger.warn '[LaunchDarkly] LAUNCHDARKLY_SDK_KEY not set, client will not connect'
  nil
end

config = LaunchDarkly::Config.new(
  plugins: [observability_plugin]
)

Rails.configuration.ld_client = LaunchDarkly::LDClient.new(sdk_key, config)

at_exit { Rails.configuration.ld_client.close }

Rails.logger.info '[LaunchDarkly] Client initialized with observability plugin'
