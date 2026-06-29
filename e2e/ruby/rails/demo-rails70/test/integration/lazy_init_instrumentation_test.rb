# frozen_string_literal: true

require 'test_helper'
require 'open3'

# Regression test for lazy LaunchDarkly client initialization.
#
# When the client is created lazily (after Rails has booted) rather than in a
# config/initializer, the OTel Rails-family instrumentations used to report
# "Instrumentation: ... failed to install" — their ActiveSupport.on_load hooks
# had already fired by the time the plugin configured OpenTelemetry. The gem's
# Railtie now installs auto-instrumentation during boot, independent of when the
# client is created, so it attaches regardless.
#
# This must run in a SEPARATE process because instrumentation install is a
# one-time, global side effect: the main test suite boots with the client
# created during boot, so we boot a fresh Rails process with LD_LAZY_INIT=1 (no
# client created at boot — see config/initializers/launchdarkly.rb) and assert
# the Rails instrumentations are installed anyway.
class LazyInitInstrumentationTest < ActiveSupport::TestCase
  CHECK_SCRIPT = <<~'RUBY'
    names = %w[Rack ActionPack ActiveRecord ActiveSupport Rails]
    installed = names.all? do |n|
      Object.const_get("OpenTelemetry::Instrumentation::#{n}::Instrumentation").instance.installed?
    end
    # Creating the client lazily (post-boot) must still work without raising.
    LazyLdClient.instance
    puts(installed ? 'LAZY_INSTRUMENTATION_OK' : 'LAZY_INSTRUMENTATION_FAILED')
  RUBY

  # Guards the cross-signal service identity in the lazy-boot path. Boot-time
  # install builds the tracer provider's resource before the plugin (and its
  # service_name) exists, so traces would carry the inferred app name while
  # logs/metrics carry the configured service_name — unless configure_traces
  # refreshes the trace resource in place when the client registers lazily. OTel
  # exposes no public resource setter, so that refresh pokes a private ivar
  # (`provider.instance_variable_set(:@resource, ...)`); this asserts the wire
  # result so a future OTel SDK change to that internal can't silently desync
  # trace vs log service.name.
  SERVICE_IDENTITY_SCRIPT = <<~'RUBY'
    def service_name(provider)
      resource = provider&.instance_variable_get(:@resource)
      resource&.attribute_enumerator&.to_h&.fetch('service.name', nil)
    end

    # Before lazy init: resource built at boot install, so the inferred app name.
    boot_trace_name = service_name(OpenTelemetry.tracer_provider)

    # Lazy init registers the plugin (service_name: 'rails7-demo-app'), which must
    # refresh the trace resource and build the logger provider from the same one.
    LazyLdClient.instance

    puts "BOOT_TRACE_SERVICE_NAME=#{boot_trace_name}"
    puts "TRACE_SERVICE_NAME=#{service_name(OpenTelemetry.tracer_provider)}"
    puts "LOG_SERVICE_NAME=#{service_name(OpenTelemetry.logger_provider)}"
  RUBY

  test 'rails auto-instrumentation installs at boot even when the client is created lazily' do
    output = boot_lazy_and_run(CHECK_SCRIPT)

    assert_includes output, 'LAZY_INSTRUMENTATION_OK',
                    "Rails auto-instrumentation should install at boot in lazy mode.\n--- subprocess output ---\n#{output}"
  end

  test 'tracer and logger resources report the same service identity after lazy init' do
    output = boot_lazy_and_run(SERVICE_IDENTITY_SCRIPT)

    boot_trace = output[/^BOOT_TRACE_SERVICE_NAME=(.*)$/, 1]
    trace = output[/^TRACE_SERVICE_NAME=(.*)$/, 1]
    log = output[/^LOG_SERVICE_NAME=(.*)$/, 1]

    assert_equal 'rails7-demo-app', trace,
                 "trace resource service.name should be refreshed to the plugin's service_name " \
                 "after lazy init.\n--- subprocess output ---\n#{output}"
    assert_equal log, trace,
                 'trace and log resources should report the same service.name after lazy init ' \
                 "(trace=#{trace.inspect} log=#{log.inspect}).\n--- subprocess output ---\n#{output}"
    refute_equal boot_trace, trace,
                 'expected the boot-time trace resource to be replaced in place after lazy init ' \
                 "(boot=#{boot_trace.inspect}).\n--- subprocess output ---\n#{output}"
  end

  private

  def boot_lazy_and_run(script)
    env = {
      'LD_LAZY_INIT' => '1',
      'LAUNCHDARKLY_SDK_KEY' => 'sdk-test-0000000000000000000000',
      'RAILS_ENV' => 'test'
    }
    rails_bin = Rails.root.join('bin/rails').to_s
    stdout, stderr, _status = Open3.capture3(env, rails_bin, 'runner', script, chdir: Rails.root.to_s)
    "#{stdout}\n#{stderr}"
  end
end
