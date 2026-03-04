# frozen_string_literal: true

class TelemetryController < ApplicationController
  def flush
    LaunchDarklyObservability.flush
    render_turbo_feedback('flush_feedback', 'Telemetry flushed')
  end
end
