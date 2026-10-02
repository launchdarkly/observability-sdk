# frozen_string_literal: true

class LogsController < ApplicationController
  def create
    Rails.logger.info "hello, world! foo=bar"
    render_turbo_feedback('log_feedback', 'Info log created')
  end

  def create_with_hash
    Rails.logger.info(test: 'ing', foo: 'bar')
    render_turbo_feedback('log_feedback', 'Info log (hash) created')
  end

  def create_warn
    Rails.logger.warn "warning: something looks off level=warn"
    render_turbo_feedback('log_feedback', 'Warn log created')
  end

  def create_error
    Rails.logger.error "error: something went wrong level=error"
    render_turbo_feedback('log_feedback', 'Error log created')
  end
end
