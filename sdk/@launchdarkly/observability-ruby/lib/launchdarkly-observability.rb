# frozen_string_literal: true

# Entry point matching the gem name (`launchdarkly-observability`) so that
# Bundler.require auto-loads the gem during application boot. Without this,
# Bundler's default `require 'launchdarkly-observability'` fails (the real entry
# point is the underscore file) and users must require the gem manually — often
# in an initializer, which is too late for the Rails Railtie to install
# auto-instrumentation during boot.
require_relative 'launchdarkly_observability'
