# frozen_string_literal: true

Rails.application.routes.draw do
  get 'pages/home'
  resources :traces, only: [:create] do
    post :create_with_helper, on: :collection
  end
  resources :logs, only: [:create] do
    collection do
      post :create_with_hash
      post :create_warn
      post :create_error
    end
  end
  resources :errors, only: [:create] do
    post :create_with_helper, on: :collection
  end
  post 'telemetry/flush', to: 'telemetry#flush', as: :flush_telemetry

  # LaunchDarkly feature flag routes
  resources :flags, only: %i[index show] do
    collection do
      post :evaluate
      post :batch
      get :all_flags
    end
  end

  root to: 'pages#home'
end
