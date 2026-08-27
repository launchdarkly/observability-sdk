# HowdySkip

This is a [Skip](https://skip.dev) dual-platform sample app: from a single Swift
and SwiftUI codebase it builds a native app for both iOS and Android. `HowdySkip`
has a single module that is **compiled natively** for Android with the Swift
toolchain rather than transpiled ([Skip Fuse](https://skip.dev/docs/modes/)
mode) — the native counterpart to the fully-transpiled
[skipapp-hello](https://github.com/skiptools/skipapp-hello).

It is one of four Skip sample apps that share the same
[conventional Skip app project layout](https://skip.dev/docs/project-types/#samples)
but differ in their module structure and Skip mode, as shown below.

## The sample apps

| Sample | Modules | Skip mode |
| --- | --- | --- |
| [skipapp-hello](https://github.com/skiptools/skipapp-hello) | `HelloSkip` | fully transpiled — Skip Lite |
| [skipapp-howdy](https://github.com/skiptools/skipapp-howdy) | `HowdySkip` | fully native — Skip Fuse |
| [skipapp-ahoy](https://github.com/skiptools/skipapp-ahoy) | `AhoySkipper`, `SkipperModel` | fully native — Skip Fuse |
| [skipapp-hiya](https://github.com/skiptools/skipapp-hiya) | `HiyaSkip`, `HiyaSkipModel`, `HiyaSkipLogic` | mixed — native model bridged to a transpiled UI |

In **transpiled** ("Skip Lite") modules, Swift is converted to Kotlin and
SwiftUI to Jetpack Compose. In **native** ("Skip Fuse") modules, Swift is
compiled directly for Android with the Swift toolchain and bridged to
Kotlin/Jetpack Compose; see [Native and Transpiled Modes](https://skip.dev/docs/modes/)
for the distinction. `skipapp-hello`, `skipapp-ahoy`, and `skipapp-hiya` include
unit tests that run on both platforms; `skipapp-howdy` omits them.

## Re-creating this project

This repository is exactly what `skip init` produces — its CI verifies that it
stays identical to the generated template — so it can be re-created with:

```
skip init --no-build --native-app --appid=howdy.skip --version 1.0.0 skipapp-howdy HowdySkip
```

## Building

This project is both a stand-alone Swift Package Manager package and an Xcode
project that builds the iOS app and, using the skipstone plugin, generates and
builds the equivalent Kotlin Gradle project for Android.

## Running

Xcode and Android Studio must both be installed to run the app in the iOS
simulator and the Android emulator. Start an Android emulator first (for example,
from Android Studio's Device Manager).

Open `Project.xcworkspace` in Xcode and run the "HowdySkip App" scheme. A build
phase runs the "Launch Android APK" script, which deploys the app to a running
Android emulator or connected device alongside the iOS build. iOS logs appear in
the Xcode console; Android logs appear in Android Studio's Logcat tab (or via
`adb logcat`).

The same thing from the command line:

```bash
xcodebuild -workspace Project.xcworkspace -scheme "HowdySkip App" \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

To iterate on the Android side alone, once that has generated the Gradle project:

```bash
gradle -p Android launchDebug      # or assembleDebug to skip deployment
```

Two Android gotchas worth knowing: the build fails if no emulator or device is attached,
and a long-lived emulator can run out of internal storage (`Requested internal only, but
not enough space`), which `emulator -avd <name> -wipe-data` clears.

## LaunchDarkly

This checkout adds thin dual-platform wrappers around the official LaunchDarkly mobile
SDKs and their observability plugins. Shared SwiftUI code only ever calls
`LaunchDarklyFlags` (flag evaluation) and `LaunchDarklyObserve` (OpenTelemetry signals);
the platform SDKs are reached from `#if os(Android)` / `#if SKIP` blocks inside those two
files.

### Dependencies per platform

| | Flags | Observability |
| --- | --- | --- |
| iOS | `LaunchDarkly` from [ios-client-sdk](https://github.com/launchdarkly/ios-client-sdk) | `LaunchDarklyOtel` from [swift-launchdarkly-observability](https://github.com/launchdarkly/swift-launchdarkly-observability) |
| Android | `com.launchdarkly:launchdarkly-android-client-sdk` | `com.launchdarkly:launchdarkly-observability-android` |

The two platforms deliberately use different observability products:

- **iOS registers `Otel`**, the OTel-only product. It installs no automatic
  instrumentation — no URLSession or UIKit swizzling, no crash reporting, no periodic
  memory/CPU metrics — so every signal the app reports comes from an explicit
  `LaunchDarklyObserve` call. This is the product to use alongside another observability
  SDK.
- **Android registers `Observability`** with its default automatic instrumentation, so
  `app_launch`, `app_foreground`/`app_background`, `screen_view`, and `click` spans show
  up in addition to the manual signals.

Android dependencies are declared in `Sources/HowdySkip/Skip/skip.yml`. That file also
adds `io.opentelemetry:opentelemetry-api`, which the observability AAR only pulls in at
runtime but the bridging code needs at compile time because it holds `Span` instances.
The sample pins Android client SDK `5.15.0` and observability `0.65.1`. Both platforms
wrap the forwarding hook in their SDK-provided `DedupingHook`; the sample does not
maintain its own exposure cache.

`LaunchDarklyOtel` is not published yet, so iOS builds it from a checkout of
swift-launchdarkly-observability. `Package.swift` expects it at
`~/flutter/swift-launchdarkly-observability`; export `LD_SWIFT_OBSERVABILITY_PATH` to
point somewhere else. An absolute path is required because Skip copies this manifest into
the Swift package it generates for Android.

### Configuration

Everything demo-specific lives in `Sources/HowdySkip/LaunchDarklyConfig.swift`: the mobile
key, the context key, the service name/version reported to observability, and the flag
keys read on the Welcome tab. Only `feature3` needs to exist — the other four keys
demonstrate typed evaluation and fall back to the defaults passed at the call site when
they are missing.

Note that mobile keys are environment-specific: a staging key only works against the
staging service endpoints, so this sample uses a production key with the SDKs' default
endpoints.

### What the Welcome tab does

- **Flags:** one row per type — `boolVariation`, `intVariation`, `doubleVariation`,
  `stringVariation`, and `jsonVariation`. JSON flags are exchanged as native dictionaries
  (`[String: Any]`); on Android they cross the Skip bridge as JSON text, so values must be
  JSON-representable. An `ExposureHook` runs after every per-key variation and records a
  short `flag_exposure` span through `LaunchDarklyObserve.recordSpan` on iOS or the
  equivalent `LDObserve.startSpan`/`end` calls on Android. Identical flag key, variation,
  and value results are emitted once per ten-minute window; the cache resets after
  identify. The **Evaluate twice** button demonstrates that the hook deduplication does
  not change the variation results or LaunchDarkly's own evaluation counts.
- **Observability:** one button per `LDObserve` call — `recordLog`, `recordError`,
  `recordSpan`, `recordMetric`, `recordCount`, `recordIncr`, `recordHistogram`,
  `recordUpDownCounter`, and `trackScreenView`, plus `LDClient.track` (which the plugin
  turns into a `track` span through its `afterTrack` hook). **Track app-error** mirrors
  the guarded-rollout example: `track("app-error", data: ["screen": "checkout",
  "error_type": "network"])` followed by `flush()` so the event is not left in the
  30-second buffer. **Identify new context** calls `LDClient.identify` with a new context
  key, so subsequent evaluations and events use it and the SDK dedup cache resets.
  Property dictionaries are native too.

Both plugins run with debug logging enabled, so signals are printed as they are exported:
`adb logcat` on Android, the Xcode console on iOS.

## Contributing

We welcome contributions to this package in the form of enhancements and bug fixes.

The general flow for contributing to this and any other Skip package is:

1. Fork this repository and enable actions from the "Actions" tab
2. Check out your fork locally
3. When developing alongside a Skip app, add the package to a [shared workspace](https://skip.dev/docs/contributing) to see your changes incorporated in the app
4. Push your changes to your fork and ensure the CI checks all pass in the Actions tab
5. Add your name to the Skip [Contributor Agreement](https://github.com/skiptools/clabot-config)
6. Open a Pull Request from your fork with a description of your changes
