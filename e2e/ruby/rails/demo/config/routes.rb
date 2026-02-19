# frozen_string_literal: true

Rails.application.routes.draw do
  get 'pages/home'
  resources :articles, only: %i[index new]
  resources :traces, only: [:create] do
    post :custom_project_id, on: :collection
  end
  resources :logs, only: [:create] do
    post :create_with_hash, on: :collection
  end
  resources :errors, only: [:create]

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
