import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

import 'my_app.dart';

class LDSingleton {
  static LDClient? client;
}

/// Selects the LaunchDarkly credential for the current build target.
///
/// Web builds require a client-side ID; all other platforms require a mobile
/// key. Reading both compile-time constants and choosing by platform lets a
/// single `dart_defines.json` hold both keys at once (which
/// `CredentialSource.fromEnvironment()` forbids), so switching platforms is
/// just a matter of selecting a different device — no file edits needed.
String _ldCredential() {
  const clientSideId = String.fromEnvironment('LAUNCHDARKLY_CLIENT_SIDE_ID');
  const mobileKey = String.fromEnvironment('LAUNCHDARKLY_MOBILE_KEY');
  final credential = kIsWeb ? clientSideId : mobileKey;
  if (credential.isEmpty) {
    throw Exception(
      kIsWeb
          ? 'Web builds require LAUNCHDARKLY_CLIENT_SIDE_ID to be set in '
                'dart_defines.json.'
          : 'Non-web builds require LAUNCHDARKLY_MOBILE_KEY to be set in '
                'dart_defines.json.',
    );
  }
  return credential;
}

void main() {
  runZonedGuarded(
    () {
      WidgetsFlutterBinding.ensureInitialized();

      LDSingleton.client = LDClient(
        LDConfig(
          // Both the mobile key and client-side ID can live in
          // `dart_defines.json` at the same time; [_ldCredential] picks the
          // right one for the current platform. This is unlike
          // `CredentialSource.fromEnvironment()`, which rejects having both
          // set — so switching between web and mobile only means picking a
          // different device/launch config, with no file edits.
          _ldCredential(),
          AutoEnvAttributes.enabled,
        ),
        LDContextBuilder().kind('user', 'bob').build(),
      );

      LDSingleton.client!.start();

      // Boots the native LaunchDarkly observability + session replay stack.
      // Mirrors the two `LDObserve.Init(...)` variants in
      // sdk/@launchdarkly/mobile-dotnet/sample/MauiProgram.cs.
      _startObservability();

      // Report any errors handled by flutter.
      FlutterError.onError = (FlutterErrorDetails details) {
        LDObserve.recordException(details.exception, stackTrace: details.stack);
      };

      runApp(const SessionReplayCapture(child: MyApp()));
    },
    (err, stack) {
      // Report any errors reported from the guarded zone.
      LDObserve.recordException(err, stackTrace: stack);

      // Any additional default error handling.
    },
    // Used to intercept print statements. Generally print statements in
    // production are treated as a warning and this is not required.
    zoneSpecification: LDObserve.zoneSpecification(),
  );
}

/// Boots LaunchDarkly observability + session replay.
///
/// Mirrors the two `LDObserve.Init(...)` overloads from the MAUI sample
/// (`sdk/@launchdarkly/mobile-dotnet/sample/MauiProgram.cs`). Only the
/// LaunchDarkly client backed variant is active; the standalone variant is
/// shown below for reference.
void _startObservability() {
  final observability = ObservabilityOptions(
    serviceName: 'flutter-sample-app',
    // This could be a semantic version or a git commit hash. This demonstrates
    // how to use an environment variable to set the hash.
    // flutter build --dart-define GIT_SHA=$(git rev-parse HEAD) --dart-define LAUNCHDARKLY_MOBILE_KEY=<my-mobile-key>
    serviceVersion: const String.fromEnvironment(
      'GIT_SHA',
      defaultValue: 'no-version',
    ),
    instrumentation: InstrumentationOptions(
      networkRequests: true,
      launchTimes: true,
      debugPrint: DebugPrintSetting.always(),
    ),
  );
  const replay = SessionReplayOptions(
    isEnabled: true,
    privacy: PrivacyOptions(
      maskTextInputs: true,
      maskWebViews: false,
      maskLabels: false,
    ),
  );

  // Client-backed variant (active): registers the observability plugin on the
  // LaunchDarkly client, mirroring `LDObserve.Init(client, ...)`.
  LDObserve.init(
    LDSingleton.client!,
    observability: observability,
    replay: replay,
  );

  // Standalone variant (no LaunchDarkly client), mirroring
  // `LDObserve.Init(mobileKey, ...)`:
  //
  // const mobileKey = String.fromEnvironment('LAUNCHDARKLY_MOBILE_KEY');
  // unawaited(LDObserve.initStandalone(
  //   mobileKey,
  //   observability: observability,
  //   replay: replay,
  // ));
}
