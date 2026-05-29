import 'dart:async';

import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_session_replay/launchdarkly_flutter_session_replay.dart';

import 'my_app.dart';

class LDSingleton {
  static LDClient? client;
}

void main() {
  runZonedGuarded(
    () {
      WidgetsFlutterBinding.ensureInitialized();

      LDSingleton.client = LDClient(
        LDConfig(
          // The credentials come from the environment, you can set them
          // using --dart-define.
          // Examples:
          // flutter run --dart-define LAUNCHDARKLY_CLIENT_SIDE_ID=<my-client-side-id> -d Chrome
          // flutter run --dart-define LAUNCHDARKLY_MOBILE_KEY=<my-mobile-key> -d ios
          //
          // Alternatively `CredentialSource.fromEnvironment()` can be replaced with your mobile key.

          // If using android studio the `additional run args` option can include the correct --dart-define.
          CredentialSource.fromEnvironment(),
          AutoEnvAttributes.enabled,
          plugins: [
            ObservabilityPlugin(
              instrumentation: InstrumentationConfig(
                debugPrint: DebugPrintSetting.always(),
              ),
              applicationName: 'test-application',
              // This could be a semantic version or a git commit hash.
              // This demonstrates how to use an environment variable to set the hash.
              // flutter build --dart-define GIT_SHA=$(git rev-parse HEAD) --dart-define LAUNCHDARKLY_MOBILE_KEY=<my-mobile-key>
              applicationVersion: const String.fromEnvironment(
                'GIT_SHA',
                defaultValue: 'no-version',
              ),
            ),
          ],
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
        Observe.recordException(details.exception, stackTrace: details.stack);
      };

      runApp(const SessionReplayCapture(child: MyApp()));
    },
    (err, stack) {
      // Report any errors reported from the guarded zone.
      Observe.recordException(err, stackTrace: stack);

      // Any additional default error handling.
    },
    // Used to intercept print statements. Generally print statements in
    // production are treated as a warning and this is not required.
    zoneSpecification: Observe.zoneSpecification(),
  );
}

/// Boots LaunchDarkly observability + session replay.
///
/// Mirrors the two `LDObserve.Init(...)` overloads from the MAUI sample
/// (`sdk/@launchdarkly/mobile-dotnet/sample/MauiProgram.cs`). Only the
/// LaunchDarkly client backed variant is active; the standalone variant is
/// shown below for reference.
void _startObservability() {
  const observability = ObservabilityOptions(
    serviceName: 'flutter-sample-app',
    instrumentation: InstrumentationOptions(
      networkRequests: true,
      launchTimes: true,
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
