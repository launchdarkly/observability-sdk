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
    
    head :no_content
  end

  def custom_project_id
    LaunchDarklyObservability.in_span('example-trace-2', attributes: { 'launchdarkly.project_id' => '56gl9g91' }) do |span|
      sleep(0.1)
    end
    
    head :no_content
  end
end
