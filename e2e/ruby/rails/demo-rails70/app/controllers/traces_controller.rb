# frozen_string_literal: true

class TracesController < ApplicationController
  def create
    LaunchDarklyObservability.in_span('example-trace-outer') do |outer_span|
      sleep(0.1)

      trace = Trace.new(name: 'trace', kind: 'internal')

      LaunchDarklyObservability.in_span('example-trace-inner', attributes: { 'trace.operation' => 'save' }) do |inner_span|
        sleep(0.2)
        trace.save!
      end

      outer_span.set_attribute('trace.operation', 'update')
      trace.update!(name: 'trace-updated')
    end

    render_turbo_feedback('trace_feedback', 'Trace created')
  end

  # Uses the controller-level with_launchdarkly_span helper
  def create_with_helper
    with_launchdarkly_span('example-trace-controller-helper', attributes: { 'source' => 'controller_helper' }) do
      sleep(0.1)
    end

    render_turbo_feedback('trace_feedback', 'Trace created (controller helper)')
  end
end
