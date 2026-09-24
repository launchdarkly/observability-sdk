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
import '../../api/log_severity.dart';
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
  /// Records [message] at [severity], with an optional [stackTrace] and extra
  /// [attributes].
  void recordLog(
    String message, {
    required LogSeverity severity,
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
  /// Records a custom event named [eventName] with optional [data],
  /// [metricValue], and evaluation [context].
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
  /// Forwards an identify for the context described by [contextKeys] (kind to
  /// key) and [canonicalKey]; [completed] is whether the identify succeeded.
  void identify({
    required Map<String, String> contextKeys,
    required String canonicalKey,
    required bool completed,
  });
}

/// Records a screen view through the platform-appropriate pipeline.
///
/// - Web: emitted as a Dart `screen_view` span via `ScreenViewConvention`, gated
///   by `analytics.views`.
/// - Native (io): forwarded to the native observability SDK so it emits the
///   native `screen_view` span and the Session Replay `Navigate` timeline event.
///   Flutter routing is invisible to native screen detection (a single host
///   Activity/UIViewController), so screen views must be reported from Dart.
abstract interface class ScreenViewRecorder {
  /// Records a view of the screen called [name], with optional class, id,
  /// category, and custom [properties].
  void trackScreenView(
    String name, {
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  });
}

/// Records a click through the platform-appropriate pipeline.
///
/// - Web: emitted as a Dart `click` span via `ClickConvention`, gated by
///   `analytics.taps`.
/// - Native (io): forwarded to the native observability SDK so it emits the
///   native `click` span and the Session Replay `Click` timeline event. Flutter
///   draws its whole UI into one native view, so a native hit-test can only ever
///   name that view; the target has to be resolved in Dart and reported here.
///
/// [x] and [y] are in the platform's own units for `event.x`/`event.y`: physical
/// pixels on Android, logical pixels (UIKit points) on iOS. [timestampMillis] is
/// the epoch millisecond the click happened, needed because the native bridge is
/// asynchronous — see the Pigeon `trackClick` documentation.
abstract interface class ClickRecorder {
  /// Records a click on the described element; see the class documentation for
  /// the units of [x], [y], and [timestampMillis].
  void trackClick({
    String? id,
    String? tag,
    String? classname,
    String? text,
    String? xpath,
    int? x,
    int? y,
    int? timestampMillis,
    Map<String, Object?>? properties,
  });

  /// Announces whether Dart is currently resolving clicks, so the platform can
  /// stop reporting the taps Dart now describes.
  ///
  /// Only native has anything to suppress: its hit-test finds the single Flutter
  /// view for every tap and would report each one a second time, as that view.
  /// The announcement is a handshake rather than a fixed setting because native
  /// starts before the widget tree exists — a build that never installs Dart
  /// detection keeps its coarse native clicks instead of reporting none at all.
  void setEmbedderClickHandling(bool enabled);
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

  /// Builds the click recorder used by `trackClick` and automatic click capture.
  ClickRecorder createClickRecorder(ObservabilityConfig config);
}
