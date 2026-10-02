# frozen_string_literal: true

require_relative 'lib/launchdarkly_observability/version'

Gem::Specification.new do |spec|
  spec.name          = 'launchdarkly-observability'
  spec.version       = LaunchDarklyObservability::VERSION
  spec.authors       = ['LaunchDarkly']
  spec.email         = ['support@launchdarkly.com']

  spec.summary       = 'LaunchDarkly Observability Plugin for the Ruby SDK'
  spec.description   = 'OpenTelemetry-based observability instrumentation for the LaunchDarkly Ruby SDK. Works with Rails, Sinatra, and any Rack-compatible framework.'
  spec.homepage      = 'https://launchdarkly.com'
  spec.license       = 'Apache-2.0'
  spec.required_ruby_version = Gem::Requirement.new('>= 3.0.0')

  spec.metadata['homepage_uri'] = spec.homepage
  spec.metadata['source_code_uri'] = 'https://github.com/launchdarkly/observability-sdk'
  spec.metadata['changelog_uri'] = 'https://github.com/launchdarkly/observability-sdk/blob/main/sdk/%40launchdarkly/observability-ruby/CHANGELOG.md'
  spec.metadata['rubygems_mfa_required'] = 'true'

  # Specify which files should be added to the gem when it is released.
  spec.files = Dir.chdir(File.expand_path(__dir__)) do
    Dir['{lib}/**/*', 'LICENSE.txt', 'README.md', 'CHANGELOG.md'].reject { |f| File.directory?(f) }
  end
  spec.bindir        = 'exe'
  spec.executables   = spec.files.grep(%r{^exe/}) { |f| File.basename(f) }
  spec.require_paths = ['lib']

  # Runtime dependencies
  # Plugin support (LaunchDarkly::Interfaces::Plugins) was added in 8.11.0; earlier
  # versions raise "uninitialized constant LaunchDarkly::Interfaces::Plugins" on require.
  spec.add_dependency 'launchdarkly-server-sdk', '>= 8.11.0'
  spec.add_dependency 'opentelemetry-exporter-otlp', '~> 0.28'
  spec.add_dependency 'opentelemetry-sdk', '~> 1.4'

  # OpenTelemetry auto-instrumentation.
  #
  # We depend on INDIVIDUAL instrumentation gems instead of the
  # opentelemetry-instrumentation-all meta-gem on purpose. The meta-gem couples
  # every instrumentation to one version, so when the Rails-family
  # instrumentations raised their floor to Rails 7.1
  # (opentelemetry-instrumentation-rails 0.42.0), the whole bundle moved with
  # them and Rails 7.0 apps silently lost ALL auto-instrumentation. Listing gems
  # individually keeps the Rails family on a Rails-7.0-compatible release while
  # everything else tracks the latest. See lib/launchdarkly_observability/
  # instrumentations.rb.
  #
  # Rails family. Each of these gems independently enforces a Rails 7.1 floor in
  # its latest release (the coordinated "Min Rails 7.1 enforced" bump), so the
  # meta gem (opentelemetry-instrumentation-rails) is NOT enough — each member
  # must be capped below its enforcing version to keep attaching on Rails 7.0.
  # These releases are still compatible with Rails 7.1+, so modern apps are
  # unaffected. Revisit when the plugin drops Rails 7.0 support.
  spec.add_dependency 'opentelemetry-instrumentation-action_mailer', '< 0.8'
  spec.add_dependency 'opentelemetry-instrumentation-action_pack', '< 0.18'
  spec.add_dependency 'opentelemetry-instrumentation-action_view', '< 0.13'
  spec.add_dependency 'opentelemetry-instrumentation-active_job', '< 0.12'
  spec.add_dependency 'opentelemetry-instrumentation-active_record', '< 0.13'
  spec.add_dependency 'opentelemetry-instrumentation-active_storage', '< 0.5'
  spec.add_dependency 'opentelemetry-instrumentation-active_support', '< 0.12'
  spec.add_dependency 'opentelemetry-instrumentation-rails', '>= 0.34', '< 0.42'

  # Non-Rails instrumentations: latest. Consumers can add any other
  # opentelemetry-instrumentation-* gem to their Gemfile and it is picked up
  # automatically (the plugin activates every loaded instrumentation).
  spec.add_dependency 'opentelemetry-instrumentation-concurrent_ruby', '>= 0.21'
  spec.add_dependency 'opentelemetry-instrumentation-faraday', '>= 0.24'
  spec.add_dependency 'opentelemetry-instrumentation-graphql', '>= 0.28'
  spec.add_dependency 'opentelemetry-instrumentation-http', '>= 0.23'
  spec.add_dependency 'opentelemetry-instrumentation-mysql2', '>= 0.28'
  spec.add_dependency 'opentelemetry-instrumentation-net_http', '>= 0.22'
  spec.add_dependency 'opentelemetry-instrumentation-pg', '>= 0.29'
  spec.add_dependency 'opentelemetry-instrumentation-rack', '>= 0.24'
  spec.add_dependency 'opentelemetry-instrumentation-redis', '>= 0.25'
  spec.add_dependency 'opentelemetry-instrumentation-sidekiq', '>= 0.25'
  spec.add_dependency 'opentelemetry-instrumentation-sinatra', '>= 0.24'
  spec.add_dependency 'opentelemetry-semantic_conventions', '~> 1.10'

  # Logs support (included by default for out-of-box DX; opt out via enable_logs: false)
  spec.add_dependency 'opentelemetry-exporter-otlp-logs', '~> 0.1'
  spec.add_dependency 'opentelemetry-logs-sdk', '~> 0.1'

  # Development dependencies
  spec.add_development_dependency 'minitest', '~> 5.0'
  spec.add_development_dependency 'rake', '~> 13.0'
  spec.add_development_dependency 'rubocop', '~> 1.0'
end
