require 'test_helper'

# Tests for the Health controller
class HealthControllerTest < ActionDispatch::IntegrationTest
  test 'should get health check' do
    get health_path
    assert_response :success

    json = JSON.parse(response.body)
    assert_equal 'ok', json['status']
    assert json['timestamp'].present?
  end

  test 'should handle errors' do
    assert_raises StandardError do
      get error_path
    end
  end

  test 'should handle custom headers' do
    get health_path, headers: { 'X-Request-ID' => 'test-request-123' }
    assert_response :success
  end
end
