# frozen_string_literal: true

require 'test_helper'

# Tests for boot-time auto-instrumentation install
# (LaunchDarklyObservability.install_rails_instrumentation), which lets the OTel
# Rails-family instrumentations attach during Rails boot even when the LD client
# is created lazily afterward. See the Railtie in rails.rb.
class BootInstrumentationTest < Minitest::Test
  include TestHelper

  def setup
    LaunchDarklyObservability.reset_instrumentation_state!
    @saved_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY', nil)
  end

  def teardown
    LaunchDarklyObservability.reset_instrumentation_state!
    if @saved_key.nil?
      ENV.delete('LAUNCHDARKLY_SDK_KEY')
    else
      ENV['LAUNCHDARKLY_SDK_KEY'] = @saved_key
    end
  end

  def test_not_installed_at_boot_by_default
    refute LaunchDarklyObservability.instrumentation_installed_at_boot?
  end

  def test_returns_false_without_project_id_or_env_key
    ENV.delete('LAUNCHDARKLY_SDK_KEY')
    refute LaunchDarklyObservability.install_rails_instrumentation(project_id: nil)
    refute LaunchDarklyObservability.instrumentation_installed_at_boot?
  end

  def test_skips_when_sdk_provider_already_configured
    # Mirrors a boot-time init: the client already configured OpenTelemetry in a
    # config/initializer, so the Railtie must not reconfigure (which would drop
    # the existing exporters).
    reset_opentelemetry
    assert_kind_of OpenTelemetry::SDK::Trace::TracerProvider, OpenTelemetry.tracer_provider

    refute LaunchDarklyObservability.install_rails_instrumentation(project_id: 'my-project'),
           'should skip install when an SDK tracer provider is already configured'
    refute LaunchDarklyObservability.instrumentation_installed_at_boot?
  end

  def test_reset_clears_flag
    LaunchDarklyObservability.instance_variable_set(:@instrumentation_installed_at_boot, true)
    assert LaunchDarklyObservability.instrumentation_installed_at_boot?
    LaunchDarklyObservability.reset_instrumentation_state!
    refute LaunchDarklyObservability.instrumentation_installed_at_boot?
  end

  def test_configure_does_not_replace_provider_when_installed_at_boot
    # When boot already installed instrumentation, registering the plugin must
    # only attach exporters to the existing provider — not reconfigure it, which
    # would drop the boot-time Rails instrumentation in the lazy-init case.
    reset_opentelemetry
    provider_before = OpenTelemetry.tracer_provider

    config = LaunchDarklyObservability::OpenTelemetryConfig.new(
      project_id: 'my-project',
      otlp_endpoint: 'http://localhost:4318',
      enable_logs: false,
      enable_metrics: false
    )

    LaunchDarklyObservability.stub(:instrumentation_installed_at_boot?, true) do
      config.configure
    end

    assert_same provider_before, OpenTelemetry.tracer_provider,
                'configure must attach to the existing provider, not replace it, when installed at boot'
  end
end
