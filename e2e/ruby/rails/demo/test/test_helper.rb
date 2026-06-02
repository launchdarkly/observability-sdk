# frozen_string_literal: true

ENV['RAILS_ENV'] ||= 'test'

# The observability plugin only configures OpenTelemetry (and installs the Rails
# auto-instrumentation) when the LaunchDarkly client registers it, which requires
# a non-empty SDK key. Set a dummy key BEFORE the app boots so the instrumentation
# attaches during initialization. The key is invalid, so the client never connects
# (background connection attempts fail gracefully and do not affect tests).
ENV['LAUNCHDARKLY_SDK_KEY'] ||= 'sdk-test-0000000000000000000000'

require_relative '../config/environment'
require 'rails/test_help'

module ActiveSupport
  class TestCase
    # Run tests in parallel with specified workers
    parallelize(workers: :number_of_processors)

    # Setup all fixtures in test/fixtures/*.yml for all tests in alphabetical order.
    fixtures :all

    # Add more helper methods to be used by all tests here...
  end
end
