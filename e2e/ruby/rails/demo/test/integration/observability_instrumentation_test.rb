# frozen_string_literal: true

require 'test_helper'

# End-to-end coverage for the LaunchDarkly observability plugin's Rails
# auto-instrumentation.
#
# Background: the plugin configures OpenTelemetry from `Plugin#register`, which
# runs when the LaunchDarkly client is created. In this app that happens in
# `config/initializers/launchdarkly.rb` — i.e. DURING Rails boot — so the OTel
# Rails-family instrumentations (Rack, ActionPack, ActiveRecord, ...) install
# correctly. A customer who instead creates the client lazily AFTER boot sees
# "Instrumentation: OpenTelemetry::Instrumentation::ActionPack failed to install"
# because the ActiveSupport.on_load hooks those instrumentations rely on have
# already fired. These tests pin the working boot-time behavior so a regression
# (or a change that breaks instrumentation install) is caught in CI.
class ObservabilityInstrumentationTest < ActionDispatch::IntegrationTest
  # The Rails-family instrumentations that must attach during a boot-time init.
  # These are exactly the ones that report "failed to install" on the lazy path.
  RAILS_INSTRUMENTATIONS = %w[Rack ActionPack ActiveRecord ActiveSupport Rails].freeze

  def instrumentation_instance(name)
    Object.const_get("OpenTelemetry::Instrumentation::#{name}::Instrumentation").instance
  end

  test 'rails auto-instrumentation installed during boot' do
    RAILS_INSTRUMENTATIONS.each do |name|
      assert instrumentation_instance(name).installed?,
             "#{name} instrumentation should be installed after a boot-time plugin init " \
             '(it reports "failed to install" when the client is created lazily after boot)'
    end
  end

  test 'http request produces a server span via the rack instrumentation' do
    exporter = OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
    processor = OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(exporter)
    OpenTelemetry.tracer_provider.add_span_processor(processor)

    get pages_home_url
    assert_response :success

    server_spans = exporter.finished_spans.select { |s| s.kind == :server }
    refute_empty server_spans, 'expected an HTTP server span from the Rack/ActionPack instrumentation'
  end
end
