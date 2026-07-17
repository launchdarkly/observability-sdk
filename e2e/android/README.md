# Android Observability e2e app

Demo app for the LaunchDarkly Android Observability SDK. It also exercises
**Android Java/Kotlin symbolication (the "Symbols Id Lane")**: R8 obfuscates
release builds, and obfuscated crash/error stack traces are retraced on the
backend using the uploaded `mapping.txt`, keyed by a deterministic symbols id.

## How the Symbols Id Lane works

R8 rewrites class/method names and line numbers in release builds, so a recorded
error's stack trace looks like `at g5.g.invoke(SourceFile:57)`. To make it
readable again:

1. **Build** — R8 emits `mapping.txt`. A Gradle task
   (`stampLaunchDarklySymbolsId<Variant>` in [`app/build.gradle.kts`](app/build.gradle.kts))
   derives a deterministic symbols id (`htlhash(mapping.txt)`, reported as
   `launchdarkly.symbols_id.htlhash`) and:
   - embeds it into the app as `assets/ld_symbols_id.txt`; and
   - stages `mapping.txt` + `mapping.txt.symbolsid` under
     `app/build/symbols/<variant>/` for upload.

   Because the id is a hash of the *mapping* (not the app), embedding it back into
   the app never changes the mapping — no self-reference.

2. **Runtime** — the SDK reads `assets/ld_symbols_id.txt` and reports it as the
   resource attribute `launchdarkly.symbols_id.htlhash` on every signal.

3. **Upload** — `ldcli symbols upload --type android` uploads `mapping.txt` keyed
   by that symbols id (`_sym/android/id/<symbolsId>/mapping.txt`), reading the id
   from the `.symbolsid` sidecar automatically.

4. **Symbolication** — the backend sees the symbols id on the error, loads the
   matching `mapping.txt`, and retraces each frame back to the original
   class/method/line (expanding inlined frames).

## Prerequisites

- Android SDK + a configured emulator/device.
- [`ldcli`](https://github.com/launchdarkly/ldcli) built/installed.
- `app/local.properties` with your keys/endpoints:

  ```properties
  sdk.dir=/path/to/Android/sdk
  launchdarkly.mobileKey=<your-mobile-key>
  # For a local backend from the Android emulator, 10.0.2.2 is the host loopback:
  launchdarkly.otlpEndpoint=http://10.0.2.2:4318
  launchdarkly.backendUrl=http://10.0.2.2:8082/public/
  ```

## 1. Build a release (obfuscated) APK

Release builds have `isMinifyEnabled = true`, so R8 obfuscates and produces the
mapping. Debug builds are not obfuscated and don't need symbolication.

```bash
# compose flavor (java flavor: assembleJavaRelease)
./gradlew :app:assembleComposeRelease
```

The task logs the symbols id, e.g.:

```
LaunchDarkly: symbols id 4d839d4c3eb1b6aeb0ec3d82774ce6c7 (mapping 64260368 bytes) -> assets/ld_symbols_id.txt + .../build/symbols/composeRelease/mapping.txt.symbolsid
```

Install it on a device/emulator (from `e2e/android/`):

```bash
./gradlew :app:installComposeRelease
adb shell am start -n com.example.androidobservability/.MainActivity
```

The release build is signed with the debug keystore (see `signingConfig` in
[`app/build.gradle.kts`](app/build.gradle.kts)) so it installs locally without a
production keystore; swap in a real release `signingConfig` before shipping.

## 2. Upload the mapping

Run this from the `e2e/android/` directory (the `--path` is relative to it; use an
absolute path if you run it elsewhere). `ldcli` reads the `.symbolsid` sidecar
automatically, so no `--symbols-id` is needed:

```bash
# from e2e/android/
ldcli symbols upload \
  --type android \
  --path ./app/build/symbols/composeRelease \
  --project <project-key> \
  --backend-url http://localhost:8082/private \
  --access-token <api-token>
```

You can also pass `--symbols-id <id>` explicitly, or `--app-version <version>` to
key by version instead (the Version Lane) if you don't inject a symbols id.

## 3. Trigger an obfuscated error

In the app, tap **Trigger Obfuscated Error**. It throws deep inside an
obfuscated multi-class chain ([`CheckoutDemo`](app/src/main/java/com/example/androidobservability/SymbolicationDemo.kt))
and records it via `LDObserve.recordError`. On the LaunchDarkly errors view the
frames should resolve to `CartPricing.computeTotal`, `CartPricing.priceOrder`, and
`CheckoutDemo.startCheckout` (R8 inlines the chain, and the backend expands it).

## Notes

- The mapping can be large because [`app/proguard-rules.pro`](app/proguard-rules.pro)
  keeps the SDK/OpenTelemetry packages intact (`-keep class …`) for runtime safety;
  only the app's own `com.example.androidobservability.*` classes are obfuscated,
  which is what the retrace demo exercises.
- `-keepattributes SourceFile,LineNumberTable` is required so R8 records line
  numbers; `-renamesourcefileattribute SourceFile` hides original file names (the
  backend derives them from the retraced class).
