# Android Symbolication — uploading `mapping.txt` for this example

R8 obfuscates release builds, so a recorded error's stack trace looks like
`at g5.g.invoke(SourceFile:57)`. Uploading the `mapping.txt` R8 produced lets the
backend retrace those frames back to the original class, method and line.

There are two ways to say *which build* a mapping belongs to, and this app can
build either way so you can compare them:

| | keyed by | build setup | stored at |
|---|---|---|---|
| **Symbols Id Lane** (default) | a content hash of the mapping | a Gradle task stamps the id into the app | `_sym/android/id/<id>/mapping.txt` |
| **Version Lane** (`-Pld.symbolsId=false`) | the app's version | none | `<version>/mapping.txt` |

Neither needs anything on the `ldcli` command line: it finds the mapping where R8
wrote it, and reads the id or the version out of the packaged APK.

## How the Symbols Id Lane works

1. **Build** — R8 emits `app/build/outputs/mapping/<variant>/mapping.txt`. A Gradle
   task (`stampLaunchDarklySymbolsId<Variant>` in [`app/build.gradle.kts`](app/build.gradle.kts))
   derives a deterministic symbols id (`htlhash(mapping.txt)`) and embeds it into
   the app as `assets/ld_symbols_id.txt`.

   Because the id is a hash of the *mapping* (not the app), embedding it back into
   the app never changes the mapping — no self-reference.

2. **Runtime** — the SDK reads `assets/ld_symbols_id.txt` and reports it as the
   resource attribute `launchdarkly.symbols_id.htlhash` on every signal.

3. **Upload** — `ldcli symbols upload --type android` reads that same asset back
   out of the packaged APK, so the mapping is keyed by exactly what the app
   reports.

4. **Symbolication** — the backend sees the symbols id on the error, loads the
   matching mapping, and retraces each frame (expanding inlined frames).

## How the Version Lane works

The fallback when a build stamps no id: the SDK reports the app's version as
`service.version`, the upload stores the mapping at `<version>/mapping.txt`, and
the backend looks it up by the version on the error.

> **The app has to report its own version.** `ObservabilityOptions.serviceVersion`
> defaults to the *SDK's* version, which describes no build of your app, so a
> mapping uploaded for `1.0.1` would never be matched. This app sets
> `serviceVersion = BuildConfig.VERSION_NAME` in
> [`BaseApplication.kt`](app/src/compose/java/com/example/androidobservability/BaseApplication.kt).

It costs no build setup, and in exchange the newest upload for a version wins.
That is fine while a version is one build, and wrong as soon as it isn't: rebuild
`1.0.1` after changing code and the second mapping replaces the first, so errors
still arriving from the first build retrace to the wrong lines. A symbols id is
derived from the mapping's own contents, so two builds are never the same id and
neither can overwrite the other.

Both lanes can hold a mapping at once, and the backend tries the symbols id first,
so adding the id to a build that was on the Version Lane is not a cutover.

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

The stamp task logs the symbols id it embedded:

```
LaunchDarkly: symbols id 7e0d66142a85de6c6b2850dcbba5f066 (mapping 64266122 bytes) -> assets/ld_symbols_id.txt
```

Add `-Pld.symbolsId=false` to build without one and exercise the Version Lane
instead. The app code is identical either way, so R8 produces the same mapping and
the only difference is how it gets found.

Install it on a device/emulator (from `e2e/android/`):

```bash
./gradlew :app:installComposeRelease
adb shell am start -n com.example.androidobservability/.MainActivity
```

The release build is signed with the debug keystore (see `signingConfig` in
[`app/build.gradle.kts`](app/build.gradle.kts)) so it installs locally without a
production keystore; swap in a real release `signingConfig` before shipping.

## 2. Upload the mapping

Run this from the `e2e/android/` directory. The same command covers both lanes —
there is no `--path`, `--symbols-id` or `--app-version`, because `ldcli` reads all
three out of the build:

```bash
# from e2e/android/
ldcli symbols upload \
  --type android \
  --project default \
  --backend-url http://localhost:8082/private \
  --access-token <api-token>
```

With a stamped build it keys by the id:

```
Found the composeRelease mapping at app/build/outputs/mapping/composeRelease/mapping.txt
Using app version 1.0.1, as packaged for composeRelease
Using symbols id 7e0d66142a85de6c6b2850dcbba5f066 for all files (Symbols Id Lane: _sym/android/id/7e0d66142a85de6c6b2850dcbba5f066)
```

and after `-Pld.symbolsId=false`, where there is no id to find, by the version —
storing the same mapping at `1.0.1/mapping.txt` instead:

```
Found the composeRelease mapping at app/build/outputs/mapping/composeRelease/mapping.txt
Using app version 1.0.1, as packaged for composeRelease
[LaunchDarkly] Uploaded .../mapping.txt to mapping.txt
```

It looks for `<module>/build/outputs/mapping/<variant>/mapping.txt`, which is where
R8 writes it; for `assets/ld_symbols_id.txt` inside the packaged APK, which is the
id the app will actually report; and for the version in the `output-metadata.json`
AGP writes beside that APK. Build more than one obfuscated variant and it names
them and asks for `--path`, rather than guessing which one you shipped.

Any of it can still be given explicitly: `--path` to point at a mapping somewhere
else, `--symbols-id <id>`, or `--app-version <version>`.

Re-running an upload for a build already uploaded skips it: a symbols id is
derived from the mapping's contents, so LaunchDarkly having the id means it has
the bytes. Use `--no-skip-existing` to force the upload anyway. Version Lane
uploads are always re-sent, since a version says nothing about what changed.

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
on the same lane, so sources are matched to a build exactly as the mapping is.
Each file is keyed by its **declared package** plus its file name
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

## Comparing the two lanes

Retracing is the same either way, so what you are comparing is how the mapping got
found, which the upload output tells you. Run each side end to end:

```bash
# Symbols Id Lane — the app carries the id
./gradlew :app:installComposeRelease
ldcli symbols upload --type android --project default …   # → _sym/android/id/<id>/mapping.txt

# Version Lane — the app carries nothing
./gradlew :app:installComposeRelease -Pld.symbolsId=false
ldcli symbols upload --type android --project default …   # → 1.0.1/mapping.txt
```

Trigger an obfuscated error after each and the frames should resolve identically.
Rebuilding the app for each side is what makes it a clean test: a stamped app
reports an id, and the backend only falls back to the version after failing to
find anything under it.

What actually differs shows up when you build the same version twice. Change a
line in `SymbolicationDemo.kt`, rebuild, and upload again:

- **Symbols Id Lane** — the mapping changed, so its id changed, and the second
  upload lands beside the first. Errors from either build retrace correctly.
- **Version Lane** — both are `1.0.1`, so the second upload replaces the first.
  Errors still arriving from the first build now retrace against the wrong
  mapping, usually to a plausible-looking but wrong line.

A re-upload of an unchanged build shows the other half of it: on the Symbols Id
Lane it is skipped (`already uploaded`), because the id proves the bytes are the
ones already stored.

## Notes

- The mapping can be large because [`app/proguard-rules.pro`](app/proguard-rules.pro)
  keeps the SDK/OpenTelemetry packages intact (`-keep class …`) for runtime safety;
  only the app's own `com.example.androidobservability.*` classes are obfuscated,
  which is what the retrace demo exercises.
- `-keepattributes SourceFile,LineNumberTable` is required so R8 records line
  numbers; `-renamesourcefileattribute SourceFile` hides original file names (the
  backend derives them from the retraced class).
