# LaunchDarkly Observability SDK for Flutter — Example App

This example demonstrates the LaunchDarkly Observability SDK for Flutter: automatic and manual instrumentation for your application — including spans, logs, error reporting, feature flag correlation, and session replay.

A complete, runnable app lives in this directory. The home screen lets you trigger errors, logs, spans, HTTP requests, identify calls, flag evaluations, and a session replay masking demo so you can see how each one is reported.

## Early Access Preview

**NB: APIs are subject to change until a 1.x version is released.**

## Features

### Automatic Instrumentation

The Flutter observability SDK automatically instruments:

- **HTTP Requests**: Outgoing HTTP requests (when `InstrumentationOptions.networkRequests` is enabled).
- **Crash / Error Reporting**: Uncaught errors captured through `runZonedGuarded` and `FlutterError.onError`.
- **Feature Flag Evaluations**: Evaluation events are added to your spans via the bundled hook.
- **App Lifecycle / Launch Times**: Session and launch-time tracking.
- **`debugPrint` / `print` Capture**: Console output forwarded as logs via the print-intercepting zone.

### Two Layers

The Flutter SDK is split across two packages with complementary responsibilities:

- `launchdarkly_flutter_session_replay` exposes **`LDObserve`**, which boots the **native** observability + session replay stack. This mirrors `LDObserve.Init(...)` in the MAUI SDK.
- `launchdarkly_flutter_observability` exposes **`Observe`** and the **`ObservabilityPlugin`**, which provide the **Dart-side** OpenTelemetry surface (spans, logs, errors) and feature-flag-evaluation hooks.

## Prerequisites

- Flutter SDK matching the constraint in `pubspec.yaml`. Verify with:
  ```bash
  flutter --version
  flutter doctor
  ```
- **iOS**: Xcode with iOS platform support, CocoaPods (`brew install cocoapods`) or Swift Package Manager, and a booted Simulator or signed physical device.
- **Android**: Android Studio with an SDK and an emulator/AVD, or a USB device with USB debugging enabled.
- Cursor (or VS Code) with the official **Dart** and **Flutter** extensions.

## Usage

### Basic Setup

Initialize observability with `LDObserve`. There are two variants depending on whether you use a LaunchDarkly client. Both take an `ObservabilityOptions` and an optional `SessionReplayOptions`.

#### With a LaunchDarkly client

Pass your constructed `LDClient` to `LDObserve.init`. This registers the observability plugin on the client so feature flag evaluations are correlated with your telemetry. The Dart-side `ObservabilityPlugin` (registered through `LDConfig.plugins`) wires up OpenTelemetry and the evaluation hooks:

```dart
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_session_replay/launchdarkly_flutter_session_replay.dart';

final client = LDClient(
  LDConfig(
    CredentialSource.fromEnvironment(),
    AutoEnvAttributes.enabled,
    plugins: [
      ObservabilityPlugin(
        applicationName: 'flutter-sample-app',
        applicationVersion: const String.fromEnvironment('GIT_SHA',
            defaultValue: 'no-version'),
        instrumentation: InstrumentationConfig(
          debugPrint: DebugPrintSetting.always(),
        ),
      ),
    ],
  ),
  LDContextBuilder().kind('user', 'bob').build(),
);

client.start();

// Boots the native observability + session replay stack and registers the
// native plugin on the client.
LDObserve.init(
  client,
  observability: const ObservabilityOptions(
    serviceName: 'flutter-sample-app',
    instrumentation: InstrumentationOptions(
      networkRequests: true,
      launchTimes: true,
    ),
  ),
  replay: const SessionReplayOptions(isEnabled: true),
);
```

#### Standalone (without a LaunchDarkly client)

If you are not using the LaunchDarkly client, pass your mobile key directly. This boots the native stack without registering a plugin:

```dart
import 'package:launchdarkly_flutter_session_replay/launchdarkly_flutter_session_replay.dart';

const mobileKey = String.fromEnvironment('LAUNCHDARKLY_MOBILE_KEY');

await LDObserve.initStandalone(
  mobileKey,
  observability: const ObservabilityOptions(
    serviceName: 'flutter-sample-app',
  ),
  replay: const SessionReplayOptions(
    isEnabled: true,
    privacy: PrivacyOptions(
      maskTextInputs: true,
      maskWebViews: false,
      maskLabels: false,
    ),
  ),
);
```

> When `replay` is omitted, session replay is not started.

### Recording Observability Data

Use `Observe` (from `launchdarkly_flutter_observability`) to record spans, logs, and errors from your Dart code.

#### Logs

Use `Observe.recordLog` to emit a structured log record with a severity level and optional typed attributes. An optional `stackTrace` can be attached:

```dart
Observe.recordLog(
  'Checkout completed',
  severity: 'info',
  attributes: <String, Attribute>{
    'order_id': StringAttribute('ORD-9876'),
    'total': DoubleAttribute(42.99),
  },
);

Observe.recordLog(
  'This is an error log!',
  severity: 'error',
  stackTrace: StackTrace.current,
);
```

`severity` is a string; common levels are `trace`, `debug`, `info`, `warn`, `error`, and `fatal`. It defaults to `info`.

A log recorded while a span is active is automatically associated with that span through the OpenTelemetry context:

```dart
final span = Observe.startSpan('checkout-flow');
Observe.recordLog(
  'Processing on the same trace',
  severity: 'warn',
  attributes: <String, Attribute>{'source': StringAttribute('checkout')},
);
span.end();
```

#### Errors

Use `Observe.recordException` to capture an error. In Dart the stack trace is independent of the exception, so capture both together:

```dart
try {
  // something that throws
} catch (e, stack) {
  Observe.recordException(e, stackTrace: stack);
}
```

The example app also installs global handlers so uncaught errors are reported automatically:

```dart
runZonedGuarded(
  () {
    WidgetsFlutterBinding.ensureInitialized();

    FlutterError.onError = (details) {
      Observe.recordException(details.exception, stackTrace: details.stack);
    };

    runApp(const SessionReplayCapture(child: MyApp()));
  },
  (err, stack) => Observe.recordException(err, stackTrace: stack),
  zoneSpecification: Observe.zoneSpecification(),
);
```

#### Traces

Use `Observe.startSpan` to create spans for tracing operations. Spans are backed by [OpenTelemetry](https://opentelemetry.io/) and must be ended when the operation completes.

```dart
final span = Observe.startSpan('api_request');
span.setAttribute('endpoint', StringAttribute('/api/users'));
span.setAttribute('method', StringAttribute('GET'));
span.addEvent('cache.miss');
span.setStatus(SpanStatusCode.ok);
span.end();
```

##### Nested Spans

`startSpan` automatically creates parent-child relationships — each new span becomes a child of the currently active span:

```dart
final parent = Observe.startSpan('ProcessOrder');
final child = Observe.startSpan('ValidatePayment');
final grandchild = Observe.startSpan('ChargeCard');

await httpClient.post(Uri.parse('https://api.example.com/charge'));

grandchild.end();
child.end();
parent.end();
```

##### Sequential Spans

End each span before starting the next so they are recorded independently rather than nested:

```dart
final span1 = Observe.startSpan('SequentialOperation1');
span1.setAttribute('sequence', StringAttribute('1'));
span1.end();

final span2 = Observe.startSpan('SequentialOperation2');
span2.setAttribute('sequence', StringAttribute('2'));
span2.end();
```

| Method | Description |
|---|---|
| `Observe.startSpan(name, {kind, attributes})` | Start a span that nests under the current active span. Returns a `Span`. |
| `Observe.recordLog(message, {severity, stackTrace, attributes})` | Record a structured log. |
| `Observe.recordException(exception, {stackTrace, attributes})` | Record an error/exception. |
| `Observe.shutdown()` | Shut down observability. It cannot be restarted afterward. |
| `Observe.zoneSpecification()` | A zone spec that forwards `print`/`debugPrint` output as logs. |
| `span.setAttribute(name, attribute)` | Set a single typed attribute on a span. |
| `span.setAttributes(map)` | Set multiple attributes on a span. |
| `span.addEvent(name, {attributes})` | Record a named event on a span. |
| `span.setStatus(SpanStatusCode)` | Set the span status (`ok`, `error`, `unset`). |
| `span.recordException(exception, {stackTrace, attributes})` | Record an exception on the span. |
| `span.end()` | End the span. |

`SpanKind` supports `internal` (default), `client`, `server`, `producer`, and `consumer`.

#### Attributes

Attribute values are strongly typed. Use the type-safe constructors, or `Attribute.fromDynamic` when the value's type is not known ahead of time:

```dart
span.setAttribute('count', IntAttribute(42));
span.setAttribute('name', StringAttribute('flutter'));
span.setAttribute('ratio', DoubleAttribute(3.14));
span.setAttribute('enabled', BooleanAttribute(true));
span.setAttribute('samples', DoubleListAttribute([3.14, 6.28]));
span.setAttribute('dynamic', Attribute.fromDynamic(someValue));
```

Available types: `StringAttribute`, `IntAttribute`, `DoubleAttribute`, `BooleanAttribute`, and their list variants (`StringListAttribute`, `IntListAttribute`, `DoubleListAttribute`, `BooleanListAttribute`).

### Identifying Users

Use the LaunchDarkly client to identify or switch user contexts. This ties observability data to the correct user:

```dart
// Single context
final userContext = LDContextBuilder()
    .kind('user', 'user-key')
    .name('Bob Smith')
    .build();
await client.identify(userContext);

// Multi-context
final multiContext = LDContextBuilder()
    .kind('user', 'user-key')
    .name('Bob Smith')
    .kind('device', 'iphone')
    .name('iphone')
    .build();
await client.identify(multiContext);

// Anonymous context
final anonContext = LDContextBuilder()
    .kind('user', 'anonymous-key')
    .anonymous(true)
    .build();
await client.identify(anonContext);
```

## Session Replay

Session Replay captures screen recordings to help you understand how users interact with your application. Enable it by passing `SessionReplayOptions` to `LDObserve.init` / `LDObserve.initStandalone`, and wrap the part of your app you want recorded in a `SessionReplayCapture` widget:

```dart
runApp(const SessionReplayCapture(child: MyApp()));
```

`SessionReplayCapture` provides Flutter-rendered screenshots to the native session replay SDK over a method channel; without it, the native SDK has no Flutter frames to record.

### Privacy Options

Control what is captured during a session with `PrivacyOptions`:

- `maskTextInputs`: (Default: `true`) Masks all text input fields.
- `maskWebViews`: (Default: `false`) Masks all web view content.
- `maskLabels`: (Default: `false`) Masks all text labels.
- `maskImages`: (Default: `false`) Masks all images.
- `minimumAlpha`: (Default: `0.02`) Minimum opacity threshold for capture.

```dart
const SessionReplayOptions(
  isEnabled: true,
  privacy: PrivacyOptions(
    maskTextInputs: true,
    maskWebViews: false,
    maskLabels: false,
    maskImages: false,
  ),
);
```

### Masking demo

The **Credit Card** screen (reachable from the home screen) is a form of sensitive fields. With `maskTextInputs: true`, every text field on the page is masked automatically in the recorded session.

> A per-widget masking API (the equivalent of MAUI's `view.LDMask()` / `view.LDUnmask()`) is not yet available in the Flutter SDK; today, masking is configured screen-wide through `PrivacyOptions`.

## Running the Example

### 1. Get your LaunchDarkly credentials

In the [LaunchDarkly dashboard](https://app.launchdarkly.com/), open your project/environment settings and copy either:

- **Mobile key** — for iOS, Android, macOS, Windows, Linux.
- **Client-side ID** — for Chrome/Web builds.

You only need the one(s) for the platforms you intend to run.

### 2. Configure `dart_defines.json`

The example calls `CredentialSource.fromEnvironment()`, which reads compile-time constants injected via `--dart-define`. To keep secrets out of source control, load them from a JSON file:

```bash
cp dart_defines.example.json dart_defines.json
```

Then set your real key. For iOS / Android / macOS / Windows / Linux:

```json
{
  "LAUNCHDARKLY_MOBILE_KEY": "mob-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "GIT_SHA": "local-dev"
}
```

For Chrome / Web, use the client-side ID instead:

```json
{
  "LAUNCHDARKLY_CLIENT_SIDE_ID": "xxxxxxxxxxxxxxxxxxxxxxxx",
  "GIT_SHA": "local-dev"
}
```

> **Important**: set **only one** of `LAUNCHDARKLY_MOBILE_KEY` or `LAUNCHDARKLY_CLIENT_SIDE_ID`. The SDK throws if both are present as non-empty strings — even placeholder values like `"xxxx..."` count. Delete (don't just placeholder) the one you aren't using.

Notes:

- `GIT_SHA` is passed to `ObservabilityPlugin.applicationVersion`; any string works.
- `dart_defines.json` is **gitignored** — never commit it. The committed `dart_defines.example.json` is the template.
- `--dart-define-from-file` values are **compile-time constants**. After editing the file you must fully stop and re-launch — hot reload/restart will not pick up new values.

### 3. Install dependencies

```bash
flutter pub get
```

For iOS with CocoaPods:

```bash
cd ios && pod install && cd ..
```

### 4. Run

From the Cursor/VS Code Run & Debug panel, the bundled `.vscode/launch.json` wires `--dart-define-from-file=dart_defines.json` into the debugger — pick a device and a `example` config and press **F5**.

Or from the command line:

```bash
# List devices to find the exact name/UDID
flutter devices

# iOS Simulator
flutter run -d "iPhone 16e" --dart-define-from-file=dart_defines.json

# Android emulator
flutter run -d emulator-5554 --dart-define-from-file=dart_defines.json
```

### Use local native SDK checkouts

By default, the example uses the published native dependencies declared by `packages/observability`. To test local native changes, set `LD_USE_LOCAL_NATIVE` (the `*, local native` launch configs do this for you):

```bash
LD_USE_LOCAL_NATIVE=true flutter run -d <device-id> --dart-define-from-file=dart_defines.json
```

For iOS CocoaPods, reinstall pods after toggling the switch:

```bash
cd ios && LD_USE_LOCAL_NATIVE=true pod install && cd ..
```

### Verify it's working

In the running app:

- **Trigger Error** / **Trigger Crash** → reported through `Observe.recordException`.
- **Trigger Log** / **Log with Context** / **Record error log with stack trace** → flow through `Observe.recordLog`.
- **Nested Spans** / **Trigger Sequential Spans** / **Send custom span** → spans recorded via `Observe.startSpan`.
- **Trigger HTTP Request** → an outgoing request that is traced when network instrumentation is enabled.
- **Identify** (User / Multi / Anon) → switches the active LaunchDarkly context.
- **Credit Card** → a masked form for the session replay masking demo.

Then check the **Observability** section of your LaunchDarkly project for traces, logs, and errors tagged with your `serviceName` / `applicationName`.

## Troubleshooting

- **`No supported devices found ...`** — run `flutter devices` for the exact name/UDID. Don't use `-d ios`; use the device name/UDID or `-d apple_ios`.
- **`pod: command not found`** — `brew install cocoapods`, then `cd ios && pod install`.
- **`The mobile key was not specified ...`** — `--dart-define-from-file=dart_defines.json` didn't reach the build. Confirm the file exists here, you used a launch config (not a default Dart/Flutter config), and you fully stopped & re-launched after editing it.
- **`... include either a client-side ID, or a mobile key, but not both`** — both keys are set as non-empty strings in `dart_defines.json`. Delete the one you aren't targeting (placeholders still count as set).

## Resources

- [Flutter installation](https://docs.flutter.dev/get-started/install)
- [LaunchDarkly Flutter SDK docs](https://docs.launchdarkly.com/sdk/client-side/flutter)
- [LaunchDarkly observability docs](https://docs.launchdarkly.com/home/observability)
- [Dart `--dart-define-from-file` reference](https://dart.dev/tools/dart-define-from-file)
