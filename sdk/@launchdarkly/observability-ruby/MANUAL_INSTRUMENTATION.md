# Manual Instrumentation Guide

The LaunchDarkly Observability plugin provides convenient methods for creating custom spans, matching the OpenTelemetry Ruby SDK naming conventions.

## Quick Start

```ruby
require 'launchdarkly_observability'

# Create a custom span (same API as OpenTelemetry's tracer.in_span)
LaunchDarklyObservability.in_span('my-operation') do |span|
  span.set_attribute('custom.key', 'value')
  # Your code here
end
```

## Creating Custom Spans

### Basic Span

```ruby
LaunchDarklyObservability.in_span('database-query') do |span|
  result = execute_query
  span.set_attribute('rows.returned', result.count)
end
```

### Span with Initial Attributes

```ruby
LaunchDarklyObservability.in_span('api-call', attributes: {
  'api.endpoint' => '/users',
  'api.method' => 'GET'
}) do |span|
  response = make_api_call
  span.set_attribute('api.status', response.code)
end
```

### Nested Spans

```ruby
LaunchDarklyObservability.in_span('process-order') do |outer_span|
  outer_span.set_attribute('order.id', order_id)
  
  # Nested span for payment
  LaunchDarklyObservability.in_span('validate-payment') do |payment_span|
    validate_payment(order)
    payment_span.set_attribute('payment.method', 'credit_card')
  end
  
  # Nested span for inventory
  LaunchDarklyObservability.in_span('update-inventory') do |inventory_span|
    update_inventory(order)
  end
  
  outer_span.set_attribute('order.status', 'completed')
end
```

## Recording Exceptions

```ruby
LaunchDarklyObservability.in_span('risky-operation') do |span|
  begin
    perform_operation
  rescue StandardError => e
    # Record exception with additional context
    LaunchDarklyObservability.record_exception(e, attributes: {
      'retry_count' => 3,
      'operation_id' => operation_id
    })
    raise # Re-raise the exception
  end
end
```

## Getting the Current Trace ID

Useful for correlating logs with traces:

```ruby
LaunchDarklyObservability.in_span('process-request') do |span|
  trace_id = LaunchDarklyObservability.current_trace_id
  
  logger.info "Processing request with trace: #{trace_id}"
  process_request
end
```

## Rails Controller Helpers

When using Rails, you also have access to controller-specific helpers:

```ruby
class OrdersController < ApplicationController
  def create
    # Get current trace ID
    trace_id = launchdarkly_trace_id
    Rails.logger.info "Creating order: #{trace_id}"
    
    # Create custom span (Rails helper)
    with_launchdarkly_span('process-payment', attributes: { 'amount' => params[:amount] }) do |span|
      process_payment
      span.set_attribute('payment.status', 'success')
    end
    
    # Record exception (Rails helper)
    begin
      finalize_order
    rescue => e
      record_launchdarkly_exception(e, attributes: { 'order_id' => @order.id })
      raise
    end
  end
end
```

## Non-Rails Applications

The module-level methods work in any Ruby application:

```ruby
# Sinatra example
require 'sinatra'
require 'launchdarkly_observability'

get '/users/:id' do
  LaunchDarklyObservability.in_span('fetch-user', attributes: { 'user.id' => params[:id] }) do |span|
    user = User.find(params[:id])
    span.set_attribute('user.name', user.name)
    user.to_json
  end
end

# Plain Ruby script
LaunchDarklyObservability.in_span('data-processing') do |span|
  files = Dir.glob('data/*.csv')
  span.set_attribute('files.count', files.length)
  
  files.each do |file|
    LaunchDarklyObservability.in_span('process-file', attributes: { 'file.name' => file }) do |file_span|
      process_csv(file)
    end
  end
end
```

## Comparison: Plugin API vs Raw OpenTelemetry

### Using the Plugin API (Recommended)

The plugin API matches OpenTelemetry's naming but eliminates boilerplate:

```ruby
# Same method name as OpenTelemetry, but no need to get a tracer
LaunchDarklyObservability.in_span('operation', attributes: { 'key' => 'value' }) do |span|
  # Your code
end

# Convenience methods for common operations
LaunchDarklyObservability.record_exception(error)
LaunchDarklyObservability.current_trace_id
```

### Using Raw OpenTelemetry API

```ruby
# Need to get a tracer first
tracer = OpenTelemetry.tracer_provider.tracer('my-component', '1.0.0')

tracer.in_span('operation', attributes: { 'key' => 'value' }) do |span|
  # Your code
end

# More verbose exception recording
span = OpenTelemetry::Trace.current_span
span.record_exception(error)
span.status = OpenTelemetry::Trace::Status.error(error.message)

# More verbose trace ID retrieval
span = OpenTelemetry::Trace.current_span
span.context.hex_trace_id if span&.context&.valid?
```

The plugin API provides the same familiar `in_span` method name while eliminating boilerplate.

## Best Practices

1. **Use descriptive span names**: Use kebab-case names that describe the operation
   ```ruby
   LaunchDarklyObservability.in_span('validate-payment') # Good
   LaunchDarklyObservability.in_span('do_stuff')        # Bad
   ```

2. **Add meaningful attributes**: Include relevant context as span attributes
   ```ruby
   LaunchDarklyObservability.in_span('database-query', attributes: {
     'db.table' => 'users',
     'db.operation' => 'select',
     'db.rows_returned' => results.count
   })
   ```

3. **Always re-raise exceptions**: After recording an exception, re-raise it unless you're handling it
   ```ruby
   rescue => e
     LaunchDarklyObservability.record_exception(e)
     raise # Important!
   end
   ```

4. **Keep spans focused**: Create separate spans for distinct operations rather than one large span
   ```ruby
   # Good - separate spans
   LaunchDarklyObservability.in_span('fetch-data') { fetch }
   LaunchDarklyObservability.in_span('process-data') { process }
   
   # Bad - one large span
   LaunchDarklyObservability.in_span('fetch-and-process') do
     fetch
     process
   end
   ```

5. **Include trace IDs in logs**: Use `current_trace_id` for log correlation
   ```ruby
   trace_id = LaunchDarklyObservability.current_trace_id
   Rails.logger.info "Starting processing [trace: #{trace_id}]"
   ```

## API Reference

### `LaunchDarklyObservability.in_span(name, attributes: {})`

Creates a new span and executes the given block within its context. Matches the OpenTelemetry `tracer.in_span` API.

**Parameters:**
- `name` (String): The name of the span
- `attributes` (Hash): Optional initial attributes for the span

**Yields:**
- `span` (OpenTelemetry::Trace::Span): The created span object

**Returns:** The result of the block

### `LaunchDarklyObservability.record_exception(exception, attributes: {})`

Records an exception in the current span and sets the span status to error.

**Parameters:**
- `exception` (Exception): The exception to record
- `attributes` (Hash): Optional additional attributes

### `LaunchDarklyObservability.current_trace_id`

Returns the current trace ID in hexadecimal format.

**Returns:** String (32 hex characters) or nil if no active span
