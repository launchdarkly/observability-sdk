// Pigeon schema for the LDNative bridge. Mirrors the
// LDObservabilityOptions / LDSessionReplayOptions / LDPrivacyOptions DTOs from
// sdk/@launchdarkly/mobile-dotnet/android/native/LDObserve/.../OptionsBridge.kt
// and sdk/@launchdarkly/mobile-dotnet/macios/native/LDObserve/Sources/OptionsBridge.swift.
//
// Regenerate with:
//   dart run pigeon --input pigeons/messages.dart

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/platform/io/messages.g.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        'android/src/main/kotlin/com/launchdarkly/launchdarkly_flutter_observability/Messages.g.kt',
    kotlinOptions: KotlinOptions(
      package: 'com.launchdarkly.launchdarkly_flutter_observability',
    ),
    swiftOut:
        'ios/launchdarkly_flutter_observability/Sources/launchdarkly_flutter_observability/Messages.g.swift',
    swiftOptions: SwiftOptions(),
    dartPackageName: 'launchdarkly_flutter_observability',
  ),
)
class LDInstrumentationOptions {
  bool? networkRequests;
  bool? launchTimes;
  bool? crashReporting;
}

class LDTracesOptions {
  bool? includeErrors;
  bool? includeSpans;
}

class LDAnalyticsOptions {
  bool? taps;
  bool? views;
  bool? trackEvents;
  bool? appLifecycle;
  bool? appLaunch;
}

class LDObservabilityOptions {
  bool? isEnabled;
  String? serviceName;
  String? serviceVersion;
  String? otlpEndpoint;
  String? backendUrl;
  String? contextFriendlyName;
  Map<String, Object?>? attributes;
  Map<String, String>? customHeaders;
  int? sessionBackgroundTimeoutMillis;
  int? logsApiLevel;
  LDTracesOptions? traces;
  bool? metricsEnabled;
  LDAnalyticsOptions? analytics;
  LDInstrumentationOptions? instrumentation;
}

class LDPrivacyOptions {
  bool? maskTextInputs;
  bool? maskWebViews;
  bool? maskLabels;
  bool? maskImages;
  double? minimumAlpha;
}

class LDSessionReplayOptions {
  bool? isEnabled;
  String? serviceName;
  double? sampleRate;
  double? frameRate;
  double? scale;
  double? imageQuality;
  LDPrivacyOptions? privacy;
}

class LDStartResult {
  String? nativeVersion;
}

/// An event recorded on a span, forwarded to the native tracer.
class LDSpanEvent {
  String? name;
  Map<String, Object?>? attributes;
}

/// A completed Dart span, forwarded to the native tracer so the native
/// pipeline re-creates it (stamping `session.id`, sampling, batching).
///
/// Mirrors the data carried by MAUI's `TraceBuilderAdapter`
/// (`sdk/@launchdarkly/mobile-dotnet/observability/bridge/TraceBuilderAdapter.cs`).
class LDSpanData {
  String? name;

  /// Span start as epoch seconds.
  double? startTimeSeconds;

  /// Span end as epoch seconds.
  double? endTimeSeconds;

  /// 32-char hex trace id.
  String? traceId;

  /// 16-char hex span id.
  String? spanId;

  /// 16-char hex parent span id, or empty for a root span.
  String? parentSpanId;
  Map<String, Object?>? attributes;
  List<LDSpanEvent?>? events;

  /// 0 = unset, 1 = ok, 2 = error.
  int? statusCode;
}

/// A log record forwarded to the native logger so it is emitted as a real
/// OpenTelemetry `LogRecord` (stamped with `session.id` and correlated with
/// the active span).
class LDLogRecord {
  String? message;

  /// OpenTelemetry severity number (e.g. 9 = INFO, 13 = WARN, 17 = ERROR).
  int? severityNumber;

  /// 32-char hex trace id of the active span, or null when none.
  String? traceId;

  /// 16-char hex span id of the active span, or null when none.
  String? spanId;
  Map<String, Object?>? attributes;
}

@HostApi()
abstract class LDNativeApi {
  @async
  LDStartResult start(
    String mobileKey,
    LDObservabilityOptions observability,
    LDSessionReplayOptions replay,
    String observabilityVersion,
  );

  /// Forwards completed Dart spans to the native tracer. Native re-creates each
  /// span so the native pipeline stamps `session.id` and exports it.
  void exportSpans(List<LDSpanData> spans);

  /// Forwards a Dart log to the native logger so it is emitted as a native
  /// `LogRecord` with `session.id` and trace/span correlation.
  void recordLog(LDLogRecord log);

  /// Forwards a custom track event to the native observability SDK so it emits
  /// the native `track` span (gated by `analytics.trackEvents`) and the Session
  /// Replay `Track` timeline event (always). `data` carries the optional event
  /// payload as a JSON object. `contextKeys` carries the evaluation context's
  /// kind -> key pairs (from the LaunchDarkly client's `afterTrack` hook) so the
  /// native `track` span is attributed to the same context the web SDK records;
  /// only the span is annotated, not the Session Replay `Track` payload.
  void track(
    String key,
    Map<String, Object?>? data,
    double? metricValue,
    Map<String, String>? contextKeys,
  );

  /// Forwards an `identify` to the native observability SDK and Session Replay.
  /// Native observability caches `contextKeys` so manual `LDObserve.track` calls
  /// (which carry no context) are attributed to the active context, and Session
  /// Replay records who the user is on the active recording. `contextKeys`
  /// carries the context's kind -> key pairs, `canonicalKey` the fully-qualified
  /// key, and `completed` whether the identify finished successfully (native
  /// ignores incomplete identifies). Mirrors MAUI's
  /// `ObservabilityHook.AfterIdentify` /  `SessionReplayHook.AfterIdentify`.
  void identify(
    Map<String, String> contextKeys,
    String canonicalKey,
    bool completed,
  );

  /// Forwards a screen view to the native observability SDK so it emits the
  /// native `screen_view` span and the Session Replay `Navigate` timeline event.
  /// Flutter owns its own routing inside a single host Activity/UIViewController,
  /// so native screen detection never sees Flutter route changes; screen views
  /// must therefore be reported from Dart (e.g. via a `NavigatorObserver`).
  /// [name] is the screen/route name; [screenClass], [screenId] and [category]
  /// are optional classifiers, and [properties] carries optional extra
  /// attributes attached to the `screen_view` span.
  void trackScreenView(
    String name,
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  );

  /// Forwards a click to the native observability SDK so it emits the native
  /// `click` span and the Session Replay `Click` timeline event.
  ///
  /// Flutter draws its entire UI into one native view, so a native hit-test
  /// bottoms out at `FlutterSurfaceView` / `FlutterView` for every tap no matter
  /// which widget was pressed. Only Dart can see the widget tree, so the widget
  /// is resolved here and reported through this method.
  ///
  /// [id] is the stable element identifier, [tag] the widget type (e.g.
  /// `ElevatedButton`), [classname] a more specific class name when one is
  /// known, [text] the element's visible label, [xpath] the widget's path within
  /// the tree, and [x]/[y] the tap coordinates in the platform's own units
  /// (physical pixels on Android, logical pixels on iOS). [screenId] is left
  /// null so native fills it from the screen stack Dart already keeps current.
  /// [timestampMillis] is captured in Dart at pointer-up: the bridge is
  /// asynchronous, so without it the replay marker would land wherever the call
  /// happens to arrive rather than with the pointer trail it belongs to.
  void trackClick(
    String? id,
    String? tag,
    String? classname,
    String? text,
    String? xpath,
    String? screenId,
    int? x,
    int? y,
    int? timestampMillis,
    Map<String, Object?>? properties,
  );

  /// Tells native that Dart now resolves clicks for the Flutter view, so native
  /// must stop reporting taps that land on it.
  ///
  /// Handshake rather than a fixed setting: native starts before the widget tree
  /// exists, so suppressing Flutter-view taps unconditionally would leave an app
  /// that never installs Dart click detection reporting no clicks at all — worse
  /// than today's coarse `FlutterSurfaceView`. Enabled when Dart's detection
  /// installs and disabled when it is torn down. Taps on native views elsewhere
  /// in the app (an add-to-app host's own screens) are never affected.
  void setEmbedderClickHandling(bool enabled);

  /// Stops Session Replay capture as part of `LDObserve.shutdown`, replying
  /// only once capture has stopped (and, on Android, queued replay events have
  /// been flushed; the iOS SDK exposes no flush).
  ///
  /// Only Session Replay can be stopped: neither native observability SDK has
  /// a teardown, so its automatic instrumentation (crash reporting, network
  /// requests, launch times, native lifecycle spans) keeps running until the
  /// process exits.
  @async
  void shutdown();
}
