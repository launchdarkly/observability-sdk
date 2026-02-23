# frozen_string_literal: true

require_relative 'lib/launchdarkly_observability/version'

Gem::Specification.new do |spec|
  spec.name          = 'launchdarkly-observability'
  spec.version       = LaunchDarklyObservability::VERSION
  spec.authors       = ['LaunchDarkly']
  spec.email         = ['support@launchdarkly.com']

  spec.summary       = 'LaunchDarkly Observability Plugin for the Ruby SDK'
  spec.description   = 'OpenTelemetry-based observability instrumentation for LaunchDarkly Ruby SDK with Rails support'
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
  spec.add_dependency 'launchdarkly-server-sdk', '>= 8.0'
  spec.add_dependency 'opentelemetry-exporter-otlp', '~> 0.28'
  spec.add_dependency 'opentelemetry-instrumentation-all', '~> 0.62'
  spec.add_dependency 'opentelemetry-sdk', '~> 1.4'
  spec.add_dependency 'opentelemetry-semantic_conventions', '~> 1.10'

  # Optional dependencies for logs/metrics (these may need to be required separately)
  # spec.add_dependency 'opentelemetry-logs-sdk', '~> 0.1'
  # spec.add_dependency 'opentelemetry-exporter-otlp-logs', '~> 0.1'

  # Development dependencies
  spec.add_development_dependency 'minitest', '~> 5.0'
  spec.add_development_dependency 'rake', '~> 13.0'
  spec.add_development_dependency 'rubocop', '~> 1.0'
end
