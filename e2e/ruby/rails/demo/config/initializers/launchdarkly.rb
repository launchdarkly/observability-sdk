# frozen_string_literal: true

require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

# Create observability plugin
observability_plugin = LaunchDarklyObservability::Plugin.new(
  project_id: ENV.fetch('LAUNCHDARKLY_PROJECT_ID', '1jdkoe52'),
  otlp_endpoint: ENV.fetch('OTEL_EXPORTER_OTLP_ENDPOINT', 'http://localhost:4318'),
  environment: Rails.env,
  service_name: 'launchdarkly-ruby-demo-backend',
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

$ld_client = LaunchDarkly::LDClient.new(sdk_key, config)

# Ensure clean shutdown on application exit
at_exit { $ld_client.close }

Rails.logger.info '[LaunchDarkly] Client initialized with observability plugin'
