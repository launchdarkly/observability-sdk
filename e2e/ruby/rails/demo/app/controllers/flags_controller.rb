# frozen_string_literal: true

# Controller to demonstrate LaunchDarkly feature flag evaluations
# with observability instrumentation
class FlagsController < ApplicationController
  # GET /flags
  # Returns all flag evaluations for the current context
  def index
    context = build_context

    # Get all flags from LaunchDarkly
    state = $ld_client.all_flags_state(context, details_only_for_tracked_flags: false)
    @all_flags_valid = state.valid?

    # Build evaluations from all available flags
    @evaluations = {}
    state.values_map.each_key do |flag_key|
      @evaluations[flag_key] = $ld_client.variation_detail(flag_key, context, nil)
    end

    respond_to do |format|
      format.html
      format.json { render json: { valid: @all_flags_valid, evaluations: format_evaluations(@evaluations) } }
    end
  end

  # GET /flags/:key
  # Returns a single flag evaluation
  def show
    context = build_context
    flag_key = params[:id]

    detail = $ld_client.variation_detail(flag_key, context, nil)

    respond_to do |format|
      format.html { @flag_key = flag_key; @detail = detail }
      format.json { render json: format_detail(flag_key, detail) }
    end
  end

  # POST /flags/evaluate
  # Evaluate a flag with a custom context
  def evaluate
    flag_key = params[:flag_key]
    context_data = params[:context] || {}

    context = LaunchDarkly::LDContext.create({
      key: context_data[:key] || 'anonymous',
      kind: context_data[:kind] || 'user',
      **context_data.except(:key, :kind).to_h.symbolize_keys
    })

    detail = $ld_client.variation_detail(flag_key, context, params[:default])

    render json: format_detail(flag_key, detail)
  end

  # POST /flags/batch
  # Evaluate multiple flags at once (demonstrates multiple spans)
  def batch
    context = build_context
    flag_keys = params[:flag_keys]

    unless flag_keys.present?
      return render json: { error: 'flag_keys parameter required' }, status: :bad_request
    end

    results = flag_keys.each_with_object({}) do |key, hash|
      hash[key] = $ld_client.variation(key, context, nil)
    end

    render json: { evaluations: results, context_key: context.key }
  end

  # GET /flags/all_flags
  # Get all flag states (demonstrates all_flags_state method)
  def all_flags
    context = build_context
    state = $ld_client.all_flags_state(context)

    render json: {
      valid: state.valid?,
      flags: state.to_json
    }
  end

  private

  def build_context
    # Build context from request parameters or session
    user_key = params[:user_key] || session.id.to_s.presence || 'anonymous'
    user_kind = params[:user_kind] || 'user'

    attrs = {
      key: user_key,
      kind: user_kind,
      anonymous: user_key == 'anonymous'
    }

    # Add optional attributes
    attrs[:email] = params[:email] if params[:email].present?
    attrs[:name] = params[:name] if params[:name].present?
    attrs[:plan] = params[:plan] if params[:plan].present?

    LaunchDarkly::LDContext.create(attrs)
  end

  def format_evaluations(evaluations)
    evaluations.transform_values { |detail| format_detail_hash(detail) }
  end

  def format_detail(key, detail)
    {
      flag_key: key,
      **format_detail_hash(detail)
    }
  end

  def format_detail_hash(detail)
    {
      value: detail.value,
      variation_index: detail.variation_index,
      reason: detail.reason&.to_json
    }
  end
end
