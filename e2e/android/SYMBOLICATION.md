# Android Symbolication — uploading `mapping.txt` for this example

R8 obfuscates release builds, so a recorded error's stack trace looks like
`at g5.g.invoke(r8-map-id-92d0222…:57)`. Uploading the `mapping.txt` R8 produced lets the
backend retrace those frames back to the original class, method and line.

There are two ways to say *which build* a mapping belongs to, and this app can
build either way so you can compare them:

| | keyed by | build setup | stored at |
|---|---|---|---|
| **Symbols Id Lane** (default) | the id R8 gave the mapping | none | `_sym/android/id/<id>/mapping.v1.index` |
| **Version Lane** (add `-renamesourcefileattribute`) | the app's version | none | `<version>/mapping.v1.index` |

Neither needs anything on the `ldcli` command line: it finds the mapping where R8
wrote it, and reads the id and the version out of the build.

## How the Symbols Id Lane works

1. **Build** — R8 emits `app/build/outputs/mapping/<variant>/mapping.txt`, records
   its own id for it in the header (`# pg_map_id: <sha-256 of the mapping>`), and —
   since AGP 8.12, unless the build sets `-renamesourcefileattribute` — stamps that
   same id into every class as its source file: `r8-map-id-<id>`.

   Nothing in [`app/build.gradle.kts`](app/build.gradle.kts) takes part. The id is a
   hash of the *mapping*, so R8 putting it into the classes it just wrote the mapping
   for changes nothing about the mapping — no self-reference.

2. **Runtime** — every frame of a crash carries the id where a file name goes, since
   that is what `StackTraceElement.getFileName()` returns:
   `at g5.g.invoke(r8-map-id-92d0222…:57)`.

3. **Upload** — `ldcli symbols upload --type android` reads `pg_map_id` out of the
   mapping header, so the index is keyed by exactly what a crash will report.

4. **Symbolication** — the backend takes the id off the frame, loads the index stored
   under it, and retraces each frame (expanding inlined frames).

A build can also stamp an id into `assets/ld_symbols_id.txt` for the SDK to report as
`launchdarkly.symbols_id.htlhash`, which is how this app used to do it and how a
project on AGP older than 8.12 still can. `ldcli` prefers that id when the packaged
app carries one; the backend prefers the one on the frame. This app carries none.

## How the Version Lane works

The fallback when a crash reports no id: the SDK reports the app's version as
`service.version`, the upload stores the index at `<version>/mapping.v1.index`, and
the backend looks it up by the version on the error.

> **The app has to report its own version.** `ObservabilityOptions.serviceVersion`
> defaults to the *SDK's* version, which describes no build of your app, so a
> mapping uploaded for `1.0.1` would never be matched. This app sets
> `serviceVersion = BuildConfig.VERSION_NAME` in
> [`BaseApplication.kt`](app/src/compose/java/com/example/androidobservability/BaseApplication.kt).

The newest upload for a version wins. That is fine while a version is one build, and
wrong as soon as it isn't: rebuild `1.0.1` after changing code and the second mapping
replaces the first, so errors still arriving from the first build retrace to the
wrong lines. An id is derived from the mapping's own contents, so two builds are
never the same id and neither can overwrite the other.

Both lanes can hold a mapping at once, and the backend tries the id first, so a build
that starts reporting one is not a cutover.

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

The id R8 gave the mapping is the first thing in it:

```bash
head -7 app/build/outputs/mapping/composeRelease/mapping.txt | grep pg_map_id
# pg_map_id: 92d0222f1a7a3b92fca00ddc75fbcf893c89be03e02286414d51abcfd9b02063
```

To build without one and exercise the Version Lane instead, add
`-renamesourcefileattribute SourceFile` to [`app/proguard-rules.pro`](app/proguard-rules.pro).
That overwrites the marker R8 would have stamped, so the app reports `SourceFile` on
every frame, as it did before AGP 8.12.

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

It reports which id it keyed by, and stores the index on both lanes:

```
Found the composeRelease mapping at app/build/outputs/mapping/composeRelease/mapping.txt
Using app version 1.0.1, as packaged for composeRelease
Using symbols id 92d0222f1a7a3b92fca00ddc75fbcf893c89be03e02286414d51abcfd9b02063, as recorded by R8 in the mapping
Indexed mapping.txt (61.2 MB of mapping into a 5.2 MB index)
[LaunchDarkly] Uploaded mapping.v1.index (Symbols Id Lane)
[LaunchDarkly] Uploaded mapping.v1.index (Version Lane)
```

A mapping always records an id, so the id lane copy is written whether or not the app
will report one. With `-renamesourcefileattribute` in place the output looks the same
and nothing asks for that copy; the backend finds the build under `1.0.1` instead.

It looks for `<module>/build/outputs/mapping/<variant>/mapping.txt`, which is where
R8 writes it; for `assets/ld_symbols_id.txt` inside the packaged APK, in case the
build stamped an id of its own; and for the version in the `output-metadata.json` AGP
writes beside that APK. Build more than one obfuscated variant and it names them and
asks for `--path`, rather than guessing which one you shipped.

Any of it can still be given explicitly: `--path` to point at a mapping somewhere
else, `--symbols-id <id>`, or `--app-version <version>`.

Re-running an upload for a build already uploaded skips it: an id is derived from the
mapping's contents, so LaunchDarkly having the id means it has the bytes. Use
`--no-skip-existing` to force the upload anyway. Version Lane uploads are always
re-sent, since a version says nothing about what changed.

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

The files are packed into a `sources.srcbundle` uploaded beside the index on the same
lane, so sources are matched to a build exactly as the mapping is.
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
# Symbols Id Lane — every frame reports the mapping's id
./gradlew :app:installComposeRelease
ldcli symbols upload --type android --project default …   # → _sym/android/id/<id>/…

# Version Lane — add -renamesourcefileattribute SourceFile to app/proguard-rules.pro
./gradlew :app:installComposeRelease
ldcli symbols upload --type android --project default …   # → also 1.0.1/…
```

Trigger an obfuscated error after each and the frames should resolve identically.
Rebuilding the app for each side is what makes it a clean test: what changes is what
the app reports, and the backend only falls back to the version after failing to find
anything under an id.

To see which one you built, ask the APK what it reports:

```bash
unzip -p app/build/outputs/apk/compose/release/app-compose-release.apk classes.dex \
  | strings | grep -m1 r8-map-id
```

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
  numbers. There is deliberately no `-renamesourcefileattribute`: R8 fills that
  attribute with the mapping's id, which hides the original file names just as well
  and is what puts a build on the Symbols Id Lane. The backend derives a file name to
  display from the retraced class either way.
