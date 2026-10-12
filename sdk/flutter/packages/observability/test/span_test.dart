import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/api/span_status_code.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart';

/// Registers its own tracer provider, which is allowed once per isolate.
void main() {
  final exported = <ReadOnlySpan>[];

  setUpAll(() {
    otel.registerGlobalTracerProvider(
      TracerProviderBase(
        processors: [SimpleSpanProcessor(_CollectingExporter(exported))],
      ),
    );
  });

  setUp(exported.clear);

  ReadOnlySpan named(String name) =>
      exported.singleWhere((s) => s.name == name);

  test('ending a span twice exports it once', () {
    final span = LDObserve.startSpan('twice');
    span.end();
    span.end();

    expect(exported.where((s) => s.name == 'twice'), hasLength(1));
  });

  test('withSpan returns the result and ends the span', () {
    final result = LDObserve.withSpan('sync', (span) => 42);

    expect(result, 42);
    expect(named('sync').status.code, otel.StatusCode.unset);
  });

  test('withSpan stays current across awaits and ends on completion', () async {
    final future = LDObserve.withSpan('outer', (span) async {
      await Future<void>.delayed(Duration.zero);
      LDObserve.startSpan('inner').end();
      return 'done';
    });
    expect(exported.where((s) => s.name == 'outer'), isEmpty);

    expect(await future, 'done');
    await pumpEventQueue();

    final outer = named('outer');
    expect(
      named('inner').parentSpanId.toString(),
      outer.spanContext.spanId.toString(),
    );
  });

  test('withSpan records a thrown error and rethrows it', () {
    expect(
      () => LDObserve.withSpan<void>('throws', (span) => throw StateError('x')),
      throwsStateError,
    );

    final span = named('throws');
    expect(span.status.code, otel.StatusCode.error);
    expect(span.events.single.name, 'exception');
  });

  test('withSpan records a failed future and passes it on', () async {
    await expectLater(
      LDObserve.withSpan('fails', (span) async {
        await Future<void>.delayed(Duration.zero);
        throw StateError('x');
      }),
      throwsStateError,
    );
    await pumpEventQueue();

    expect(named('fails').status.code, otel.StatusCode.error);
  });

  test('a span started with startSpan can set an error status', () {
    LDObserve.startSpan('manual')
      ..setStatus(SpanStatusCode.error)
      ..end();

    expect(named('manual').status.code, otel.StatusCode.error);
  });
}

class _CollectingExporter implements SpanExporter {
  _CollectingExporter(this._spans);

  final List<ReadOnlySpan> _spans;

  @override
  void export(List<ReadOnlySpan> spans) => _spans.addAll(spans);

  @override
  void forceFlush() {}

  @override
  void shutdown() {}
}
