# LaunchDarkly Observability Plugin for Ruby

OpenTelemetry-based observability instrumentation for the LaunchDarkly Ruby SDK with full Rails support.

## Overview

This plugin automatically instruments LaunchDarkly feature flag evaluations with OpenTelemetry traces, providing visibility into:

- Flag evaluation timing and results
- Evaluation reasons and rule matches
- Context information (user/organization)
- Error tracking for failed evaluations
- Correlation with HTTP requests in Rails applications

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'launchdarkly-observability'
```

And then execute:

```bash
bundle install
```

Or install it yourself as:

```bash
gem install launchdarkly-observability
```

### Dependencies

The gem requires:
- `launchdarkly-server-sdk` >= 8.0
- `opentelemetry-sdk` ~> 1.4
- `opentelemetry-exporter-otlp` ~> 0.28
- `opentelemetry-instrumentation-all` ~> 0.62

For logs and metrics support (optional):
- `opentelemetry-logs-sdk` ~> 0.1
- `opentelemetry-metrics-sdk` ~> 0.1

## Quick Start

### Basic Usage (Non-Rails)

```ruby
require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

# Create observability plugin (SDK key and environment automatically inferred)
observability = LaunchDarklyObservability::Plugin.new

# Initialize LaunchDarkly client with plugin
config = LaunchDarkly::Config.new(plugins: [observability])
client = LaunchDarkly::LDClient.new('your-sdk-key', config)

# Flag evaluations are now automatically instrumented
context = LaunchDarkly::LDContext.create({ key: 'user-123', kind: 'user' })
value = client.variation('my-feature-flag', context, false)
```

> **Note**: The plugin automatically extracts the SDK key from the LaunchDarkly client during initialization. The backend derives both the project and environment from the SDK key for telemetry routing, so you don't need to configure them explicitly.

### Rails Usage

Create an initializer at `config/initializers/launchdarkly.rb`:

```ruby
require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'

# Setup observability plugin (SDK key and environment automatically inferred)
observability = LaunchDarklyObservability::Plugin.new(
  service_name: 'my-rails-app',
  service_version: '1.0.0'
)

# Initialize LaunchDarkly client using Rails configuration
config = LaunchDarkly::Config.new(plugins: [observability])
Rails.configuration.ld_client = LaunchDarkly::LDClient.new(
  ENV['LAUNCHDARKLY_SDK_KEY'],
  config
)

# Ensure clean shutdown
at_exit { Rails.configuration.ld_client.close }
```

Use in controllers:

```ruby
class ApplicationController < ActionController::Base
  private

  # Helper method for accessing the LaunchDarkly client
  def ld_client
    Rails.configuration.ld_client
  end

  def current_ld_context
    @current_ld_context ||= LaunchDarkly::LDContext.create({
      key: current_user&.id || 'anonymous',
      kind: 'user',
      email: current_user&.email,
      name: current_user&.name
    })
  end
end

class HomeController < ApplicationController
  def index
    # This evaluation is automatically traced and correlated with the HTTP request
    @show_new_feature = ld_client.variation('new-feature', current_ld_context, false)
  end
end
```

## Configuration

### Plugin Options

```ruby
LaunchDarklyObservability::Plugin.new(
  # All parameters are optional - SDK key and environment are automatically inferred

  # Optional: Custom OTLP endpoint (default: LaunchDarkly's endpoint)
  otlp_endpoint: 'https://otel.observability.app.launchdarkly.com:4318',

  # Optional: Environment override (default: inferred from SDK key)
  # Only specify for advanced scenarios like deployment-specific suffixes
  environment: 'production-canary',

  # Optional: Service identification
  service_name: 'my-service',
  service_version: '1.0.0',

  # Optional: Enable/disable signal types
  enable_traces: true,   # default: true
  enable_logs: true,     # default: true
  enable_metrics: true,  # default: true

  # Optional: Custom instrumentation configuration
  instrumentations: {
    'OpenTelemetry::Instrumentation::Rails' => { enable_recognize_route: true },
    'OpenTelemetry::Instrumentation::ActiveRecord' => { db_statement: :include }
  }
)
```

> **Advanced**: You can explicitly pass `sdk_key` or `project_id` for testing scenarios, but this is rarely needed since they're automatically extracted from the client.

### Environment Variables

You can configure via environment variables:

| Variable | Description |
|----------|-------------|
| `LAUNCHDARKLY_SDK_KEY` | LaunchDarkly SDK key (automatically extracted from client during initialization) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Custom OTLP endpoint |
| `OTEL_SERVICE_NAME` | Service name (if not specified in plugin options) |

> **Note**: The environment associated with your SDK key is automatically determined by the backend, so you don't need to configure it separately.

## Telemetry Details

### Cross-SDK Compatibility

This Ruby SDK is designed for compatibility with other LaunchDarkly observability SDKs (Android, Node.js, Python, Go, .NET). Key compatibility features:

- **Span name**: `"evaluation"` (consistent across all SDKs)
- **Event name**: `"feature_flag"` (matches Android and Node SDKs)
- **Provider name**: `"LaunchDarkly"` (consistent across all SDKs)
- **Attribute naming**: Follows OpenTelemetry semantic conventions

### Span Attributes

Each flag evaluation creates a span with the following attributes, following [OpenTelemetry semantic conventions for feature flags](https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/):

### Standard Semantic Convention Attributes

| Attribute | Status | Description | Example |
|-----------|--------|-------------|---------|
| `feature_flag.key` | Release Candidate | Flag key | `"my-feature"` |
| `feature_flag.provider.name` | Release Candidate | Provider name | `"LaunchDarkly"` |
| `feature_flag.result.value` | Release Candidate | Evaluated value | `"true"` |
| `feature_flag.result.variant` | Release Candidate | Variation index | `"1"` |
| `feature_flag.result.reason` | Release Candidate | Evaluation reason | `"default"`, `"targeting_match"`, `"error"` |
| `feature_flag.context.id` | Release Candidate | Context identifier | `"user-123"` |
| `error.type` | Stable | Error type (when applicable) | `"flag_not_found"` |
| `error.message` | Development | Error message (when applicable) | `"Flag evaluation error: FLAG_NOT_FOUND"` |

### LaunchDarkly-Specific Attributes

These custom attributes provide additional LaunchDarkly-specific details:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `launchdarkly.context.kind` | Context kind | `"user"` |
| `launchdarkly.context.key` | Context key | `"user-123"` |
| `launchdarkly.reason.kind` | LaunchDarkly reason kind | `"FALLTHROUGH"`, `"RULE_MATCH"`, `"ERROR"` |
| `launchdarkly.reason.rule_index` | Rule index (for RULE_MATCH) | `0` |
| `launchdarkly.reason.rule_id` | Rule ID (for RULE_MATCH) | `"rule-key"` |
| `launchdarkly.reason.prerequisite_key` | Prerequisite key (for PREREQUISITE_FAILED) | `"other-flag"` |
| `launchdarkly.reason.in_experiment` | In experiment flag | `true` |
| `launchdarkly.reason.error_kind` | LaunchDarkly error kind (for ERROR) | `"FLAG_NOT_FOUND"` |
| `launchdarkly.evaluation.duration_ms` | Evaluation time in milliseconds | `0.5` |
| `launchdarkly.evaluation.method` | SDK method called | `"variation"`, `"variation_detail"` |

### Feature Flag Event

In addition to span attributes, each evaluation adds a `"feature_flag"` event to the span. This matches the pattern used by other LaunchDarkly observability SDKs (Android, Node.js) and follows OpenTelemetry semantic conventions for feature flag events.

The event contains the core evaluation data:

| Event Attribute | Description | Example |
|-----------------|-------------|---------|
| `feature_flag.key` | Flag key | `"my-feature"` |
| `feature_flag.provider.name` | Provider name | `"LaunchDarkly"` |
| `feature_flag.context.id` | Context identifier | `"user-123"` |
| `feature_flag.result.value` | Evaluated value | `"true"` |
| `feature_flag.result.variant` | Variation index | `"1"` |
| `feature_flag.result.reason` | Evaluation reason | `"default"` |
| `launchdarkly.reason.in_experiment` | In experiment flag (if applicable) | `true` |

**Why both span attributes and events?**

- **Span attributes** provide detailed context for the entire evaluation span, including timing, method, and LaunchDarkly-specific details
- **Span events** represent a point-in-time record of the evaluation result, which is the standard OpenTelemetry pattern for feature flag evaluations
- This dual approach matches other LaunchDarkly SDKs and maximizes compatibility with observability backends

### Error Tracking

When evaluation errors occur, the plugin follows [OpenTelemetry error semantic conventions](https://opentelemetry.io/docs/specs/semconv/feature-flags/feature-flags-events/):

- **`error.type`**: Mapped from LaunchDarkly error kinds to standard values (`flag_not_found`, `type_mismatch`, `provider_not_ready`, `general`)
- **`error.message`**: Human-readable error description
- **`feature_flag.result.reason`**: Set to `"error"`
- **`launchdarkly.reason.error_kind`**: Original LaunchDarkly error kind (`FLAG_NOT_FOUND`, `WRONG_TYPE`, etc.)

The span status is also set to `ERROR` with a descriptive message.

#### Error Type Mapping

| LaunchDarkly Error | OpenTelemetry `error.type` |
|-------------------|----------------------------|
| `FLAG_NOT_FOUND` | `flag_not_found` |
| `WRONG_TYPE` | `type_mismatch` |
| `CLIENT_NOT_READY` | `provider_not_ready` |
| `MALFORMED_FLAG` | `parse_error` |
| Others | `general` |

### Rails Integration

When used with Rails, the plugin provides:

1. **Rack Middleware**: Automatically traces HTTP requests and provides context propagation
2. **Controller Helpers**: Convenient methods for custom tracing
3. **View Helpers**: Generate traceparent meta tags for client-side correlation

#### Controller Helpers

```ruby
class MyController < ApplicationController
  def index
    # Get current trace ID for logging
    trace_id = launchdarkly_trace_id
    Rails.logger.info "Processing request with trace: #{trace_id}"

    # Create custom spans
    with_launchdarkly_span('custom-operation', attributes: { 'custom.key' => 'value' }) do |span|
      # Your code here
      span.set_attribute('result', 'success')
    end
  end

  def create
    # Record exceptions
    begin
      process_something
    rescue => e
      record_launchdarkly_exception(e)
      raise
    end
  end
end
```

#### View Helpers

```erb
<head>
  <%= launchdarkly_traceparent_meta_tag %>
</head>
```

This generates:
```html
<meta name="traceparent" content="00-abc123...-def456...-01">
```

## Auto-Instrumentation

By default, the plugin enables OpenTelemetry auto-instrumentation for common Ruby libraries:

- **Rails**: Request tracing, route recognition
- **ActiveRecord**: Database query tracing
- **Net::HTTP**: Outbound HTTP request tracing
- **Rack**: Request/response tracing
- **Redis**: Cache operation tracing
- **Sidekiq**: Background job tracing

### Customizing Instrumentations

```ruby
LaunchDarklyObservability::Plugin.new(
  instrumentations: {
    # Disable specific instrumentations
    'OpenTelemetry::Instrumentation::Redis' => { enabled: false },

    # Configure instrumentations
    'OpenTelemetry::Instrumentation::ActiveRecord' => {
      db_statement: :obfuscate,  # Mask sensitive data
      obfuscation_limit: 2000
    },

    # Skip certain endpoints
    'OpenTelemetry::Instrumentation::Rack' => {
      untraced_endpoints: ['/health', '/metrics']
    }
  }
)
```

## Manual Instrumentation

### Creating Custom Spans

The LaunchDarkly Observability plugin provides a clean API matching OpenTelemetry conventions for creating custom spans:

```ruby
# Simple span creation
LaunchDarklyObservability.in_span('database-query') do |span|
  span.set_attribute('db.table', 'users')
  span.set_attribute('db.operation', 'select')

  # Your code here
  results = execute_query
end

# With initial attributes
LaunchDarklyObservability.in_span('api-call', attributes: { 'api.endpoint' => '/users' }) do |span|
  response = make_api_call
  span.set_attribute('api.status', response.code)
end

# Nested spans
LaunchDarklyObservability.in_span('process-order') do |outer_span|
  outer_span.set_attribute('order.id', order_id)

  LaunchDarklyObservability.in_span('validate-payment') do |inner_span|
    validate_payment(order)
  end

  LaunchDarklyObservability.in_span('update-inventory') do |inner_span|
    update_inventory(order)
  end
end
```

### Recording Exceptions

```ruby
begin
  risky_operation
rescue StandardError => e
  # Record the exception in the current span
  LaunchDarklyObservability.record_exception(e, attributes: { 'retry_count' => 3 })
  raise
end
```

### Getting Current Trace ID

```ruby
# Get the current trace ID for logging or debugging
trace_id = LaunchDarklyObservability.current_trace_id
logger.info "Processing request: #{trace_id}"
```

### Rails Controller Helpers

In Rails controllers, you can also use these helper methods:

```ruby
class MyController < ApplicationController
  def index
    # Get current trace ID
    trace_id = launchdarkly_trace_id
    Rails.logger.info "Processing request with trace: #{trace_id}"

    # Create custom spans (Rails helper - equivalent to LaunchDarklyObservability.in_span)
    with_launchdarkly_span('custom-operation', attributes: { 'custom.key' => 'value' }) do |span|
      # Your code here
      span.set_attribute('result', 'success')
    end
  end

  def create
    # Record exceptions (Rails helper - equivalent to LaunchDarklyObservability.record_exception)
    begin
      process_something
    rescue => e
      record_launchdarkly_exception(e)
      raise
    end
  end
end
```

### Logging with Trace Context

```ruby
# Logs are automatically correlated with traces via the Logger instrumentation
Rails.logger.info "Processing flag evaluation"  # Includes trace_id, span_id
```

### Using Raw OpenTelemetry API

If you need more control, you can still use the OpenTelemetry API directly:

```ruby
tracer = OpenTelemetry.tracer_provider.tracer('my-component')

tracer.in_span('custom-operation') do |span|
  span.set_attribute('custom.attribute', 'value')

  # Your code here

  if error_occurred
    span.record_exception(error)
    span.status = OpenTelemetry::Trace::Status.error('Operation failed')
  end
end
```

## Troubleshooting

### Spans Not Appearing

1. Verify the OTLP endpoint is accessible:
   ```ruby
   puts LaunchDarklyObservability.instance&.otlp_endpoint
   ```

2. Check if OpenTelemetry is configured:
   ```ruby
   puts OpenTelemetry.tracer_provider.class
   # Should be: OpenTelemetry::SDK::Trace::TracerProvider
   ```

3. Ensure the plugin is registered:
   ```ruby
   puts LaunchDarklyObservability.instance&.registered?
   ```

### Missing Flag Evaluations

Verify the hook is receiving evaluations by checking logs:
```ruby
# Set environment variable for debug output
ENV['OTEL_LOG_LEVEL'] = 'debug'
```

### Rails Middleware Not Active

Ensure the gem is loaded in your Gemfile and the initializer runs before controllers:
```ruby
# Gemfile
gem 'launchdarkly-observability'
```

## Testing

When testing, you may want to use an in-memory exporter:

```ruby
# test/test_helper.rb
require 'opentelemetry/sdk'

class ActiveSupport::TestCase
  setup do
    @exporter = OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
    OpenTelemetry::SDK.configure do |c|
      c.add_span_processor(
        OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@exporter)
      )
    end
  end

  teardown do
    @exporter.reset
  end

  def finished_spans
    @exporter.finished_spans
  end
end
```

## API Reference

### LaunchDarklyObservability Module

```ruby
# Initialize the plugin (alternative to creating Plugin directly)
LaunchDarklyObservability.init

# Check if initialized
LaunchDarklyObservability.initialized?  # => true

# Create a custom span for manual instrumentation (matches OpenTelemetry API)
LaunchDarklyObservability.in_span('operation-name') do |span|
  span.set_attribute('key', 'value')
  # your code
end

# Create a span with initial attributes
LaunchDarklyObservability.in_span('operation-name', attributes: { 'key' => 'value' }) do |span|
  # your code
end

# Record an exception in the current span
begin
  risky_operation
rescue => e
  LaunchDarklyObservability.record_exception(e, attributes: { 'context' => 'value' })
  raise
end

# Get the current trace ID
trace_id = LaunchDarklyObservability.current_trace_id  # => "abc123..."

# Flush pending telemetry
LaunchDarklyObservability.flush

# Shutdown (flushes and stops)
LaunchDarklyObservability.shutdown
```

### Plugin Class

```ruby
# SDK key and environment are automatically inferred
plugin = LaunchDarklyObservability::Plugin.new(service_name: 'my-service')

plugin.project_id        # => nil (extracted from client during registration)
plugin.otlp_endpoint     # => 'https://otel...'
plugin.environment       # => nil (inferred from SDK key by backend)
plugin.registered?       # => false (true after client initialization)
plugin.flush             # Flush pending data
plugin.shutdown          # Stop the plugin
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for your changes
4. Run tests (`bundle exec rake test`)
5. Commit your changes (`git commit -am 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE.txt](LICENSE.txt) file for details.

## Support

- [LaunchDarkly Documentation](https://docs.launchdarkly.com)
- [OpenTelemetry Ruby Documentation](https://opentelemetry.io/docs/instrumentation/ruby/)
- [GitHub Issues](https://github.com/launchdarkly/observability-sdk/issues)
