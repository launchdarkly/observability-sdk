import 'dart:async';

import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

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

      // Report any errors handled by flutter.
      FlutterError.onError = (FlutterErrorDetails details) {
        Observe.recordException(details.exception, stackTrace: details.stack);
      };

      runApp(MyApp());
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

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // TRY THIS: Try running your application with "flutter run". You'll see
        // the application has a purple toolbar. Then, without quitting the app,
        // try changing the seedColor in the colorScheme below to Colors.green
        // and then invoke "hot reload" (save your changes or press the "hot
        // reload" button in a Flutter-supported IDE, or press "r" if you used
        // the command line to start the app).
        //
        // Notice that the counter didn't reset back to zero; the application
        // state is not lost during the reload. To reset the state, use hot
        // restart instead.
        //
        // This works for code too, not just values: Most code changes can be
        // tested with just a hot reload.
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      routes: {
        // When navigating to the "/" route, build the FirstScreen widget.
        '/': (context) => const MyHomePage(title: 'Flutter Demo Home Page'),
        // When navigating to the "/second" route, build the SecondScreen widget.
        '/second': (context) => const SecondScreen(),
      },
      navigatorObservers: [Observe.navigatorObserver()],
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            ElevatedButton(
              onPressed: () {
                throw Exception('This is an error!');
              },
              child: const Text('Trigger unhandled error'),
            ),
            ElevatedButton(
              onPressed: () {
                Observe.recordLog(
                  'This is a warning log!',
                  severity: 'warn',
                  attributes: <String, Attribute>{
                    'attribute-in-log': StringAttribute('value-in-log'),
                  },
                );
              },
              child: const Text('Record warning log'),
            ),
            ElevatedButton(
              onPressed: () {
                Observe.recordLog(
                  'This is an error log!',
                  severity: 'error',
                  stackTrace: StackTrace.current,
                  attributes: <String, Attribute>{
                    'attribute-in-log': StringAttribute('value-in-log'),
                  },
                );
              },
              child: const Text('Record error log with stack trace'),
            ),
            ElevatedButton(
              onPressed: () {
                debugPrint("This is a message from debug print");
              },
              child: const Text('Call debugPrint'),
            ),
            ElevatedButton(
              onPressed: () {
                // ignore: avoid_print
                print('This is a message from print');
              },
              child: const Text('Call print'),
            ),
            ElevatedButton(
              onPressed: () {
                final span = Observe.startSpan(
                  'manually-recorded-span',
                  attributes: <String, Attribute>{
                    "custom": StringAttribute("value"),
                  },
                );
                span.end();
              },
              child: const Text('Record a span'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.pushNamed(context, '/second');
              },
              child: const Text('Navigate to second screen'),
            ),
          ],
        ),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }
}

class SecondScreen extends StatelessWidget {
  const SecondScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Second Screen')),
      body: Center(
        child: ElevatedButton(
          onPressed: () {
            Navigator.pop(context);
          },
          child: const Text('Go back!'),
        ),
      ),
    );
  }
}
