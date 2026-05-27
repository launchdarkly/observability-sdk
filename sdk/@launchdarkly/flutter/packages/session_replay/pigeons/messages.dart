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
    dartOut: 'lib/src/messages.g.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        'android/src/main/kotlin/com/launchdarkly/launchdarkly_flutter_session_replay/Messages.g.kt',
    kotlinOptions: KotlinOptions(
      package: 'com.launchdarkly.launchdarkly_flutter_session_replay',
    ),
    swiftOut:
        'ios/launchdarkly_flutter_session_replay/Sources/launchdarkly_flutter_session_replay/Messages.g.swift',
    swiftOptions: SwiftOptions(),
    dartPackageName: 'launchdarkly_flutter_session_replay',
  ),
)
class LDInstrumentationOptions {
  bool? networkRequests;
  bool? launchTimes;
}

class LDObservabilityOptions {
  bool? isEnabled;
  String? serviceName;
  String? serviceVersion;
  String? otlpEndpoint;
  String? backendUrl;
  String? contextFriendlyName;
  Map<String, Object?>? attributes;
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
  LDPrivacyOptions? privacy;
}

class LDStartResult {
  String? nativeVersion;
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
}
