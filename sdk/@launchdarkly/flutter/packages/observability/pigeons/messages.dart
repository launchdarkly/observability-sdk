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
  bool? pageViews;
  bool? trackEvents;
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
}
