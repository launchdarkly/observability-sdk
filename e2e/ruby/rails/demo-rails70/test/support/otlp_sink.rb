# frozen_string_literal: true

require 'webrick'
require 'zlib'
require 'stringio'

# Load the OTLP protobuf message classes. These ship with the exporter gems the
# plugin already depends on, so we can decode the EXACT bytes the Ruby OTLP
# exporter puts on the wire.
require 'opentelemetry-exporter-otlp'
require 'opentelemetry-exporter-otlp-logs'

# A minimal, in-process OTLP/HTTP sink used by the E2E tests to assert that the
# Rails app actually EXPORTED telemetry over the wire.
#
# Why not e2e/mock-otel-server? That server parses JSON and gates reads on a
# browser-only `highlight.session_id` attribute. The Ruby OTLP exporter sends
# gzip-compressed binary protobuf with no session id, so it cannot be parsed
# there. This sink decodes the real OTLP protobuf using the proto classes
# shipped with the exporter gems — pure Ruby, no Docker/Node — so the whole
# repro runs under `bundle exec rake`, identically locally and in CI.
module OtlpSink
  # A decoded span (only the fields the tests assert on).
  Span = Struct.new(:name, :kind, :scope, :attributes, :events, keyword_init: true)
  # A decoded log record.
  LogRecord = Struct.new(:body, :severity, :attributes, keyword_init: true)

  class Server
    attr_reader :port

    def initialize(port: 4327)
      @port = port
      @spans = []
      @logs = []
      @mutex = Mutex.new
      @server = WEBrick::HTTPServer.new(
        Port: port,
        BindAddress: '127.0.0.1',
        Logger: WEBrick::Log.new(File::NULL),
        AccessLog: []
      )
      @server.mount_proc('/v1/traces') { |req, res| ingest_traces(req); ok(res) }
      @server.mount_proc('/v1/logs') { |req, res| ingest_logs(req); ok(res) }
      # Respond 200 to metrics so the exporter never sees an error, even though
      # the tests do not assert on metrics.
      @server.mount_proc('/v1/metrics') { |_req, res| ok(res) }
    end

    def start
      @thread = Thread.new { @server.start }
      self
    end

    def stop
      @server.shutdown
      @thread&.join(2)
    end

    def spans
      @mutex.synchronize { @spans.dup }
    end

    def logs
      @mutex.synchronize { @logs.dup }
    end

    def reset
      @mutex.synchronize do
        @spans.clear
        @logs.clear
      end
    end

    private

    def ok(res)
      res.status = 200
      res.body = 'OK'
    end

    def body_bytes(req)
      raw = req.body.to_s
      if req['content-encoding'].to_s.include?('gzip')
        Zlib::GzipReader.new(StringIO.new(raw)).read
      else
        raw
      end
    end

    def ingest_traces(req)
      req_msg = Opentelemetry::Proto::Collector::Trace::V1::ExportTraceServiceRequest.decode(body_bytes(req))
      parsed = []
      req_msg.resource_spans.each do |rs|
        rs.scope_spans.each do |ss|
          scope = ss.scope&.name
          ss.spans.each do |s|
            parsed << Span.new(
              name: s.name,
              kind: s.kind,
              scope: scope,
              attributes: kv(s.attributes),
              events: s.events.map { |e| { name: e.name, attributes: kv(e.attributes) } }
            )
          end
        end
      end
      @mutex.synchronize { @spans.concat(parsed) }
    rescue StandardError => e
      warn "[OtlpSink] trace decode error: #{e.class}: #{e.message}"
    end

    def ingest_logs(req)
      req_msg = Opentelemetry::Proto::Collector::Logs::V1::ExportLogsServiceRequest.decode(body_bytes(req))
      parsed = []
      req_msg.resource_logs.each do |rl|
        rl.scope_logs.each do |sl|
          sl.log_records.each do |lr|
            parsed << LogRecord.new(
              body: any_value(lr.body),
              severity: lr.severity_text,
              attributes: kv(lr.attributes)
            )
          end
        end
      end
      @mutex.synchronize { @logs.concat(parsed) }
    rescue StandardError => e
      warn "[OtlpSink] log decode error: #{e.class}: #{e.message}"
    end

    # Flatten a repeated KeyValue list into a plain Ruby hash.
    def kv(attributes)
      attributes.each_with_object({}) do |a, h|
        h[a.key] = any_value(a.value)
      end
    end

    # Extract the set field of an OTLP AnyValue.
    def any_value(value)
      return nil if value.nil?

      case value.value
      when :string_value then value.string_value
      when :bool_value then value.bool_value
      when :int_value then value.int_value
      when :double_value then value.double_value
      else value.string_value
      end
    end
  end
end
