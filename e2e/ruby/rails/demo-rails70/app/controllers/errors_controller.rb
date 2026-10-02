# frozen_string_literal: true

class ErrorsController < ApplicationController
  # Uses the module-level LaunchDarklyObservability.record_exception
  def create
    LaunchDarklyObservability.in_span('error-handling-example', attributes: { 'foo' => 'bar' }) do |span|
      begin
        1 / 0
      rescue StandardError => e
        LaunchDarklyObservability.record_exception(e)
        Rails.logger.error "Exception occurred: #{e.message}"
      end
    end

    render_turbo_feedback('error_feedback', 'Error recorded (module helper)')
  end

  # Uses the controller-level record_launchdarkly_exception helper
  def create_with_helper
    with_launchdarkly_span('error-controller-helper-example', attributes: { 'source' => 'controller_helper' }) do
      begin
        raise ArgumentError, 'demo error via controller helper'
      rescue StandardError => e
        record_launchdarkly_exception(e)
        Rails.logger.error "Exception occurred: #{e.message}"
      end
    end

    render_turbo_feedback('error_feedback', 'Error recorded (controller helper)')
  end
end
