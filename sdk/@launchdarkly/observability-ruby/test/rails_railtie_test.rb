# frozen_string_literal: true

require_relative 'test_helper'

# Regression test for the Rails Railtie load order.
#
# When the gem is required lazily *after* Rails has finished booting (e.g. from an
# autoloaded model during a request), ActiveSupport's `:after_initialize` load hook
# has already fired, so `config.after_initialize` runs its block synchronously while
# rails.rb is still being evaluated. Any constant/method that block references
# (ControllerHelpers, attach_otel_log_bridge) must therefore be defined *before* the
# Railtie. Previously they were defined after it, which raised:
#
#   uninitialized constant LaunchDarklyObservability::ControllerHelpers
#
# This test simulates that "already booted" condition and asserts rails.rb loads clean.
class RailsRailtieTest < Minitest::Test
  def setup
    # Minimal stubs of the Rails surface rails.rb touches. The key behavior is that
    # `config.after_initialize` invokes its block immediately, mimicking a post-boot
    # lazy require where the :after_initialize hook has already run.
    #
    # Track exactly what we introduce so teardown only removes our additions — some
    # transitive dependency may already define a partial `::Rails` (without Railtie).
    @added = []

    Object.const_set(:Rails, Module.new) unless defined?(::Rails)

    unless defined?(::Rails::Railtie)
      railtie_config = Class.new do
        def after_initialize(&block)
          block.call # already-booted: run synchronously
        end
      end
      railtie = Class.new do
        def self.initializer(*); end
      end
      railtie.const_set(:RAILTIE_CONFIG, railtie_config)
      railtie.define_singleton_method(:config) { @config ||= self::RAILTIE_CONFIG.new }
      ::Rails.const_set(:Railtie, railtie)
      @added << [::Rails, :Railtie]
    end

    ::Rails.define_singleton_method(:logger) { @logger ||= Object.new } unless ::Rails.respond_to?(:logger)

    unless defined?(::ActionController)
      action_controller = Module.new
      action_controller.const_set(:Base, Class.new { def self.include(_mod); end })
      action_controller.const_set(:API, Class.new { def self.include(_mod); end })
      Object.const_set(:ActionController, action_controller)
      @added << [Object, :ActionController]
    end

    return if defined?(::ActionView)
    action_view = Module.new
    action_view.const_set(:Base, Class.new { def self.include(_mod); end })
    Object.const_set(:ActionView, action_view)
    @added << [Object, :ActionView]
  end

  def teardown
    @added.reverse_each { |mod, const| mod.send(:remove_const, const) if mod.const_defined?(const, false) }
  end

  def test_rails_file_loads_when_after_initialize_runs_immediately
    rails_rb = File.expand_path('../lib/launchdarkly_observability/rails.rb', __dir__)

    # The bug manifested as a NameError raised during load of this file.
    load rails_rb

    assert defined?(LaunchDarklyObservability::ControllerHelpers),
           'ControllerHelpers should be defined after loading rails.rb'
    assert defined?(LaunchDarklyObservability::ViewHelpers),
           'ViewHelpers should be defined after loading rails.rb'
    assert defined?(LaunchDarklyObservability::Railtie),
           'Railtie should be defined after loading rails.rb'
  end
end
