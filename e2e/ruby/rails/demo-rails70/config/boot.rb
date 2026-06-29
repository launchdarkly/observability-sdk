# frozen_string_literal: true

# Ruby 3.3 + Rails 7.0 boot fix: concurrent-ruby >= 1.3.5 dropped its implicit
# `require 'logger'`, which makes Rails < 7.1 raise
# "NameError: uninitialized constant Logger" during boot. Requiring it here (the
# documented workaround) is unrelated to the instrumentation bug under test; it
# just lets this Rails 7.0 app boot on the Ruby 3.3.4 toolchain.
require 'logger'

ENV['BUNDLE_GEMFILE'] ||= File.expand_path('../Gemfile', __dir__)

require 'bundler/setup' # Set up gems listed in the Gemfile.
require 'bootsnap/setup' # Speed up boot time by caching expensive operations.
