require 'sinatra'
require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'
require 'logger'

# Add LaunchDarkly request tracing middleware
use LaunchDarklyObservability::Middleware

$logger = Logger.new($stdout)

# Initialize LaunchDarkly Observability
observability_plugin = LaunchDarklyObservability::Plugin.new(
  service_name: 'launchdarkly-sinatra-demo',
  service_version: '1.0.0'
)

# Initialize LaunchDarkly client
sdk_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY', 'sdk-test-key')
config = LaunchDarkly::Config.new(plugins: [observability_plugin])
$ld_client = LaunchDarkly::LDClient.new(sdk_key, config)

at_exit { $ld_client.close }

# Test event class
class TestEvent
  attr_reader :name, :data

  def initialize(name, data)
    @name = name
    @data = data
  end
end

# Routes
get '/' do
  LaunchDarklyObservability.in_span('log-event-example') do |span|
    event = TestEvent.new(
      :supporter_notified_of_shifts_change,
      {
        supporter_id: 6875,
        event_id: 34,
        edited_shifts_ids: [200, 203]
      }
    )

    span.set_attribute('event.name', event.name.to_s)
    span.set_attribute('event.supporter_id', event.data[:supporter_id])

    $logger.info(published_event: event.name, **event.data)
    $logger.info("Test log...")

    'Event logged! Check your logs.'
  end
end

# Error route to test error logging
get '/error' do
  LaunchDarklyObservability.in_span('error-example') do |span|
    begin
      $logger.error("Test error logging")
      raise "Test error"
    rescue StandardError => e
      LaunchDarklyObservability.record_exception(e)
      raise
    end
  end
end
