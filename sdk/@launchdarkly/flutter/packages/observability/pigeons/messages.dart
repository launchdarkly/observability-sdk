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
  double? frameRate;
  double? scale;
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
}
