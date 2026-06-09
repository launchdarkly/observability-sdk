// Platform-specific construction of the span/log exporters.
//
// Mirrors the MAUI approach (see
// `sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDTraceExporter.cs`
// and `observe/plugin/ObservabilityService.cs`): on mobile, Dart-recorded spans
// and logs are forwarded to the native iOS/Android SDK so the native pipeline
// stamps `session.id` (and applies sampling/batching), exactly like the
// standalone Android/iOS SDKs. On web, the Dart OpenTelemetry pipeline is used
// directly.

import 'package:opentelemetry/sdk.dart' show SpanProcessor;

import '../../api/attribute.dart';
import '../../plugin/observability_config.dart';

// Selects the implementation at compile time, matching the platform split used
// by `LDObservePlatform`. The native (io) implementation forwards to the pigeon
// bridge; the web implementation uses the Dart OTLP exporters. Neither is
// compiled into the other's build.
import 'exporter_factory_stub.dart'
    if (dart.library.io) 'exporter_factory_io.dart'
    if (dart.library.js_interop) 'exporter_factory_web.dart';

/// Records a single log through the platform-appropriate pipeline.
///
/// - Web: emitted as a span event (the Dart OpenTelemetry pipeline has no
///   standalone logs exporter), preserving the existing behaviour.
/// - Native (io): forwarded to the native logger as a real OpenTelemetry
///   `LogRecord`, so it is stamped with `session.id` and correlated with the
///   active span — matching the Android/iOS SDKs.
abstract interface class LogRecorder {
  void recordLog(
    String message, {
    required String severity,
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  });
}

/// Factory for the span and log exporters used by the observability pipeline.
///
/// The concrete implementation is selected per build target via conditional
/// imports. Use [ObservabilityExporters.instance] to obtain it.
abstract interface class ObservabilityExporters {
  /// The platform implementation selected for the current build target.
  static final ObservabilityExporters instance = createObservabilityExporters();

  /// Builds the span processors wired into the Dart `TracerProvider`.
  List<SpanProcessor> createSpanProcessors(ObservabilityConfig config);

  /// Builds the log recorder used by `recordLog`.
  LogRecorder createLogRecorder(ObservabilityConfig config);
}
