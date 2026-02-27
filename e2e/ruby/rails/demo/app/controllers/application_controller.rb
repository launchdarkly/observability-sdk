# frozen_string_literal: true

class ApplicationController < ActionController::Base
  private

  # Helper method to access LaunchDarkly client
  def ld_client
    Rails.configuration.ld_client
  end
  helper_method :ld_client
end
