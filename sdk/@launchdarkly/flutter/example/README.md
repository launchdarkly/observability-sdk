# LaunchDarkly Observability SDK for Flutter — Example App

A complete, runnable app that demonstrates the [`launchdarkly_flutter_observability`](../packages/observability) package: spans, logs, error reporting, feature flag correlation, and session replay.

The home screen lets you trigger errors, logs, spans, HTTP requests, identify calls, flag evaluations, and a session replay masking demo so you can see how each one is reported.

> Looking for how to **install and use the package in your own app**, or the `LDObserve` API reference? See the [package README](../packages/observability/README.md). This document only covers running *this* example.

## Prerequisites

- Flutter SDK matching the constraint in `pubspec.yaml`. Verify with:
  ```bash
  flutter --version
  flutter doctor
  ```
- **iOS**: Xcode with iOS platform support, CocoaPods (`brew install cocoapods`) or Swift Package Manager, and a booted Simulator or signed physical device.
- **Android**: Android Studio with an SDK and an emulator/AVD, or a USB device with USB debugging enabled.
- **Web**: Chrome.
- Cursor (or VS Code) with the official **Dart** and **Flutter** extensions.

## 1. Get your LaunchDarkly credentials

In the [LaunchDarkly dashboard](https://app.launchdarkly.com/), open your project/environment settings and copy either:

- **Mobile key** — for iOS, Android, macOS, Windows, Linux.
- **Client-side ID** — for Chrome/Web builds.

You only need the one(s) for the platforms you intend to run.

## 2. Configure `dart_defines.json`

The example reads its credentials from compile-time constants injected via `--dart-define`. To keep secrets out of source control, load them from a JSON file:

```bash
cp dart_defines.example.json dart_defines.json
```

You can set **both** keys at the same time — the example's credential resolver picks the right one per platform (the **client-side ID** on web, the **mobile key** everywhere else), so you switch platforms just by choosing a different device, with no file edits:

```json
{
  "LAUNCHDARKLY_CLIENT_SIDE_ID": "xxxxxxxxxxxxxxxxxxxxxxxx",
  "LAUNCHDARKLY_MOBILE_KEY": "mob-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "GIT_SHA": "local-dev"
}
```

Prefer to keep only the key(s) for the platforms you run? That works too — just include the relevant one. Web requires `LAUNCHDARKLY_CLIENT_SIDE_ID`; iOS / Android / macOS / Windows / Linux require `LAUNCHDARKLY_MOBILE_KEY`.

> The platform-aware selection lives in the example app (`_ldCredential()` in `lib/main.dart`), not the SDK. It replaces `CredentialSource.fromEnvironment()`, which rejects having both keys set at once.

Notes:

- `GIT_SHA` is passed to `ObservabilityOptions.serviceVersion`; any string works.
- `OTLP_ENDPOINT` and `BACKEND_URL` are **optional** overrides for the observability OTLP endpoint and backend URL (e.g. to target staging), mirroring `otlpEndpoint` / `backendUrl` in the Swift sample's `Secrets.xcconfig`. Omit them to use the production defaults baked into `ObservabilityOptions`.
- `dart_defines.json` is **gitignored** — never commit it. The committed `dart_defines.example.json` is the template.
- `--dart-define-from-file` values are **compile-time constants**. After editing the file you must fully stop and re-launch — hot reload/restart will not pick up new values.

## 3. Install dependencies

```bash
flutter pub get
```

For iOS with CocoaPods:

```bash
cd ios && pod install && cd ..
```

## 4. Run

From the Cursor/VS Code Run & Debug panel, the bundled `.vscode/launch.json` wires `--dart-define-from-file=dart_defines.json` into the debugger — pick a device and an `example` config and press **F5**.

Or from the command line:

```bash
# List devices to find the exact name/UDID
flutter devices

# iOS Simulator
flutter run -d "iPhone 16e" --dart-define-from-file=dart_defines.json

# Android emulator
flutter run -d emulator-5554 --dart-define-from-file=dart_defines.json

# Chrome / Web
flutter run -d chrome --dart-define-from-file=dart_defines.json
```

## Use local native SDK checkouts

By default, the example uses the published native dependencies declared by `packages/observability`. To test local native changes, set `LD_USE_LOCAL_NATIVE` (the `*, local native` launch configs do this for you):

```bash
LD_USE_LOCAL_NATIVE=true flutter run -d <device-id> --dart-define-from-file=dart_defines.json
```

For iOS CocoaPods, reinstall pods after toggling the switch:

```bash
cd ios && LD_USE_LOCAL_NATIVE=true pod install && cd ..
```

## Verify it's working

In the running app:

- **Trigger Error** / **Trigger Crash** → reported through `LDObserve.recordException`.
- **Trigger Log** / **Log with Context** / **Record error log with stack trace** → flow through `LDObserve.recordLog`.
- **Nested Spans** / **Trigger Sequential Spans** / **Send custom span** → spans recorded via `LDObserve.startSpan`.
- **Trigger HTTP Request** → an outgoing request that is traced when network instrumentation is enabled.
- **Identify** (User / Multi / Anon) → switches the active LaunchDarkly context.
- **Credit Card** → a masked form for the session replay masking demo.

Then check the **Observability** section of your LaunchDarkly project for traces, logs, and errors tagged with your `serviceName`.

## Symbolication (readable stack traces)

Obfuscated release builds (`--obfuscate --split-debug-info`) report Dart AOT
stack traces as raw addresses until the debug symbols are uploaded to
LaunchDarkly. See **[SYMBOLICATION.md](./SYMBOLICATION.md)** for the full
walkthrough — building with split debug info, uploading with
`ldcli symbols upload --type flutter`, and the Symbols Id vs Version lanes.

## Troubleshooting

- **`No supported devices found ...`** — run `flutter devices` for the exact name/UDID. Don't use `-d ios`; use the device name/UDID or `-d apple_ios`.
- **`pod: command not found`** — `brew install cocoapods`, then `cd ios && pod install`.
- **`Web builds require LAUNCHDARKLY_CLIENT_SIDE_ID ...`** / **`Non-web builds require LAUNCHDARKLY_MOBILE_KEY ...`** — the key for the platform you launched is missing/empty in `dart_defines.json`, or `--dart-define-from-file=dart_defines.json` didn't reach the build. Confirm the file exists here, you used a launch config (not a default Dart/Flutter config), and you fully stopped & re-launched after editing it.
- **Blank/white screen on web** — almost always a missing/invalid `LAUNCHDARKLY_CLIENT_SIDE_ID`. Web needs the **Client-side ID** (no prefix), not a mobile key (`mob-`) or server-side SDK key (`sdk-`).

## Resources

- [`launchdarkly_flutter_observability` package README](../packages/observability/README.md) — install & API usage.
- [SYMBOLICATION.md](./SYMBOLICATION.md) — symbolicating obfuscated release stack traces.
- [Flutter installation](https://docs.flutter.dev/get-started/install)
- [LaunchDarkly Flutter SDK docs](https://docs.launchdarkly.com/sdk/client-side/flutter)
- [LaunchDarkly observability docs](https://docs.launchdarkly.com/home/observability)
- [Dart `--dart-define-from-file` reference](https://dart.dev/tools/dart-define-from-file)
