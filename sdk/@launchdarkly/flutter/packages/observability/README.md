# LaunchDarkly Observability SDK for Flutter

`launchdarkly_flutter_observability` provides LaunchDarkly observability and session replay for Flutter through a single public facade, `LDObserve`: automatic and manual instrumentation for your application — including spans, logs, error reporting, feature flag correlation, and session replay.

Observability (spans, logs, errors) works on **mobile and web**. Session replay is available on iOS and Android only; it is not supported on web.

## Early Access Preview

**NB: APIs are subject to change until a 1.x version is released.**

## Supported platforms

| Platform | Observability | Session replay |
|---|---|---|
| iOS | ✅ | ✅ (native screenshot capture) |
| Android | ✅ | ✅ (native screenshot capture) |
| Web | ✅ | ❌ Not supported |

## Install

Requires Flutter 3.27 or newer.

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

> `LDObserve.init` and `LDObserve.initStandalone` return a `Future<bool>` that completes with `true` once observability is ready. Awaiting it is optional, but anything recorded before it completes is dropped (except the most recent screen view, which is replayed once ready). It never completes with an error: it completes with `false` if startup fails (the error is logged, and calling `init` again retries) or after `LDObserve.shutdown()`. Only the first successful `init` takes effect; later calls are logged and ignored.

> `ObservabilityOptions(isEnabled: false)` turns off observability telemetry: no spans, logs, errors, flag-evaluation spans, lifecycle or `debugPrint` capture are recorded, and nothing is exported on web. Session replay is controlled separately by `SessionReplayOptions.isEnabled`; while it is on, screen views, clicks and track events still reach the replay timeline.

## Automatic instrumentation

When enabled through `InstrumentationOptions`, the SDK automatically instruments:

- **HTTP Requests**: Outgoing HTTP requests (when `InstrumentationOptions.networkRequests` is enabled).
- **Crash / Error Reporting**: Uncaught errors captured through `runZonedGuarded` and `FlutterError.onError`.
- **Feature Flag Evaluations**: Evaluation events are added to your spans via the bundled hook.
- **Clicks**: Every tap, resolved to the widget it landed on (requires `SessionReplayCapture`; see [Clicks (taps)](#clicks-taps)).
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

The following options are forwarded to the native Android and iOS SDKs. Unless noted otherwise they are **no-ops on web**, where the Dart OpenTelemetry pipeline is used instead.

On `ObservabilityOptions`:

- `customHeaders` (`Map<String, String>`): extra HTTP headers added to OTLP exports (e.g. for proxies or auth). Defaults to `{}`.
- `sessionBackgroundTimeout` (`Duration`): how long the app may stay backgrounded before the session ends. Defaults to 15 minutes.
- `logsApiLevel` (`ObservabilityLogLevel`): minimum severity of logs forwarded to the logs pipeline. Use `ObservabilityLogLevel.none` to disable logs. Defaults to `ObservabilityLogLevel.info`.
- `traces` (`TracesOptions`): toggles `includeErrors` and `includeSpans` for automatic trace generation. Both default to `true`.
- `metricsEnabled` (`bool`): whether metrics are exported. Defaults to `true`.
- `analytics` (`AnalyticsOptions`): analytics telemetry. Use the `AnalyticsOptions.enabled` / `AnalyticsOptions.disabled` shorthands to toggle everything at once (mirroring Swift's `analytics: .enabled`), or set the individual flags:
  - `taps` (`bool`): capture taps and emit a `click` event for each. Gates the Dart click detection described under [Clicks (taps)](#clicks-taps), so turning it off stops the widget resolution too, not just the span. Supported on Android, iOS and web. Defaults to `true`.
  - `customClickTargetResolver` (`LDClickTargetResolver?`): names your own widget types as click targets. Dart-side only. See [Clicks (taps)](#clicks-taps).
  - `views` (`bool`): emit spans for screen/page views. Supported on Android, iOS and web. This gates the `screen_view` span only; on mobile the Session Replay `Navigate` event is emitted either way. Defaults to `true`.
  - `trackEvents` (`bool`): emit a span when a custom event is tracked. Supported on Android, iOS and web. Defaults to `true`.
  - `appLifecycle` (`bool`): emit app-lifecycle spans. On every platform, including web, this gates the Dart `device.app.lifecycle` span emitted on each Flutter `AppLifecycleState` change; on Android and iOS it also gates the native `app_foreground` / `app_background` spans. Defaults to `true`.
  - `appLaunch` (`bool`): emit an `app_launch` span (carrying the launch type — `install` / `update` / `relaunch` — and version fields) once per process launch. **Mobile-only** (Android, iOS; no-op on web). Defaults to `true`.
- `instrumentation.crashReporting` (`bool`): report uncaught exceptions as errors. Defaults to `true`.

On `SessionReplayOptions`:

- `sampleRate` (`double`): probability from `0.0` to `1.0` that replay starts when enabled. Defaults to `1.0`.
- `frameRate` (`double`): target capture rate in frames per second. Defaults to `1.0`.
- `scale` (`double?`): replay capture resolution multiplier — `1.0` = 1x (160 DPI), `2.0` = 2x, etc. Higher values capture more detail but produce larger frames. `null` is treated as `1.0`. Defaults to `1.0`.
- `imageQuality` (`double`): JPEG encoding quality of exported frames, from `0.0` (lowest quality, smallest payload) to `1.0` (highest quality, largest payload). Values outside that range are clamped. Defaults to `0.3`.

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
  replay: const SessionReplayOptions(
    isEnabled: true,
    sampleRate: 0.25,
    frameRate: 2.0,
    imageQuality: 0.2,
  ),
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

Flutter renders into a single native `Activity`/`UIViewController`, so the native SDK's automatic screen detection never sees your Flutter route changes. Nothing is recorded until you report them from Dart.

Usually that means attaching `LDNavigatorObserver` to your app's navigator. Each route change then emits a `screen_view` span and a Session Replay `Navigate` timeline event:

```dart
MaterialApp(
  navigatorObservers: [LDNavigatorObserver()],
  // ...
);
```

**Routes must be named to be reported.** The observer reads `route.settings.name` and skips routes without one, so an app that pushes bare `MaterialPageRoute(builder: ...)` records nothing and gives no error. Name them at the push site:

```dart
Navigator.of(context).push(
  MaterialPageRoute<void>(
    settings: const RouteSettings(name: '/checkout'),
    builder: (_) => const CheckoutPage(),
  ),
);
```

Skipping unnamed routes is deliberate: it keeps the dialogs and bottom sheets that `showDialog` and `showModalBottomSheet` push without settings from showing up as screens. To name those anyway, or to derive names some other way, pass a `screenNameExtractor`. Returning `null` from it skips the route, so it doubles as a filter for routes you don't want recorded:

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

The query string and fragment are always dropped from a reported name, so `'/reset?token=abc123'` and `'/search?q=<whatever was typed>'` become `/reset` and `/search`: a query holds values, and a screen name identifies a screen. Names that aren't paths are left alone, so a route legitimately called `'Delete this?'` keeps its punctuation.

**Routers that navigate by URL need their patterns named.** GetX, go_router, Beamer, and anything else pushing `'/orders/42'` put that concrete path in `route.settings.name`, so every order becomes its own screen and an order id ends up in a name shown in the UI. Path parameters are the one thing the SDK can't clean up on its own — only your app knows which segments are ids — so name the patterns you registered and `LDRoutePatterns.extractor` collapses them back:

```dart
LDNavigatorObserver(
  screenNameExtractor: LDRoutePatterns.extractor(const [
    '/orders',
    '/orders/new', // A literal listed first wins over '/orders/:id'.
    '/orders/:id',
    '/orders/:id/receipt',
  ]),
);
```

That flow then reports `/orders/:id` and `/orders/:id/receipt` whatever ids were involved. A `:name` segment matches one segment and a trailing `*` matches the rest of the path, the same syntax those routers use. A route matching nothing is reported by its path, so a screen you forgot to list still shows up; pass `skipUnmatched: true` to record only the patterns you named.

An observer only sees one navigator, which a few common setups run into:

- **Nested navigators** — a tab shell, or a `Navigator` inside a page — report only their own routes, so each needs its own observer. One instance cannot be shared between navigators; Flutter asserts on that.
- **`MaterialApp.router`** (go_router, auto_route, Beamer) does not accept `navigatorObservers` at all. Pass the observer to the router's own observer list instead, such as `GoRouter(observers: [LDNavigatorObserver()])`.
- **`GetMaterialApp`** (GetX) merges the observers you pass with its own, so `navigatorObservers: [LDNavigatorObserver()]` works as it does on `MaterialApp`, with `LDRoutePatterns.extractor` fed your `GetPage` names. `GetMaterialApp.router` is the exception: it accepts `navigatorObservers` and then builds its delegate without them, so pass them as `routerDelegate: GetDelegate(navigatorObservers: [LDNavigatorObserver()])`.
- **Navigation that leaves the route stack unchanged** — switching tabs in an `IndexedStack`, paging a `PageView` — is invisible to any observer.

For those cases, and for screens you want to report with extra detail, call `LDObserve.trackScreenView` directly:

```dart
LDObserve.trackScreenView(
  'Checkout',
  screenClass: 'CheckoutPage',
  category: 'commerce',
  properties: {'cart_size': 3},
);
```

### Clicks (taps)

Taps are captured automatically. Every tap that resolves to a recognizable widget emits a `click` span describing the widget by type, identifier, visible label, ancestry path, and coordinates. On iOS and Android the same tap also becomes a `Click` marker on the replay timeline, so you can jump to the moment a widget was pressed.

This needs `SessionReplayCapture` around your app — it hosts the detector, so the same widget that feeds session replay also feeds click tracking. Wrap as high as possible, ideally around `MaterialApp`: dialogs and bottom sheets are children of the app's `Navigator`, so a wrap further down excludes taps on anything your app pushes above it.

Resolution happens in Dart because it can only happen in Dart: Flutter renders its whole UI into one native view, so a native hit-test names that view — `FlutterSurfaceView` — for every tap in your app regardless of what was pressed.

What ends up on the click:

- **`event.tag`** — the widget type, e.g. `ElevatedButton`. The Material and Cupertino buttons (`IconButton` and `FloatingActionButton` included), selection controls (`Switch`, `Checkbox`, `Radio`, `Slider`), chips, tabs, menus (`PopupMenuButton`, `DropdownButton`), navigation bars, `ListTile`, `InkWell`, and `GestureDetector` are recognized out of the box. A tap on unrecognized empty space, or on a **disabled** control, reports nothing at all.
- **`event.id`** — the first of an enclosing `LDClick` id, an id from your own `customClickTargetResolver`, a `Semantics.identifier`, or a `ValueKey`. Optional: a widget with none of those is still reported, grouped by type and path.
- **`event.text`** — the label: a button's own text, otherwise a semantic label, icon label, or tooltip. A container's inner text is deliberately *not* harvested, so a tapped row reports `ListTile` rather than whichever word sat under the finger. A radio with no label falls back to its `value`.
- **`event.xpath`** — the widget ancestry, e.g. `Scaffold/Column/ProductRow/IconButton#cart.add`. Framework plumbing (theme and media-query providers, builders, focus and semantics wrappers, single-child layout and painting boxes) is left out and only the innermost ten segments are kept, so the path names the screen and row a tap came from rather than the scaffolding every screen shares. Segments for widget types the SDK does not recognize come from the runtime type, which `--obfuscate` mangles; `event.tag` and `event.id` stay readable, so group on those.
- **`event.x` / `event.y`** — the tap point. Automatic capture reports the same units native taps use on that platform (physical pixels on Android, logical pixels / UIKit points on iOS), so a Flutter click lands on the replay timeline next to a native one. `LDObserve.trackClick`'s `x`/`y` are logical pixels (the same units Flutter `Offset` uses).

To name a specific widget, wrap it in `LDClick`. It renders its child unchanged and emits nothing itself, so wrapping a button cannot double-count a tap:

```dart
LDClick(
  id: 'checkout.pay',
  properties: {'cart_size': 3},
  child: ElevatedButton(onPressed: _pay, child: const Text('Pay')),
);
```

To name every instance of one of your own widget types — a design-system button that looks like an anonymous composition of Material widgets — register a resolver once instead of tagging each call site:

```dart
ObservabilityOptions(
  analytics: AnalyticsOptions(
    customClickTargetResolver: (widget) => switch (widget) {
      PrimaryButton(:final label) =>
        LDClickTargetInfo(tag: 'PrimaryButton', text: label),
      _ => null,
    },
  ),
);
```

Use string literals for `tag`, not `runtimeType.toString()`: release builds compiled with `--obfuscate` mangle runtime type names, and the built-in rules use literals for the same reason.

For an interaction automatic capture cannot observe — a shake, a hardware button, a custom recognizer — report it yourself. Pass `x`/`y` in logical pixels (the same units `Offset` uses). Avoid calling this from an `onPressed` that capture already sees, which would count the tap twice:

```dart
LDObserve.trackClick(
  id: 'onboarding.shake_to_skip',
  tag: 'ShakeGesture',
  x: 24,
  y: 80,
  properties: {'step': 2},
);
```

Limitations worth knowing:

- **Only taps count as clicks.** A press that moves further than `kTouchSlop` (a scroll or a drag), one that outlasts the long-press timeout, and anything multi-touch are all left unreported — the same rule the native SDKs apply, so Flutter and native clicks stay comparable.
- **Embedded platform views** (`WebView`, native maps) resolve to the Flutter widget hosting them, e.g. `WebViewWidget`. What was pressed *inside* the embedded view is not described.
- **Pointer-blocking overlays are honored.** An `IgnorePointer` is transparent to the walk (the tap went past it). An absorbing `AbsorbPointer` — a loading overlay laid over a `Stack` — swallows the tap, so neither its children nor the controls painted behind it are reported. That matches what Flutter delivered to the app.
- **Click text follows your masking.** Text inside an `LDMask`/`LDIgnore` subtree is never reported, editable field contents are never read, and `PrivacyOptions.maskClickText` turns off click labels entirely while keeping the clicks themselves.

### API reference

| Method | Description |
|---|---|
| `LDObserve.startSpan(name, {kind, properties})` | Start a span that nests under the current active span. Returns a `Span`. |
| `LDObserve.recordLog(message, {severity, stackTrace, properties})` | Record a structured log. |
| `LDObserve.recordException(exception, {stackTrace, properties})` | Record an error/exception. |
| `LDObserve.track(eventName, {properties, metricValue})` | Record a custom `track` event as a `track` span. |
| `LDObserve.trackScreenView(name, {screenClass, screenId, category, properties})` | Record a screen view (navigation) as a `screen_view` span and a Session Replay `Navigate` event. Prefer `LDNavigatorObserver` for ordinary route changes. |
| `LDObserve.trackClick({id, tag, text, x, y, properties})` | Record a click as a `click` span and a Session Replay `Click` event, for interactions automatic capture cannot observe. `x`/`y` are logical pixels. |
| `LDObserve.shutdown()` | Flush buffered spans, remove the Dart instrumentations, stop session replay, and turn every recording API into a no-op. Terminal: a later `init` does nothing. On Android and iOS the native SDK's automatic instrumentation (crashes, network, launch times) keeps running, because it has no teardown. |
| `LDObserve.zoneSpecification()` | A zone spec that forwards `print`/`debugPrint` output as logs. |
| `LDNavigatorObserver({screenNameExtractor, category})` | A `NavigatorObserver` that reports each route change as a screen view. |
| `LDRoutePatterns.extractor(patterns, {skipUnmatched})` | A `screenNameExtractor` that reports the route pattern a navigation matched, so `/orders/42` becomes `/orders/:id`. |
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

Methods that accept `properties` (`recordLog`, `recordException`, `startSpan`, `track`, `trackScreenView`, `trackClick`) take a `Map<String, Object?>` of these same plain values.

## Session replay

Session Replay captures screen recordings to help you understand how users interact with your application. Enable it by passing `SessionReplayOptions` to `LDObserve.init` / `LDObserve.initStandalone`, and wrap the part of your app you want recorded in a `SessionReplayCapture` widget:

```dart
runApp(const SessionReplayCapture(child: MyApp()));
```

On mobile, the part of your app wrapped in `SessionReplayCapture` is what gets recorded; without it, those frames are not captured. Session replay is not supported on web, so there `SessionReplayCapture` records nothing, but it still hosts [click capture](#clicks-taps) — wrapping your app with it is safe and useful on every platform.

### Privacy options

Control what is captured during a session with `PrivacyOptions`:

- `maskTextInputs`: (Default: `true`) Masks all text input fields.
- `maskWebViews`: (Default: `false`) Masks all web view content.
- `maskLabels`: (Default: `false`) Masks all text labels.
- `maskImages`: (Default: `false`) Masks all images.
- `minimumAlpha`: (Default: `0.02`) Opacity threshold below which a widget is treated as invisible and skipped during capture and masking. Raise it to ignore nearly-transparent UI.
- `maskClickText`: (Default: `false`) Drops the visible label from click events, so clicks report the widget type and identifier but no text. Independent of `maskLabels`, which controls whether text is painted over in the frames — a screen can be readable in replay while its click stream stays anonymous, or the reverse.

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

`LDMask` / `LDIgnore` / `LDUnmask` are active on iOS and Android. On web, where session replay is not supported, they render their child unchanged.

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
