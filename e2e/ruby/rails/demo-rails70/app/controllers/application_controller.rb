# frozen_string_literal: true

class ApplicationController < ActionController::Base
  private

  def ld_client
    Rails.configuration.ld_client
  end
  helper_method :ld_client

  # Render a turbo-frame-compatible success message for POST action buttons
  def render_turbo_feedback(frame_id, message)
    timestamp = Time.current.strftime('%H:%M:%S')
    html = %(<turbo-frame id="#{frame_id}"><span class="feedback">#{message} at #{timestamp}</span></turbo-frame>)
    render html: html.html_safe, layout: false
  end
end
