class HealthController < ApplicationController
  def index
    context = LaunchDarkly::LDContext.create({ key: 'health-check', kind: 'service' })
    state = ld_client.all_flags_state(context)

    render(json: {
      status: 'ok',
      timestamp: Time.current,
      launchdarkly: {
        connected: state.valid?,
        flag_count: state.values_map.size
      }
    })
  end

  def error
    raise(StandardError, 'Test API error')
  end

  # GET /health/flags
  # Returns all flag evaluations for testing
  def flags
    user_key = params[:user_key] || 'anonymous'
    context = LaunchDarkly::LDContext.create({ key: user_key, kind: 'user' })

    state = ld_client.all_flags_state(context)

    # Get detailed evaluations for all flags
    evaluations = {}
    state.values_map.each_key do |flag_key|
      detail = ld_client.variation_detail(flag_key, context, nil)
      evaluations[flag_key] = {
        value: detail.value,
        variation_index: detail.variation_index,
        reason: detail.reason&.kind
      }
    end

    render(json: {
      valid: state.valid?,
      context_key: user_key,
      flag_count: evaluations.size,
      evaluations: evaluations
    })
  end

  # GET /health/flags/:key
  # Evaluate a specific flag
  def flag
    flag_key = params[:key]
    user_key = params[:user_key] || 'anonymous'
    context = LaunchDarkly::LDContext.create({ key: user_key, kind: 'user' })

    detail = ld_client.variation_detail(flag_key, context, nil)

    render(json: {
      flag_key: flag_key,
      context_key: user_key,
      value: detail.value,
      variation_index: detail.variation_index,
      reason: detail.reason&.to_json
    })
  end
end
