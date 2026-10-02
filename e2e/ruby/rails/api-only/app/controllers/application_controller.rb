class ApplicationController < ActionController::API
  include ActionController::Cookies

  private

  def ld_client
    Rails.configuration.ld_client
  end
end
