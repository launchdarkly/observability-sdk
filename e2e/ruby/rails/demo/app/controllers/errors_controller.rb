# frozen_string_literal: true

class ErrorsController < ApplicationController
  def create
    LaunchDarklyObservability.in_span('error-handling-example', attributes: { 'foo' => 'bar' }) do |span|
      begin
        1 / 0
      rescue StandardError => e
        # Record the exception using the convenience method
        LaunchDarklyObservability.record_exception(e)
        
        # Also log it
        Rails.logger.error "Exception occurred: #{e.message}"
      end
    end
    
    head :no_content
  end
end
