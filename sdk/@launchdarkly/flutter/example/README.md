# LaunchDarkly Flutter Observability — Example App

A minimal Flutter app that demonstrates the `launchdarkly_flutter_observability` plugin: initializing the LaunchDarkly client with the `ObservabilityPlugin`, recording spans, logs, and exceptions, and routing them through OpenTelemetry.

The button on the home screen lets you trigger an unhandled error, record warn/error logs, and call `debugPrint`/`print` so you can see how each gets reported.

---

## 1. Prerequisites

- Flutter SDK matching the constraint in `pubspec.yaml` (`sdk: ^3.10.0-162.1.beta`). Verify with:
  ```bash
  flutter --version
  flutter doctor
  ```
  All checkmarks in `flutter doctor` should be green for the platforms you want to target.
- **iOS**: Xcode (with iOS Platform Support installed via Xcode → Settings → Platforms), CocoaPods (`brew install cocoapods`) or Flutter Swift Package Manager support, and a booted iOS Simulator (`open -a Simulator`) or a physical iPhone with signing set up.
- **Android**: Android Studio with an SDK and at least one AVD/emulator (start one from **Android Studio → Device Manager**) or a USB‑connected device with USB debugging enabled.
- Cursor (or VS Code) with the official **Dart** and **Flutter** extensions installed.

---

## 2. Get your LaunchDarkly credentials

In the [LaunchDarkly dashboard](https://app.launchdarkly.com/):

1. Pick the **Project** and **Environment** you want to use.
2. Open the environment's settings and copy:
   - **Mobile key** — for iOS, Android, macOS, Windows, Linux.
   - **Client‑side ID** — for Chrome/Web builds.

You don't need both; only the one(s) for the platforms you intend to run.

---

## 3. Configure `dart_defines.json`

The example calls `CredentialSource.fromEnvironment()`, which reads compile‑time constants (`LAUNCHDARKLY_MOBILE_KEY`, `LAUNCHDARKLY_CLIENT_SIDE_ID`) injected via `--dart-define`. To keep the secrets out of source control we load them from a JSON file via `--dart-define-from-file`.

From this directory:

```bash
cp dart_defines.example.json dart_defines.json
```

Then open `dart_defines.json` and replace the placeholder with your real value. For iOS / Android / macOS / Windows / Linux:

```json
{
  "LAUNCHDARKLY_MOBILE_KEY": "mob-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "GIT_SHA": "local-dev"
}
```

For Chrome / Web, use the client‑side ID instead:

```json
{
  "LAUNCHDARKLY_CLIENT_SIDE_ID": "xxxxxxxxxxxxxxxxxxxxxxxx",
  "GIT_SHA": "local-dev"
}
```

> **Important**: set **only one** of `LAUNCHDARKLY_MOBILE_KEY` or `LAUNCHDARKLY_CLIENT_SIDE_ID`. The SDK throws `"When building an application using the SDK you should include either a client-side ID, or a mobile key, but not both"` if both keys are present as non‑empty strings — even placeholder values like `"xxxxxxxxxxxxxxxxxxxxxxxx"` count as non‑empty and will trip the check. Delete (don't just placeholder) the one you aren't using.

Notes:

- `GIT_SHA` is read by `main.dart` and passed to `ObservabilityPlugin.applicationVersion`; any string works (commit SHA, semver, etc.).
- `dart_defines.json` is **gitignored** — never commit it. The committed `dart_defines.example.json` is the template for other contributors.
- For CI/automation, you can replace the file with `--dart-define LAUNCHDARKLY_MOBILE_KEY=...` flags instead.
- `--dart-define-from-file` values are **compile‑time constants**. After editing `dart_defines.json` you must fully stop and re‑launch the app — hot reload and hot restart will not pick up new values.

---

## 4. Install dependencies

```bash
flutter pub get
```

For iOS with CocoaPods, also install pod dependencies (the first `flutter run` does this automatically, but running it manually surfaces errors more clearly):

```bash
cd ios && pod install && cd ..
```

If CocoaPods cannot find a LaunchDarkly pod version, refresh your local specs:

```bash
cd ios && pod install --repo-update && cd ..
```

### Use local native SDK checkouts

By default, the example uses the published native dependencies declared by
`packages/session_replay`. To test local native changes, use one of the
`*, local native` launch configs in the root or example `.vscode/launch.json`
files (they set `LD_USE_LOCAL_NATIVE` via `env`), or set the variable in your
shell before running Flutter:

```bash
LD_USE_LOCAL_NATIVE=true flutter run -d <device-id> --dart-define-from-file=dart_defines.json
```

The default local paths assume this workspace layout:

```text
flutter/
  observability-sdk/
    sdk/@launchdarkly/observability-android/
    sdk/@launchdarkly/flutter/
  swift-launchdarkly-observability/
```

Override those paths when needed:

```bash
export LD_OBSERVABILITY_ANDROID_PATH=/path/to/observability-android
export LD_SWIFT_OBSERVABILITY_PATH=/path/to/swift-launchdarkly-observability
LD_USE_LOCAL_NATIVE=true flutter run -d <device-id> --dart-define-from-file=dart_defines.json
```

For iOS CocoaPods, reinstall pods after changing the switch:

```bash
cd ios
LD_USE_LOCAL_NATIVE=true pod install
# or switch back to published pods:
pod install
cd ..
```

For Android Gradle commands, you can also use Gradle properties instead of
environment variables:

```bash
cd android
./gradlew assembleDebug \
  -PldUseLocalNative=true \
  -PldObservabilityAndroidPath=/path/to/observability-android
```

To exercise Flutter's Swift Package Manager integration instead, enable it once with:

```bash
flutter config --enable-swift-package-manager
```

---

## 5. Run from the Cursor UI (recommended)

This folder ships with `.vscode/launch.json` ([file](.vscode/launch.json)) that wires `--dart-define-from-file=dart_defines.json` into the Dart/Flutter debugger. You can use it in two ways:

### Option A — Open the `example/` folder directly

`File → Open Folder…` → select this `example/` directory. The Run & Debug panel (⇧⌘D / Ctrl+Shift+D) will show three configs:

- `example (debug)`
- `example (profile)`
- `example (release)`

### Option B — Open the parent `flutter/` workspace

If you have the larger `flutter/` workspace open, the root `/.vscode/launch.json` exposes the same configs under different names:

- `ld-observability example (debug)`
- `ld-observability example (profile)`
- `ld-observability example (release)`
- `ld-observability example (debug, local native)` — sets `LD_USE_LOCAL_NATIVE=true` via launch `env`
- `ld-observability example (profile, local native)`
- `ld-observability example (release, local native)`

### Then, in either case

1. Pick a device in the **device selector** at the bottom right of the Cursor window (e.g. `iPhone 16e`, `Pixel 7 API 34`, `Chrome`, `macOS`).
2. Select the matching config in the Run & Debug dropdown.
3. Hit **F5** (or click the green ▶ button).

Hot reload is **⌘\\** (macOS) / **Ctrl+\\**; hot restart is **⇧⌘F5** / **Ctrl+Shift+F5**.

---

## 6. Run from the command line

If you'd rather not use the debugger UI:

### iOS Simulator

```bash
# Make sure a simulator is booted
open -a Simulator

# List devices to find the exact name/UDID
flutter devices

# Run (replace device name with what flutter devices shows)
flutter run -d "iPhone 16e" --dart-define-from-file=dart_defines.json
```

You can also target by UDID for stability across simulator swaps:

```bash
flutter run -d <UDID-from-flutter-devices> --dart-define-from-file=dart_defines.json
```

### Physical iPhone

1. Open `ios/Runner.xcworkspace` in Xcode.
2. Select the **Runner** target → **Signing & Capabilities** → check **Automatically manage signing** → pick your **Team** (sign in with your Apple ID in Xcode → Settings → Accounts if no team is listed).
3. Change the **Bundle Identifier** to something unique (e.g. `com.<yourname>.ldobservability.example`); the default `com.example.example` cannot be signed by a personal team.
4. Plug in the iPhone, trust the Mac on the device, then:
   ```bash
   flutter run -d <your-iphone-name> --dart-define-from-file=dart_defines.json
   ```

### Android Emulator

```bash
# Start an emulator (or use Android Studio's Device Manager)
flutter emulators
flutter emulators --launch <emulator-id>

flutter devices
flutter run -d emulator-5554 --dart-define-from-file=dart_defines.json
```

### Physical Android device

1. Enable **Developer options** and **USB debugging** on the device (Settings → About phone → tap *Build number* 7 times, then Settings → Developer options → USB debugging).
2. Plug it in, accept the "Allow USB debugging" prompt.
3. ```bash
   flutter devices
   flutter run -d <device-id> --dart-define-from-file=dart_defines.json
   ```

---

## 7. Verify it's working

After launching, in the running app:

- Tap **Trigger unhandled error** → an exception is thrown and reported through `Observe.recordException` (via `runZonedGuarded`).
- Tap **Record warning log** / **Record error log with stack trace** → logs flow through `Observe.recordLog`.
- Tap the **+** floating action button → two nested spans (`increment-counter-1`, `increment-counter-2`) are recorded around a `stringVariation` call.

Then check the **Observability** section of your LaunchDarkly project for traces, logs, and errors tagged with `applicationName: test-application`.

---

## Troubleshooting

- **`No supported devices found with name or id matching '<name>'`** — run `flutter devices` to get the exact name/UDID. Don't use `-d ios` (not a valid flag); either use the device name/UDID or `-d apple_ios` for any iOS device.
- **`pod: command not found`** — `brew install cocoapods`, then `cd ios && pod install`.
- **`Automatically assigning platform iOS with version 13.0`** — already fixed in `ios/Podfile` by setting `platform :ios, '13.0'` explicitly.
- **`CocoaPods did not set the base configuration ... Pods-Runner.profile.xcconfig`** — harmless warning. Only matters if you run `flutter run --profile`. Fix by creating `ios/Flutter/Profile.xcconfig` (mirror of `Release.xcconfig` but pointing to `Pods-Runner.profile.xcconfig`) and switching the **Profile** build configuration of the `Runner` target to it in Xcode.
- **Cursor prompts to "enable iOS development"** — you're launching the wrong config. Make sure you've selected one of the `example` / `ld-observability example` configs, not a config that points at a different project. See section 5 above.
- **`The mobile key was not specified, but must be for this build type`** — `--dart-define-from-file=dart_defines.json` didn't reach the build. Confirm `dart_defines.json` exists in this directory, you're using one of the launch configs above (not a default Dart/Flutter config), and that you fully stopped & re‑launched the app after editing the file (hot reload/restart won't pick up new defines).
- **`When building an application using the SDK you should include either a client-side ID, or a mobile key, but not both`** — both `LAUNCHDARKLY_MOBILE_KEY` and `LAUNCHDARKLY_CLIENT_SIDE_ID` are set as non‑empty strings in `dart_defines.json`. Delete the one for the platform you're not targeting (placeholder strings like `"xxxx..."` still count as set).

---

## Resources

- [Flutter installation](https://docs.flutter.dev/get-started/install)
- [Flutter cookbook](https://docs.flutter.dev/cookbook)
- [LaunchDarkly Flutter SDK docs](https://docs.launchdarkly.com/sdk/client-side/flutter)
- [LaunchDarkly observability docs](https://docs.launchdarkly.com/home/observability)
- [Dart `--dart-define-from-file` reference](https://dart.dev/tools/dart-define-from-file)
