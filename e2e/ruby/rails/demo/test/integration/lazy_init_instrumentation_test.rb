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

  test 'rails auto-instrumentation installs at boot even when the client is created lazily' do
    output = boot_lazy_and_run(CHECK_SCRIPT)

    assert_includes output, 'LAZY_INSTRUMENTATION_OK',
                    "Rails auto-instrumentation should install at boot in lazy mode.\n--- subprocess output ---\n#{output}"
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
