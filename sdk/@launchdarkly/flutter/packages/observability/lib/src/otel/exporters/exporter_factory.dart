// Platform-specific construction of the span/log exporters.
//
// Mirrors the MAUI approach (see
// `sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDTraceExporter.cs`
// and `observe/plugin/ObservabilityService.cs`): on mobile, Dart-recorded spans
// and logs are forwarded to the native iOS/Android SDK so the native pipeline
// stamps `session.id` (and applies sampling/batching), exactly like the
// standalone Android/iOS SDKs. On web, the Dart OpenTelemetry pipeline is used
// directly.

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
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

/// Records a custom `track` event through the platform-appropriate pipeline.
///
/// - Web: emitted as a Dart `track` span via `TrackConvention`, gated by
///   `analytics.trackEvents`.
/// - Native (io): forwarded to the native observability SDK so it emits the
///   native `track` span (gated natively) and the Session Replay `Track`
///   timeline event (always). The Dart span is intentionally not emitted on
///   mobile, so `track` is not double-counted.
abstract interface class TrackRecorder {
  void track(
    String eventName, {
    LDValue? data,
    num? metricValue,
    LDContext? context,
  });
}

/// Forwards an `identify` through the platform-appropriate pipeline.
///
/// - Web: no-op. The Dart pipeline has no Session Replay and the manual track
///   path already attributes spans from the live context, so there is nothing
///   to cache.
/// - Native (io): forwarded to the native observability SDK (which caches the
///   context keys so the manual `LDObserve.track` path is attributed to the
///   active context) and Session Replay (which records who the user is on the
///   active recording). Mirrors MAUI's `ObservabilityHook.AfterIdentify`.
abstract interface class IdentifyRecorder {
  void identify({
    required Map<String, String> contextKeys,
    required String canonicalKey,
    required bool completed,
  });
}

/// Records a screen view through the platform-appropriate pipeline.
///
/// - Web: emitted as a Dart `screen_view` span via `ScreenViewConvention`, gated
///   by `analytics.pageViews`.
/// - Native (io): forwarded to the native observability SDK so it emits the
///   native `screen_view` span and the Session Replay `Navigate` timeline event.
///   Flutter routing is invisible to native screen detection (a single host
///   Activity/UIViewController), so screen views must be reported from Dart.
abstract interface class ScreenViewRecorder {
  void trackScreenView(
    String name, {
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
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

  /// Builds the track recorder used by `track`.
  TrackRecorder createTrackRecorder(ObservabilityConfig config);

  /// Builds the identify recorder used by `identify`.
  IdentifyRecorder createIdentifyRecorder(ObservabilityConfig config);

  /// Builds the screen-view recorder used by `trackScreenView`.
  ScreenViewRecorder createScreenViewRecorder(ObservabilityConfig config);
}
