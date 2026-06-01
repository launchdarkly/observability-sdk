// E2E test for O11Y-1524 — verify Observe.track emits a launchdarkly.track
// span over real OTLP to the local backend (devbox SSH-forwarded), and that
// the row lands in ClickHouse default.traces with the expected attributes.
//
// Run with:
//   flutter test test/observe_e2e_test.dart
//
// Prereqs:
//   - Backend HTTP listener at http://localhost:9096 with OTLP at /otel/v1/traces
//   - ClickHouse HTTP at http://localhost:8123 with default.traces table
//
// This is a real-network test; it's intentionally NOT registered as a default
// unit-test target.

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/observe.dart';
import 'package:opentelemetry/api.dart' as otel_api;
import 'package:opentelemetry/sdk.dart' as otel_sdk;

const _backendHost = 'localhost';
const _backendPort = 9096;
const _otlpPath = '/otel/v1/traces';
const _chHost = 'localhost';
const _chPort = 8123;
const _projectId = '1';

/// Mirror of Observe.shutdown()-restorable state for tests. We don't call
/// Observe.shutdown() here because it permanently disables the singleton.

Future<String> _chQueryString(String query) async {
  final client = HttpClient();
  try {
    final uri = Uri(
      scheme: 'http',
      host: _chHost,
      port: _chPort,
      path: '/',
      queryParameters: {'query': query},
    );
    final req = await client.getUrl(uri);
    final resp = await req.close();
    final body = await resp.transform(utf8.decoder).join();
    return body;
  } finally {
    client.close();
  }
}

void main() {
  // CRITICAL: do NOT call TestWidgetsFlutterBinding.ensureInitialized() — its
  // `createHttpClient` override forces every `HttpClient` (and `http.Client`
  // built on top of it) to return 400, which would block both the OTLP POST
  // from CollectorExporter and our ClickHouse polls. Explicitly clear any
  // HttpOverrides that an earlier test or binding may have installed.
  HttpOverrides.global = null;

  test(
    'Observe.track end-to-end: span lands in ClickHouse default.traces',
    () async {
      // 1. Generate unique event key.
      final eventKey =
          'test-event-flutter-${DateTime.now().microsecondsSinceEpoch}';
      // ignore: avoid_print
      print('E2E event key: $eventKey');

      // 2. Build a real TracerProvider with OTLP HTTP exporter pointed at the
      //    devbox backend, mirroring lib/src/otel/setup.dart but with a faster
      //    flush cadence.
      final resourceAttributes = <otel_api.Attribute>[
        otel_api.Attribute.fromString('highlight.project_id', _projectId),
        otel_api.Attribute.fromString('service.name', 'flutter-e2e-test'),
        otel_api.Attribute.fromString('service.version', '0.0.0-e2e'),
      ];

      final exporter = otel_sdk.CollectorExporter(
        Uri.parse('http://$_backendHost:$_backendPort$_otlpPath'),
      );
      final batchProcessor = otel_sdk.BatchSpanProcessor(
        exporter,
        scheduledDelayMillis: 200,
      );
      final tracerProvider = otel_sdk.TracerProviderBase(
        processors: [batchProcessor],
        resource: otel_sdk.Resource(resourceAttributes),
      );

      // The opentelemetry-dart package allows only ONE registerGlobalTracerProvider
      // call per process. If a previous test or `Observe.shutdown()` ran in the
      // same `flutter test` invocation, calling register again throws. Guard it.
      try {
        otel_api.registerGlobalTracerProvider(tracerProvider);
      } catch (_) {
        // already registered — swap by setting global via reflection is not
        // possible. Fall back to using the provider directly is also not
        // possible because Observe.startSpan reads otel.globalTracerProvider.
        // For a freshly-spawned `flutter test` process, the first registration
        // here is THE global, so this branch should never hit in practice.
      }

      // 3. Seed Observe's process-wide caches the way the plugin's
      //    afterIdentify + registration would, so that the resulting
      //    launchdarkly.track span carries representative context/SDK
      //    metadata.
      setLDContextKeyAttributes(<String, Attribute>{
        'user': StringAttribute('alice'),
        'org': StringAttribute('team-a'),
      });
      setLDSdkMetadataAttributes(<String, Attribute>{
        'telemetry.sdk.name': StringAttribute('launchdarkly-flutter'),
        'telemetry.sdk.version': StringAttribute('0.0.0-e2e'),
        'launchdarkly.application.id': StringAttribute('flutter-e2e-test'),
        'feature_flag.set.id': StringAttribute(_projectId),
        'feature_flag.provider.name': StringAttribute('launchdarkly'),
      });
      setProductAnalyticsTrackEventsForTest(true);

      // 4. Call Observe.track with `data` containing a string field and a
      //    numericValue. The hook contract under test is: the resulting span
      //    should carry key=<eventKey>, value=42.0, foo='bar', plus context
      //    and SDK metadata attrs.
      Observe.track(
        eventKey,
        data: LDValue.buildObject().addString('foo', 'bar').build(),
        numericValue: 42.0,
      );

      // 5. Force-flush the batch processor so the exporter posts immediately.
      tracerProvider.forceFlush();
      // BatchSpanProcessor.forceFlush is synchronous and just calls
      // exporter.export(); the HTTP POST itself is fire-and-forget on a
      // microtask. Give it a moment to actually hit the wire before polling.
      await Future<void>.delayed(const Duration(milliseconds: 500));

      // 6. Poll ClickHouse for our row. The OTLP handler -> Kafka -> worker
      //    -> CH pipeline can take a few seconds in the devbox setup.
      final pollQuery =
          "SELECT SpanName, ProjectId, TraceAttributes['key'] AS k, "
          "TraceAttributes['value'] AS v, TraceAttributes['foo'] AS foo, "
          "TraceAttributes['user'] AS user_attr, "
          "TraceAttributes['feature_flag.provider.name'] AS provider_name "
          "FROM default.traces WHERE TraceAttributes['key'] = '$eventKey' "
          "AND SpanName = 'launchdarkly.track' LIMIT 1 FORMAT JSON";

      final start = DateTime.now();
      Map<String, dynamic>? row;
      String lastBody = '';
      while (DateTime.now().difference(start) < const Duration(seconds: 45)) {
        final body = await _chQueryString(pollQuery);
        lastBody = body;
        final parsed = jsonDecode(body) as Map<String, dynamic>;
        final data = parsed['data'] as List<dynamic>?;
        if (data != null && data.isNotEmpty) {
          row = data.first as Map<String, dynamic>;
          break;
        }
        await Future<void>.delayed(const Duration(seconds: 1));
      }

      final elapsed = DateTime.now().difference(start);
      // ignore: avoid_print
      print('Time-to-trace: ${elapsed.inMilliseconds}ms');
      // ignore: avoid_print
      print('Last CH body: $lastBody');

      expect(
        row,
        isNotNull,
        reason:
            'launchdarkly.track span never appeared in default.traces. '
            'Last CH response: $lastBody',
      );

      // 7. Assert key fields.
      expect(row!['SpanName'], 'launchdarkly.track');
      expect(row['ProjectId'].toString(), _projectId);
      expect(row['k'], eventKey);
      // numericValue=42.0 is emitted as a double-typed OTLP attribute; CH
      // serializes Map(String, String) as the stringified value.
      expect(row['v'], '42');
      expect(row['foo'], 'bar');
      expect(row['user_attr'], 'alice');
      expect(row['provider_name'], 'launchdarkly');
    },
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
