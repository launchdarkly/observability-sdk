# frozen_string_literal: true

class PagesController < ApplicationController
  def home
    # Create LaunchDarkly context for current user/session
    @context = LaunchDarkly::LDContext.create({
      key: session.id.to_s.presence || 'anonymous',
      kind: 'user',
      anonymous: session.id.blank?
    })

    state = ld_client.all_flags_state(@context)
    @flags_valid = state.valid?
    @flag_count = state.values_map.size
    @sample_evaluations = state.values_map.first(5).to_h

    # Make an HTTP request (auto-instrumented by OpenTelemetry)
    @http_url = 'http://www.example.com/?test=1'
    with_launchdarkly_span('pages-home-fetch', attributes: { 'custom.source' => 'demo' }) do
      response = Net::HTTP.get_response(URI.parse(@http_url))
      @http_status = "#{response.code} #{response.message}"
    end

    Rails.logger.info "[LaunchDarkly] Loaded #{@flag_count} flags, valid=#{@flags_valid}"
  end
end
