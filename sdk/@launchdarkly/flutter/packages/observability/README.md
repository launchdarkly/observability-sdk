# LaunchDarkly Observability SDK for Flutter

`launchdarkly_flutter_observability` provides LaunchDarkly observability and session replay for Flutter through a single public facade, `LDObserve`: automatic and manual instrumentation for your application — including spans, logs, error reporting, feature flag correlation, and session replay.

Observability (spans, logs, errors) works on **mobile and web**. Session replay is available on mobile; web session replay is not yet available.

## Early Access Preview

**NB: APIs are subject to change until a 1.x version is released.**

## Supported platforms

| Platform | Observability | Session replay |
|---|---|---|
| iOS | ✅ | ✅ (native screenshot capture) |
| Android | ✅ | ✅ (native screenshot capture) |
| Web | ✅ | 🚧 Not yet available |

## Install

Add the package to your app's `pubspec.yaml`:

```bash
flutter pub add launchdarkly_flutter_observability
```

Then fetch dependencies:

```bash
flutter pub get
```

On iOS, install the native pods (from your app's `ios/` directory):

```bash
cd ios && pod install
```

No extra native setup is required for Android — Gradle resolves the plugin automatically.

## Getting started

Initialize observability with `LDObserve`. There are two variants depending on whether you use a LaunchDarkly client. Both take an `ObservabilityOptions` and an optional `SessionReplayOptions`.

### With a LaunchDarkly client

Pass your constructed `LDClient` to `LDObserve.init`. This registers the observability plugin on the client so feature flag evaluations are correlated with your telemetry, wires up the Dart OpenTelemetry pipeline, and boots the platform session replay:

```dart
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

final client = LDClient(
  LDConfig(
    CredentialSource.fromEnvironment(),
    AutoEnvAttributes.enabled,
  ),
  LDContextBuilder().kind('user', 'bob').build(),
);

client.start();

// Wires up observability + session replay and registers the plugin on the
// client. All instrumentation settings live on ObservabilityOptions.
LDObserve.init(
  client,
  observability: ObservabilityOptions(
    serviceName: 'flutter-sample-app',
    serviceVersion: const String.fromEnvironment('GIT_SHA',
        defaultValue: 'no-version'),
    instrumentation: InstrumentationOptions(
      networkRequests: true,
      launchTimes: true,
      debugPrint: DebugPrintSetting.always(),
    ),
  ),
  replay: const SessionReplayOptions(isEnabled: true),
);
```

### Standalone (without a LaunchDarkly client)

If you are not using the LaunchDarkly client, pass your mobile key directly. This boots observability and session replay without registering a plugin:

```dart
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

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

## Automatic instrumentation

When enabled through `InstrumentationOptions`, the SDK automatically instruments:

- **HTTP Requests**: Outgoing HTTP requests (when `InstrumentationOptions.networkRequests` is enabled).
- **Crash / Error Reporting**: Uncaught errors captured through `runZonedGuarded` and `FlutterError.onError`.
- **Feature Flag Evaluations**: Evaluation events are added to your spans via the bundled hook.
- **App Lifecycle / Launch Times**: Session and launch-time tracking.
- **`debugPrint` / `print` Capture**: Console output forwarded as logs via the print-intercepting zone.

To forward uncaught errors and `print`/`debugPrint` output automatically, run your app inside a guarded zone:

```dart
runZonedGuarded(
  () {
    WidgetsFlutterBinding.ensureInitialized();

    FlutterError.onError = (details) {
      LDObserve.recordException(details.exception, stackTrace: details.stack);
    };

    runApp(const SessionReplayCapture(child: MyApp()));
  },
  (err, stack) => LDObserve.recordException(err, stackTrace: stack),
  zoneSpecification: LDObserve.zoneSpecification(),
);
```

## Native-only options

The following options are forwarded to the native Android and iOS SDKs. They are **no-ops on web**, where the Dart OpenTelemetry pipeline is used instead.

On `ObservabilityOptions`:

- `customHeaders` (`Map<String, String>`): extra HTTP headers added to OTLP exports (e.g. for proxies or auth). Defaults to `{}`.
- `sessionBackgroundTimeout` (`Duration`): how long the app may stay backgrounded before the session ends. Defaults to 15 minutes.
- `logsApiLevel` (`ObservabilityLogLevel`): minimum severity of logs forwarded to the logs pipeline. Use `ObservabilityLogLevel.none` to disable logs. Defaults to `ObservabilityLogLevel.info`.
- `traces` (`TracesOptions`): toggles `includeErrors` and `includeSpans` for automatic trace generation. Both default to `true`.
- `metricsEnabled` (`bool`): whether metrics are exported. Defaults to `true`.
- `analytics` (`AnalyticsOptions`): analytics telemetry. Use the `AnalyticsOptions.enabled` / `AnalyticsOptions.disabled` shorthands to toggle everything at once (mirroring Swift's `analytics: .enabled`), or set the individual flags:
  - `taps` (`bool`): emit a span for each user tap. Tap detection is always enabled; this flag only controls whether a span is published. Supported on Android and iOS. Defaults to `true`.
  - `views` (`bool`): emit spans for screen/page views. **Android-only** (no-op on iOS/web). Defaults to `true`.
  - `trackEvents` (`bool`): emit a span when a custom event is tracked. **Android-only** (no-op on iOS/web). Defaults to `true`.
  - `appLifecycle` (`bool`): emit `app_foreground` / `app_background` spans as the app moves between foreground and background. **Mobile-only** (Android, iOS; no-op on web). Defaults to `true`.
  - `appLaunch` (`bool`): emit an `app_launch` span (carrying the launch type — `install` / `update` / `relaunch` — and version fields) once per process launch. **Mobile-only** (Android, iOS; no-op on web). Defaults to `true`.
- `instrumentation.crashReporting` (`bool`): report uncaught exceptions as errors. Defaults to `true`.

On `SessionReplayOptions`:

- `sampleRate` (`double`): probability from `0.0` to `1.0` that session replay starts when `isEnabled` is true. Values `<= 0` never start; values `>= 1` always start. Defaults to `1.0`.
- `frameRate` (`double`): target capture rate in frames per second. Defaults to `1.0`.
- `scale` (`double?`): replay capture resolution multiplier — `1.0` = 1x (160 DPI), `2.0` = 2x, etc. Higher values capture more detail but produce larger frames. `null` is treated as `1.0`. Defaults to `1.0`.

```dart
LDObserve.init(
  client,
  observability: const ObservabilityOptions(
    customHeaders: {'x-proxy-token': 'secret'},
    sessionBackgroundTimeout: Duration(minutes: 5),
    logsApiLevel: ObservabilityLogLevel.warn,
    traces: TracesOptions(includeErrors: true, includeSpans: true),
    metricsEnabled: true,
    // Shorthand for all analytics enabled; use AnalyticsOptions.disabled to
    // turn it all off, or AnalyticsOptions(taps: …, views: …, trackEvents: …,
    // appLifecycle: …, appLaunch: …) for fine-grained control.
    analytics: AnalyticsOptions.enabled,
    instrumentation: InstrumentationOptions(
      crashReporting: true,
    ),
  ),
  replay: const SessionReplayOptions(isEnabled: true, frameRate: 2.0),
);
```

## Recording observability data

Use `LDObserve` to record spans, logs, and errors from your Dart code.

### Logs

Use `LDObserve.recordLog` to emit a structured log record with a severity level and optional `properties`. `properties` is a plain Dart map (`Map<String, Object?>`) — no LaunchDarkly or OpenTelemetry types are needed. An optional `stackTrace` can be attached:

```dart
LDObserve.recordLog(
  'Checkout completed',
  severity: 'info',
  properties: <String, Object?>{
    'order_id': 'ORD-9876',
    'total': 42.99,
  },
);

LDObserve.recordLog(
  'This is an error log!',
  severity: 'error',
  stackTrace: StackTrace.current,
);
```

`severity` is a string; common levels are `trace`, `debug`, `info`, `warn`, `error`, and `fatal`. It defaults to `info`.

A log recorded while a span is active is automatically associated with that span through the OpenTelemetry context:

```dart
final span = LDObserve.startSpan('checkout-flow');
LDObserve.recordLog(
  'Processing on the same trace',
  severity: 'warn',
  properties: <String, Object?>{'source': 'checkout'},
);
span.end();
```

### Errors

Use `LDObserve.recordException` to capture an error. In Dart the stack trace is independent of the exception, so capture both together:

```dart
try {
  // something that throws
} catch (e, stack) {
  LDObserve.recordException(e, stackTrace: stack);
}
```

### Traces

Use `LDObserve.startSpan` to create spans for tracing operations. Spans are backed by [OpenTelemetry](https://opentelemetry.io/) and must be ended when the operation completes.

```dart
final span = LDObserve.startSpan('api_request');
span.setAttribute('endpoint', '/api/users');
span.setAttribute('method', 'GET');
span.addEvent('cache.miss');
span.setStatus(SpanStatusCode.ok);
span.end();
```

#### Nested spans

`startSpan` automatically creates parent-child relationships — each new span becomes a child of the currently active span:

```dart
final parent = LDObserve.startSpan('ProcessOrder');
final child = LDObserve.startSpan('ValidatePayment');
final grandchild = LDObserve.startSpan('ChargeCard');

await httpClient.post(Uri.parse('https://api.example.com/charge'));

grandchild.end();
child.end();
parent.end();
```

#### Sequential spans

End each span before starting the next so they are recorded independently rather than nested:

```dart
final span1 = LDObserve.startSpan('SequentialOperation1');
span1.setAttribute('sequence', '1');
span1.end();

final span2 = LDObserve.startSpan('SequentialOperation2');
span2.setAttribute('sequence', '2');
span2.end();
```

### Screen views (navigation)

Flutter renders into a single native `Activity`/`UIViewController`, so the native SDK's automatic screen detection never sees your Flutter route changes. Report them from Dart with `LDObserve.trackScreenView`, which emits a `screen_view` span and a Session Replay `Navigate` timeline event:

```dart
LDObserve.trackScreenView(
  'Checkout',
  screenClass: 'CheckoutPage',
  category: 'commerce',
  properties: {'cart_size': 3},
);
```

To record navigations automatically, attach the provided `LDNavigatorObserver` to your app's navigator. By default it uses each route's `settings.name` and skips unnamed routes:

```dart
MaterialApp(
  navigatorObservers: [LDNavigatorObserver()],
  // ...
);
```

Name your routes (for example via `MaterialPageRoute(settings: RouteSettings(name: 'Home'), ...)` or named routes) so they are reported. To customize how a name is derived — or to name otherwise-anonymous routes — pass a `screenNameExtractor`:

```dart
MaterialApp(
  navigatorObservers: [
    LDNavigatorObserver(
      screenNameExtractor: (route) =>
          route.settings.name ?? route.settings.arguments?.toString(),
      category: 'navigation',
    ),
  ],
);
```

### API reference

| Method | Description |
|---|---|
| `LDObserve.startSpan(name, {kind, properties})` | Start a span that nests under the current active span. Returns a `Span`. |
| `LDObserve.recordLog(message, {severity, stackTrace, properties})` | Record a structured log. |
| `LDObserve.recordException(exception, {stackTrace, properties})` | Record an error/exception. |
| `LDObserve.track(eventName, {properties, metricValue})` | Record a custom `track` event as a `track` span. |
| `LDObserve.trackScreenView(name, {screenClass, screenId, category, properties})` | Record a screen view (navigation) as a `screen_view` span and a Session Replay `Navigate` event. |
| `LDObserve.shutdown()` | Shut down observability. It cannot be restarted afterward. |
| `LDObserve.zoneSpecification()` | A zone spec that forwards `print`/`debugPrint` output as logs. |
| `span.setAttribute(name, value)` | Set a single attribute on a span. |
| `span.setAttributes(map)` | Set multiple attributes on a span. |
| `span.addEvent(name, {attributes})` | Record a named event on a span. |
| `span.setStatus(SpanStatusCode)` | Set the span status (`ok`, `error`, `unset`). |
| `span.recordException(exception, {stackTrace, attributes})` | Record an exception on the span. |
| `span.end()` | End the span. |

`SpanKind` supports `internal` (default), `client`, `server`, `producer`, and `consumer`.

### Attributes

Attributes are supplied as plain Dart values — no LaunchDarkly or OpenTelemetry types are involved. A value may be a `String`, `int`, `double`, `bool`, or a homogeneous list of any of those. Values that cannot be represented as an attribute (such as nested maps or mixed-type lists) are ignored.

```dart
span.setAttribute('count', 42);
span.setAttribute('name', 'flutter');
span.setAttribute('ratio', 3.14);
span.setAttribute('enabled', true);
span.setAttribute('samples', <double>[3.14, 6.28]);

span.setAttributes(<String, Object?>{
  'order_id': 'ORD-9876',
  'total': 42.99,
});
```

Methods that accept `properties` (`recordLog`, `recordException`, `startSpan`, `track`) take a `Map<String, Object?>` of these same plain values.

## Session replay

Session Replay captures screen recordings to help you understand how users interact with your application. Enable it by passing `SessionReplayOptions` to `LDObserve.init` / `LDObserve.initStandalone`, and wrap the part of your app you want recorded in a `SessionReplayCapture` widget:

```dart
runApp(const SessionReplayCapture(child: MyApp()));
```

On mobile, the part of your app wrapped in `SessionReplayCapture` is what gets recorded; without it, those frames are not captured. On web, session replay is not yet available, so `SessionReplayCapture` is a safe no-op pass-through — wrapping your app with it is safe on every platform.

### Privacy options

Control what is captured during a session with `PrivacyOptions`:

- `maskTextInputs`: (Default: `true`) Masks all text input fields.
- `maskWebViews`: (Default: `false`) Masks all web view content.
- `maskLabels`: (Default: `false`) Masks all text labels.
- `maskImages`: (Default: `false`) Masks all images.
- `minimumAlpha`: (Default: `0.02`) Opacity threshold below which a widget is treated as invisible and skipped during capture and masking. Raise it to ignore nearly-transparent UI.

```dart
const SessionReplayOptions(
  isEnabled: true,
  privacy: PrivacyOptions(
    maskTextInputs: true,
    maskWebViews: false,
    maskLabels: false,
    maskImages: false,
    minimumAlpha: 0.02,
  ),
);
```

Masks are applied to every captured frame: they follow their widgets through scrolling, transforms, and animations, and frames where a mask cannot be placed reliably are dropped rather than risk exposing unmasked content.

### Per-widget masking

Beyond the screen-wide `PrivacyOptions`, you can redact individual widgets by wrapping them in `LDMask`. The wrapped subtree's on-screen bounds are painted over in every captured frame, and the mask follows the widget as it lays out and scrolls:

```dart
LDMask(
  child: Text(creditCardNumber),
)
```

Use `LDIgnore` to exclude a subtree from session replay entirely. In Flutter it behaves like `LDMask` (the region is painted over in every frame), so its content never appears in a recording:

```dart
LDIgnore(
  child: VideoPlayer(controller),
)
```

Use `LDUnmask` to exempt a subtree from **global** masking — the screen-wide `PrivacyOptions` rules such as `maskTextInputs`. For example, to reveal one non-sensitive field on a page where every input is masked:

```dart
// maskTextInputs masks every field; reveal just this one.
LDUnmask(
  child: TextField(controller: searchController),
)
```

Precedence: `LDUnmask` only overrides global masking — it does **not** override an explicit `LDMask` or `LDIgnore`. An `LDUnmask` nested inside one stays masked, because an explicit per-widget mask always wins.

`LDMask` / `LDIgnore` / `LDUnmask` are active on iOS and Android. On web they render their child unchanged for now, since web session replay is not yet available.

#### Masking by widget key or type

When wrapping widgets isn't convenient, name them once in `PrivacyOptions` by their `Key` or `runtimeType`. These rules are resolved entirely on the Flutter side and follow the same precedence as the wrapper widgets (a mask/ignore match wins over an unmask match):

```dart
PrivacyOptions(
  maskWidgetTypes: {CreditCardField},
  maskWidgetKeys: {const ValueKey('ssn-field')},
  unmaskWidgetTypes: {SearchBox},
  ignoreWidgetTypes: {LiveCameraPreview},
)
```

## Identifying users

Use the LaunchDarkly client to identify or switch user contexts. This ties observability data to the correct user:

```dart
final userContext = LDContextBuilder()
    .kind('user', 'user-key')
    .name('Bob Smith')
    .build();
await client.identify(userContext);
```

You do not need to call anything on `LDObserve`: the observability plugin hooks into the LaunchDarkly client and, on mobile, forwards each completed identify to the native observability SDK and Session Replay. This attributes subsequent `LDObserve.track` events to the active context and records who the user is on the active Session Replay recording.

## Example

A complete, runnable sample app lives in [`example/`](../../example). See its [README](../../example/README.md) for how to configure credentials and launch it on each platform.

LaunchDarkly overview
-------------------------
[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves trillions of feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/home/getting-started) using LaunchDarkly today!

[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](CONTRIBUTING.md) for instructions on how to contribute to this SDK.
