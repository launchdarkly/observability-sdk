// A minimal app wired up with LaunchDarkly Observability and Session Replay.
//
// A complete, runnable sample app lives in the repository at
// https://github.com/launchdarkly/observability-sdk/tree/main/sdk/flutter/example
//
// Run with:
//   flutter run --dart-define=LAUNCHDARKLY_MOBILE_KEY=<your-mobile-key>

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

void main() {
  runZonedGuarded(
    () {
      WidgetsFlutterBinding.ensureInitialized();

      FlutterError.onError = (details) {
        LDObserve.recordException(details.exception, stackTrace: details.stack);
      };

      final client = LDClient(
        LDConfig(
          CredentialSource.fromEnvironment(),
          AutoEnvAttributes.enabled,
        ),
        LDContextBuilder().kind('user', 'example-user').build(),
      );
      client.start();

      LDObserve.init(
        client,
        observability: const ObservabilityOptions(
          serviceName: 'flutter-example-app',
        ),
        replay: const SessionReplayOptions(
          isEnabled: true,
          privacy: PrivacyOptions(maskTextInputs: true),
        ),
      );

      runApp(const SessionReplayCapture(child: ExampleApp()));
    },
    (err, stack) => LDObserve.recordException(err, stackTrace: stack),
    zoneSpecification: LDObserve.zoneSpecification(),
  );
}

/// The root widget of the example app.
class ExampleApp extends StatelessWidget {
  /// Creates the example app.
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LaunchDarkly Observability Example',
      navigatorObservers: [LDNavigatorObserver()],
      home: const HomePage(),
    );
  }
}

/// A page with buttons that record observability data.
class HomePage extends StatelessWidget {
  /// Creates the home page.
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Observability Example')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ElevatedButton(
              onPressed: () => LDObserve.recordLog(
                'Button pressed',
                severity: LogSeverity.info,
                properties: <String, Object?>{'button': 'log'},
              ),
              child: const Text('Record log'),
            ),
            ElevatedButton(
              onPressed: () => LDObserve.withSpan('checkout', (span) {
                span.setAttribute('order_id', 'ORD-1234');
              }),
              child: const Text('Record span'),
            ),
            ElevatedButton(
              onPressed: () => LDObserve.recordException(
                StateError('Example error'),
                stackTrace: StackTrace.current,
              ),
              child: const Text('Record error'),
            ),
            const TextField(
              decoration: InputDecoration(labelText: 'Masked text input'),
            ),
          ],
        ),
      ),
    );
  }
}
