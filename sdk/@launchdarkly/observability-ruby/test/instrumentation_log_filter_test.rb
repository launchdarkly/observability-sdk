# frozen_string_literal: true

require 'test_helper'

# Unit tests for the install-log filter. The failure-capture path
# (FAILED_PATTERN + the single summary warning) only fires in the pre-fix red
# state — a Rails version below an instrumentation's floor — so the green e2e
# suite never exercises it. These tests cover it directly.
class InstrumentationLogFilterTest < Minitest::Test
  Filter = LaunchDarklyObservability::InstrumentationLogFilter

  # Records what the real logger was asked to emit, so we can assert which
  # messages the filter forwarded vs suppressed.
  class FakeLogger
    attr_reader :messages

    def initialize
      @messages = []
    end

    %i[debug info warn error fatal unknown].each do |level|
      define_method(level) { |message = nil, &block| @messages << [level, message || block&.call] }
    end

    def add(_severity, message = nil, progname = nil, &block)
      @messages << [:add, message || progname || block&.call]
    end

    # Used to prove unknown methods are delegated.
    def level
      :delegated_level
    end
  end

  def test_capture_failures_restores_the_original_logger
    original = OpenTelemetry.logger
    Filter.capture_failures { OpenTelemetry.logger.info('noop') }
    assert_same original, OpenTelemetry.logger
  end

  def test_capture_failures_restores_logger_even_when_block_raises
    original = OpenTelemetry.logger
    assert_raises(RuntimeError) do
      Filter.capture_failures { raise 'boom' }
    end
    assert_same original, OpenTelemetry.logger
  end

  def test_capture_failures_collects_failed_instrumentation_names
    failed = Filter.capture_failures do
      OpenTelemetry.logger.warn('Instrumentation: OpenTelemetry::Instrumentation::ActionView failed to install')
      OpenTelemetry.logger.warn('Instrumentation: OpenTelemetry::Instrumentation::ActiveRecord failed to install')
      OpenTelemetry.logger.info('OpenTelemetry::Instrumentation::Rack was successfully installed')
      OpenTelemetry.logger.warn('an unrelated warning')
    end

    assert_equal %w[OpenTelemetry::Instrumentation::ActionView OpenTelemetry::Instrumentation::ActiveRecord], failed
  end

  def test_suppresses_install_chatter_but_forwards_real_messages
    fake = FakeLogger.new
    failed = []
    filter = Filter.new(fake, failed)

    filter.warn('Instrumentation: OpenTelemetry::Instrumentation::ActionView failed to install')
    filter.info('OpenTelemetry::Instrumentation::Rack was successfully installed')
    filter.warn('a real warning')

    # Only the non-chatter message reaches the delegate.
    assert_equal [[:warn, 'a real warning']], fake.messages
    # ...and the failure was recorded as a side effect.
    assert_equal ['OpenTelemetry::Instrumentation::ActionView'], failed
  end

  def test_add_path_is_filtered_too
    fake = FakeLogger.new
    failed = []
    filter = Filter.new(fake, failed)

    filter.add(Logger::WARN, 'Instrumentation: OpenTelemetry::Instrumentation::ActiveJob failed to install')
    filter.add(Logger::INFO, 'something worth keeping')

    assert_equal [[:add, 'something worth keeping']], fake.messages
    assert_equal ['OpenTelemetry::Instrumentation::ActiveJob'], failed
  end

  def test_block_form_messages_are_filtered
    fake = FakeLogger.new
    failed = []
    filter = Filter.new(fake, failed)

    filter.warn { 'Instrumentation: OpenTelemetry::Instrumentation::ActionMailer failed to install' }

    assert_empty fake.messages
    assert_equal ['OpenTelemetry::Instrumentation::ActionMailer'], failed
  end

  def test_delegates_unknown_methods
    fake = FakeLogger.new
    filter = Filter.new(fake, [])

    assert_equal :delegated_level, filter.level
    assert_respond_to filter, :level
  end

  def test_failure_warning_is_a_single_summary_with_unique_stripped_names
    warning = Filter.failure_warning(
      %w[
        OpenTelemetry::Instrumentation::ActionView
        OpenTelemetry::Instrumentation::ActionView
        OpenTelemetry::Instrumentation::ActiveRecord
      ]
    )

    assert_includes warning, '2 OpenTelemetry instrumentation(s) could not attach'
    assert_includes warning, 'ActionView, ActiveRecord'
    # The verbose OTel namespace prefix is stripped from the user-facing summary.
    refute_includes warning, 'OpenTelemetry::Instrumentation::'
  end
end
