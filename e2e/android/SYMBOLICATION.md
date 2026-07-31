# Android Symbolication — uploading `mapping.txt` for this example

R8 obfuscates release builds, so a recorded error's stack trace looks like
`at g5.g.invoke(SourceFile:57)`. Uploading the `mapping.txt` R8 produced lets the
backend retrace those frames back to the original class, method and line.

This app uploads it keyed by **app version** — the "Version Lane". The build does
nothing for symbolication at all: `ldcli` finds the mapping where R8 wrote it and
reads the version out of the packaged APK.

## How the Version Lane works

1. **Build** — R8 emits `app/build/outputs/mapping/<variant>/mapping.txt`.

2. **Runtime** — the SDK reports the app's version as `service.version`, which the
   backend records on every error.

3. **Upload** — `ldcli symbols upload --type android`, run from the project root,
   finds the mapping and the version and stores it at `<version>/mapping.txt`.

4. **Symbolication** — the backend looks up the mapping by the version on the
   error and retraces each frame (expanding inlined frames).

> **The app has to report its own version.** `ObservabilityOptions.serviceVersion`
> defaults to the *SDK's* version, which describes no build of your app, so a
> mapping uploaded for `1.0.1` would never be matched. This app sets
> `serviceVersion = BuildConfig.VERSION_NAME` in
> [`BaseApplication.kt`](app/src/compose/java/com/example/androidobservability/BaseApplication.kt).
> That one line is the whole setup.

Keying by version means the newest upload for a version wins. That is fine while a
version is one build, and wrong as soon as it isn't: rebuild `1.0.1` after changing
code and the second mapping replaces the first, so errors still coming in from the
first build retrace to the wrong lines. Keying by a content-derived symbols id
instead avoids that.

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

Install it on a device/emulator (from `e2e/android/`):

```bash
./gradlew :app:installComposeRelease
adb shell am start -n com.example.androidobservability/.MainActivity
```

The release build is signed with the debug keystore (see `signingConfig` in
[`app/build.gradle.kts`](app/build.gradle.kts)) so it installs locally without a
production keystore; swap in a real release `signingConfig` before shipping.

## 2. Upload the mapping

Run this from the `e2e/android/` directory. There is no `--path` and no
`--app-version`: `ldcli` reads both out of the build.

```bash
# from e2e/android/
ldcli symbols upload \
  --type android \
  --project default \
  --backend-url http://localhost:8082/private \
  --access-token <api-token>
```

```
Found the composeRelease mapping at app/build/outputs/mapping/composeRelease/mapping.txt
Using app version 1.0.1, as packaged for composeRelease
[LaunchDarkly] Uploaded .../mapping.txt to mapping.txt
Successfully uploaded all symbols
```

It looks for `<module>/build/outputs/mapping/<variant>/mapping.txt`, which is where
R8 writes it, and takes the version from the `output-metadata.json` AGP writes
beside the packaged APK — the same version the app reports. Build more than one
obfuscated variant and it names them and asks for `--path`, rather than guessing
which one you shipped.

Either can still be given explicitly: `--path` to point at a mapping somewhere
else, `--app-version <version>` to key by something other than what was packaged.

### Optional — also upload your sources (`--include-sources`)

Retracing alone gets you `CartPricing.computeTotal` at `SymbolicationDemo.kt:42`.
Adding `--include-sources` also uploads your `.java`/`.kt` files, so the errors
page shows the **code around each frame** instead of just the location:

```bash
# from e2e/android/
ldcli symbols upload \
  --type android \
  --project default \
  --base-uri https://ld-stg.launchdarkly.com \
  --backend-url http://localhost:8082/private \
  --access-token <api-token> \
  --include-sources \
  --source-path ./app/src
```

```
Built source bundle from ./app/src (28 files, 37272 bytes)
```

Unlike Apple, an R8 mapping records no file paths at all — only
(class, method, line) — so `ldcli` has to be told where your sources are.
`--source-path` defaults to the current directory, which works from a project
root; pointing it at a source directory is faster and avoids bundling test or
sample code. Files under `build/`, `.gradle/`, `.git/`, `.idea/` and
`node_modules/` are always skipped.

> **Point it above your source sets, not at `main`.** This app has product
> flavors, so a `composeRelease` build compiles `app/src/main` **and**
> `app/src/compose`. `--source-path ./app/src/main` would bundle only the former
> and silently omit `MainActivity.kt` and `MainActivityViewModel.kt` — the upload
> succeeds, and those frames just never get a snippet. `./app/src` covers every
> source set. The same applies to any multi-module project: pass a directory that
> contains all the modules you ship.

The files are packed into a `sources.srcbundle` uploaded beside `mapping.txt`
under the same version, so sources are matched to a build exactly as the mapping
is. Each file is keyed by its **declared package** plus its file name
(`com/example/androidobservability/SymbolicationDemo.kt`), which is what R8's
own `sourceFile` metadata names for each retraced class — so a Kotlin file in a
directory that doesn't mirror its package still resolves, and so does a file
that declares several classes (`SymbolicationDemo.kt` holds both `CheckoutDemo`
and `CartPricing`).

- **Off by default, because it stores your source code in LaunchDarkly.**
- Individual files over 2 MiB, and a bundle over 64 MiB, are skipped.
- If no sources are found it says so and uploads the mapping alone, so this flag
  can't break an upload that would otherwise work.

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
