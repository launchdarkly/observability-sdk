require 'sinatra'
require 'launchdarkly-server-sdk'
require 'launchdarkly_observability'
require 'logger'
require 'json'

use LaunchDarklyObservability::Middleware

$logger = Logger.new($stdout)

observability_plugin = LaunchDarklyObservability::Plugin.new(
  service_name: 'launchdarkly-sinatra-demo',
  service_version: '1.0.0'
)

sdk_key = ENV.fetch('LAUNCHDARKLY_SDK_KEY') do
  $logger.warn '[LaunchDarkly] LAUNCHDARKLY_SDK_KEY not set, client will not connect'
  nil
end
config = LaunchDarkly::Config.new(plugins: [observability_plugin])
$ld_client = LaunchDarkly::LDClient.new(sdk_key, config)

if ENV['DEBUG'] == 'true'
  debug_exporter = Class.new do
    def export(spans, timeout: nil)
      spans.each do |span|
        attrs = span.attributes&.map { |k, v| "#{k}=#{v.inspect}" }&.join(', ')
        events = span.events&.map(&:name)&.join(', ')
        duration_ms = ((span.end_timestamp - span.start_timestamp) / 1e6).round(2)
        status = span.status.ok? ? 'OK' : (span.status.description || span.status.code.to_s)
        parts = [
          "[Span] #{span.name}",
          "trace=#{span.hex_trace_id[0..15]}...",
          "duration=#{duration_ms}ms",
          "status=#{status}"
        ]
        parts << "attrs={#{attrs}}" if attrs && !attrs.empty?
        parts << "events=[#{events}]" if events && !events.empty?
        $stderr.puts parts.join(' | ')
      end
      OpenTelemetry::SDK::Trace::Export::SUCCESS
    end

    def force_flush(timeout: nil) = OpenTelemetry::SDK::Trace::Export::SUCCESS
    def shutdown(timeout: nil) = OpenTelemetry::SDK::Trace::Export::SUCCESS
  end

  OpenTelemetry.tracer_provider.add_span_processor(
    OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(debug_exporter.new)
  )
  $logger.level = Logger::DEBUG
  $logger.info '[Debug] Span console exporter enabled'
end

at_exit { $ld_client.close }

def ld_context
  LaunchDarkly::LDContext.create({ key: 'sinatra-demo-user', kind: 'user' })
end

# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

get '/' do
  state = $ld_client.all_flags_state(ld_context)
  @flags_valid = state.valid?
  @flags = state.values_map
  @trace_id = LaunchDarklyObservability.current_trace_id
  erb :home
end

post '/traces' do
  content_type :json
  LaunchDarklyObservability.in_span('example-trace-outer') do |outer_span|
    sleep(0.1)
    LaunchDarklyObservability.in_span('example-trace-inner', attributes: { 'trace.operation' => 'work' }) do
      sleep(0.2)
    end
    outer_span.set_attribute('trace.operation', 'complete')
  end
  { status: 'ok', message: 'Trace created with nested spans' }.to_json
end

post '/logs' do
  content_type :json
  $logger.info('hello, world! foo=bar')
  $logger.info(test: 'ing', foo: 'bar')
  { status: 'ok', message: 'Logs written' }.to_json
end

post '/errors' do
  content_type :json
  LaunchDarklyObservability.in_span('error-handling-example') do
    begin
      1 / 0
    rescue StandardError => e
      LaunchDarklyObservability.record_exception(e)
      $logger.error("Exception occurred: #{e.message}")
    end
  end
  { status: 'ok', message: 'Error recorded via record_exception' }.to_json
end

post '/flags/evaluate' do
  content_type :json
  body = JSON.parse(request.body.read) rescue {}
  flag_key = body['flag_key'] || 'test-flag'
  detail = $ld_client.variation_detail(flag_key, ld_context, false)
  {
    flag_key: flag_key,
    value: detail.value,
    variation_index: detail.variation_index,
    reason: detail.reason
  }.to_json
end

get '/flags' do
  content_type :json
  state = $ld_client.all_flags_state(ld_context)
  { valid: state.valid?, flags: state.values_map }.to_json
end

__END__

@@ home
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>LaunchDarkly Sinatra Demo</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; }
    body {
      font-family: system-ui, -apple-system, sans-serif;
      max-width: 720px;
      margin: 2rem auto;
      padding: 0 1rem;
      color: #1a1a1a;
      background: #fafafa;
    }
    h1 { margin-bottom: 0.25rem; }
    h1 small { font-weight: 400; color: #666; font-size: 0.5em; }
    h2 { margin-top: 2rem; border-bottom: 1px solid #ddd; padding-bottom: 0.25rem; }
    .status { margin-bottom: 2rem; color: #555; }
    .status code { background: #eee; padding: 2px 6px; border-radius: 3px; }

    .card-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .card {
      border: 1px solid #ddd;
      border-radius: 8px;
      padding: 1rem;
      background: #fff;
    }
    .card h3 { margin: 0 0 0.5rem; }
    .card p { margin: 0 0 0.75rem; color: #555; font-size: 0.9rem; }
    button {
      cursor: pointer;
      border: none;
      border-radius: 6px;
      padding: 0.5rem 1rem;
      font-size: 0.85rem;
      font-weight: 500;
      color: #fff;
      transition: opacity 0.15s;
    }
    button:hover { opacity: 0.85; }
    button:disabled { opacity: 0.5; cursor: wait; }
    .btn-blue   { background: #3b82f6; }
    .btn-green  { background: #22c55e; }
    .btn-red    { background: #ef4444; }
    .btn-purple { background: #a855f7; }

    .result {
      margin-top: 0.5rem;
      font-size: 0.8rem;
      min-height: 1.2em;
      color: #666;
    }
    .result.ok { color: #16a34a; }
    .result.err { color: #dc2626; }

    table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; }
    th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid #eee; font-size: 0.85rem; }
    th { color: #888; font-weight: 500; }
    td code { background: #f3f4f6; padding: 1px 4px; border-radius: 3px; }

    .flag-input { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; }
    .flag-input input {
      flex: 1;
      padding: 0.4rem 0.6rem;
      border: 1px solid #ccc;
      border-radius: 6px;
      font-size: 0.85rem;
    }
  </style>
</head>
<body>
  <h1>LaunchDarkly Observability <small>Sinatra Demo</small></h1>
  <div class="status">
    SDK: <strong><%= @flags_valid ? '&#10003; Connected' : '&#10007; Not connected' %></strong>
    &middot; Flags: <strong><%= @flags.size %></strong>
    <% if @trace_id %>
      &middot; Trace: <code><%= @trace_id %></code>
    <% end %>
  </div>

  <h2>Actions</h2>
  <div class="card-grid">
    <div class="card">
      <h3>Traces</h3>
      <p>Creates nested spans (outer + inner) with a short sleep.</p>
      <button class="btn-blue" onclick="fire(this, '/traces')">Create Trace</button>
      <div class="result" id="res-traces"></div>
    </div>

    <div class="card">
      <h3>Logs</h3>
      <p>Writes two log entries (string and structured hash).</p>
      <button class="btn-green" onclick="fire(this, '/logs')">Write Logs</button>
      <div class="result" id="res-logs"></div>
    </div>

    <div class="card">
      <h3>Errors</h3>
      <p>Triggers a ZeroDivisionError and records it via <code>record_exception</code>.</p>
      <button class="btn-red" onclick="fire(this, '/errors')">Raise Error</button>
      <div class="result" id="res-errors"></div>
    </div>

    <div class="card">
      <h3>Flag Evaluation</h3>
      <p>Evaluates a single flag with <code>variation_detail</code>.</p>
      <div class="flag-input">
        <input type="text" id="flag-key" placeholder="flag key" value="test-flag">
      </div>
      <button class="btn-purple" onclick="evalFlag(this)">Evaluate</button>
      <div class="result" id="res-flags"></div>
    </div>
  </div>

  <% if @flags.any? %>
    <h2>All Flags (<%= @flags.size %>)</h2>
    <table>
      <thead><tr><th>Key</th><th>Value</th></tr></thead>
      <tbody>
        <% @flags.sort.each do |key, value| %>
          <tr><td><code><%= key %></code></td><td><code><%= value.inspect %></code></td></tr>
        <% end %>
      </tbody>
    </table>
  <% end %>

  <script>
    async function fire(btn, path) {
      const id = 'res-' + path.split('/').pop();
      const el = document.getElementById(id);
      el.textContent = '';
      el.className = 'result';
      btn.disabled = true;
      try {
        const res = await fetch(path, { method: 'POST' });
        const json = await res.json();
        el.textContent = json.message;
        el.className = 'result ok';
      } catch (e) {
        el.textContent = e.message;
        el.className = 'result err';
      } finally {
        btn.disabled = false;
      }
    }

    async function evalFlag(btn) {
      const el = document.getElementById('res-flags');
      const key = document.getElementById('flag-key').value || 'test-flag';
      el.textContent = '';
      el.className = 'result';
      btn.disabled = true;
      try {
        const res = await fetch('/flags/evaluate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ flag_key: key })
        });
        const json = await res.json();
        el.textContent = key + ' = ' + JSON.stringify(json.value) + ' (reason: ' + JSON.stringify(json.reason) + ')';
        el.className = 'result ok';
      } catch (e) {
        el.textContent = e.message;
        el.className = 'result err';
      } finally {
        btn.disabled = false;
      }
    }
  </script>
</body>
</html>
