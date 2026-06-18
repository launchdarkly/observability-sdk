# frozen_string_literal: true

# Loads the OpenTelemetry auto-instrumentations the plugin enables by default.
#
# We require INDIVIDUAL instrumentation gems instead of
# `opentelemetry/instrumentation/all` on purpose. The meta-gem couples every
# instrumentation to a single version, so when the Rails-family instrumentations
# raised their minimum to Rails 7.1 (opentelemetry-instrumentation-rails 0.42.0),
# the whole bundle moved with them and Rails 7.0 apps silently lost ALL
# auto-instrumentation. Requiring gems individually lets the Rails family stay on
# a Rails-7.0-compatible release (pinned in the gemspec) while everything else
# tracks the latest.
#
# `OpenTelemetry::SDK#use_all` activates every instrumentation that has been
# loaded, so any additional `opentelemetry-instrumentation-*` gem a consumer adds
# to their own Gemfile is picked up automatically alongside these defaults.

# Rails family. Requiring this pulls action_pack, active_record, active_support,
# action_view and active_job.
require 'opentelemetry/instrumentation/rails'

# Common non-Rails instrumentations (latest; see gemspec for version policy).
# Note the require paths differ from gem names in places, e.g. the
# opentelemetry-instrumentation-net_http gem is required as 'net/http'.
%w[
  concurrent_ruby
  faraday
  graphql
  http
  mysql2
  net/http
  pg
  rack
  redis
  sidekiq
  sinatra
].each do |path|
  require "opentelemetry/instrumentation/#{path}"
rescue LoadError => e
  # A default instrumentation gem is unexpectedly absent. Don't abort the whole
  # plugin over one missing instrumentation; the rest still load.
  warn "[LaunchDarklyObservability] optional instrumentation '#{path}' not loaded: #{e.message}"
end
