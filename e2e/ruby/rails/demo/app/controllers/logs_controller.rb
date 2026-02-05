# frozen_string_literal: true

class LogsController < ApplicationController
  def create
    # Rails logger is automatically instrumented by OpenTelemetry
    Rails.logger.info "hello, world! foo=bar"
    head :no_content
  end

  def create_with_hash
    Rails.logger.info(test: 'ing', foo: 'bar')
    head :no_content
  end
end
