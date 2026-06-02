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
  # Minimal stubs of the Rails surface rails.rb touches. The key behavior is that
  # `config.after_initialize` invokes its block immediately, mimicking a post-boot
  # lazy require where the :after_initialize hook has already run.
  #
  # Everything we introduce is tracked so teardown can undo ALL of it — some transitive
  # dependency may already define a partial `::Rails` (without Railtie), and anything we
  # leak (especially a bogus `Rails.logger`) pollutes other tests: the LD SDK's
  # Config.default_logger returns `Rails.logger` whenever `Rails.respond_to?(:logger)`.
  def setup
    @added_consts = [] # [owner, const_name] pairs to remove
    @added_rails_logger = false

    stub_rails
    stub_rails_logger
    stub_action_controller
    stub_action_view
  end

  def teardown
    # Remove the logger singleton method first (only relevant when we didn't create ::Rails
    # ourselves; if we did, removing the constant below drops it along with everything else).
    if @added_rails_logger && defined?(::Rails) && ::Rails.singleton_class.method_defined?(:logger)
      ::Rails.singleton_class.send(:remove_method, :logger)
    end
    @added_consts.reverse_each { |owner, const| owner.send(:remove_const, const) if owner.const_defined?(const, false) }
  end

  private

  def stub_rails
    unless defined?(::Rails)
      Object.const_set(:Rails, Module.new)
      @added_consts << [Object, :Rails]
    end

    return if defined?(::Rails::Railtie)

    railtie_config = Class.new do
      def after_initialize(&block)
        block.call # already-booted: run synchronously
      end
    end
    railtie = Class.new { def self.initializer(*); end }
    railtie.const_set(:RAILTIE_CONFIG, railtie_config)
    railtie.define_singleton_method(:config) { @config ||= self::RAILTIE_CONFIG.new }
    ::Rails.const_set(:Railtie, railtie)
    @added_consts << [::Rails, :Railtie]
  end

  # Provide a real (null) logger if Rails doesn't already have one, so attach_otel_log_bridge
  # works if exercised. Defined only when missing, and removed in teardown to avoid leaking.
  def stub_rails_logger
    return if ::Rails.respond_to?(:logger)

    null_logger = ::Logger.new(File::NULL)
    ::Rails.define_singleton_method(:logger) { null_logger }
    @added_rails_logger = true
  end

  # A bare class whose `.include` is a no-op, standing in for ActionController::Base etc.
  # Built via a helper so the no-op `include` is defined only once (avoids a false-positive
  # Lint/DuplicateMethods when the same block is inlined for multiple constants).
  def noop_includable_class
    Class.new { def self.include(_mod); end }
  end

  def stub_action_controller
    return if defined?(::ActionController)

    action_controller = Module.new
    action_controller.const_set(:Base, noop_includable_class)
    action_controller.const_set(:API, noop_includable_class)
    Object.const_set(:ActionController, action_controller)
    @added_consts << [Object, :ActionController]
  end

  def stub_action_view
    return if defined?(::ActionView)

    action_view = Module.new
    action_view.const_set(:Base, noop_includable_class)
    Object.const_set(:ActionView, action_view)
    @added_consts << [Object, :ActionView]
  end

  public

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

    # Regression: the Railtie's `attach_otel_log_bridge` runs from the synchronous
    # `config.after_initialize` block *during* the require of launchdarkly_observability.rb,
    # before that file's `class << self` block (which defines the module-level
    # `otel_logger_provider_available?`) has executed. The Railtie used to delegate to
    # that not-yet-defined method, so the bridge attach failed with:
    #
    #   Could not attach log bridge to Rails.logger: undefined method
    #   `otel_logger_provider_available?' for module LaunchDarklyObservability
    #
    # The Railtie's check must be self-contained. Simulate the load-order state by
    # removing the module method, then assert the Railtie check still works.
    sc = LaunchDarklyObservability.singleton_class
    saved = sc.instance_method(:otel_logger_provider_available?)
    sc.send(:remove_method, :otel_logger_provider_available?)
    begin
      result = nil
      assert_silent do
        result = LaunchDarklyObservability::Railtie.send(:otel_logger_provider_available?)
      end
      assert_includes [true, false], result
    ensure
      sc.send(:define_method, :otel_logger_provider_available?, saved)
      sc.send(:private, :otel_logger_provider_available?)
    end
  end
end
