This is a new [**React Native**](https://reactnative.dev) project, bootstrapped using [`@react-native-community/cli`](https://github.com/react-native-community/cli).

# Getting Started

> **Note**: Make sure you have completed the [Set Up Your Environment](https://reactnative.dev/docs/set-up-your-environment) guide before proceeding.

## Step 0: Configure LaunchDarkly Mobile Key

Before running the app, you need to configure your LaunchDarkly mobile key. You can do this in one of two ways:

1. **Using environment variables (recommended)**: Copy `.env.example` to `.env` and replace `YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE` with your actual mobile key:
   ```sh
   cp .env.example .env
   # Then edit .env and add your LaunchDarkly mobile key
   ```

2. **Direct replacement**: Edit `src/App.tsx` and replace `YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE` with your actual mobile key.

> **Note**: Never commit your actual API keys to version control. The `.env` file is already included in `.gitignore`.

## Step 1: Start Metro

First, you will need to run **Metro**, the JavaScript build tool for React Native.

To start the Metro dev server, run the following command from the root of your React Native project:

```sh
# Using npm
npm start

# OR using Yarn
yarn start
```

## Step 2: Build and run your app

With Metro running, open a new terminal window/pane from the root of your React Native project, and use one of the following commands to build and run your Android or iOS app:

### Android

```sh
# Using npm
npm run android

# OR using Yarn
yarn android
```

### iOS

For iOS, remember to install CocoaPods dependencies (this only needs to be run on first clone or after updating native deps).

The first time you create a new project, run the Ruby bundler to install CocoaPods itself:

```sh
bundle install
```

Then, and every time you update your native dependencies, run:

```sh
bundle exec pod install
```

For more information, please visit [CocoaPods Getting Started guide](https://guides.cocoapods.org/using/getting-started.html).

```sh
# Using npm
npm run ios

# OR using Yarn
yarn ios
```

If everything is set up correctly, you should see your new app running in the Android Emulator, iOS Simulator, or your connected device.

This is one way to run your app — you can also build it directly from Android Studio or Xcode.

## Step 3: Modify your app

Now that you have successfully run the app, let's make changes!

Open `App.tsx` in your text editor of choice and make some changes. When you save, your app will automatically update and reflect these changes — this is powered by [Fast Refresh](https://reactnative.dev/docs/fast-refresh).

When you want to forcefully reload, for example to reset the state of your app, you can perform a full reload:

- **Android**: Press the <kbd>R</kbd> key twice or select **"Reload"** from the **Dev Menu**, accessed via <kbd>Ctrl</kbd> + <kbd>M</kbd> (Windows/Linux) or <kbd>Cmd ⌘</kbd> + <kbd>M</kbd> (macOS).
- **iOS**: Press <kbd>R</kbd> in iOS Simulator.

## Congratulations! :tada:

You've successfully run and modified your React Native App. :partying_face:

### Now what?

- If you want to add this new React Native code to an existing application, check out the [Integration guide](https://reactnative.dev/docs/integration-with-existing-apps).
- If you're curious to learn more about React Native, check out the [docs](https://reactnative.dev/docs/getting-started).

# Uploading React Native symbols for error monitoring

To get readable stack traces for JavaScript errors in the LaunchDarkly UI, upload
this app's React Native symbols (the release bundle plus its sourcemap) with the
[LaunchDarkly CLI](https://launchdarkly.com/docs/home/getting-started/ldcli).

> **This app runs on Hermes** (`hermesEnabled=true`). In release mode the app
> executes Hermes **bytecode**, so its stack frames are bytecode offsets (line 1,
> a large column). The map you upload must be the **composed** sourcemap
> (Metro packager map + Hermes map) — the plain `--sourcemap-output` map alone
> will not resolve release stack traces.

## This example uses the Symbols Id Lane

The backend can match an uploaded map to an error two ways:

- **Symbols Id Lane (what this example uses, recommended).** A Metro plugin
  stamps each bundle with a deterministic symbols id (reported as
  `launchdarkly.symbols_id.htlhash`). The SDK reports that id on every error,
  and `ldcli` uploads the map keyed by the same id, so maps match by content —
  independent of filename or app version. It's already wired up in
  `metro.config.js`:

  ```js
  const {
    withLaunchDarklySymbolsId,
  } = require('@launchdarkly/observability-react-native/metro');
  // ...
  module.exports = withLaunchDarklySymbolsId(config);
  ```

  The plugin reserves a fixed-length placeholder in the bundle, computes the id
  from the composed map, then overwrites the placeholder **in place** (same
  length, so Hermes byte offsets — and the map — stay valid). At runtime the SDK
  reads the injected `globalThis.__LD_SYMBOLS_ID__` and reports it on every error;
  no app code changes are required.

- **Version Lane — basename + app version (automatic fallback).** If no symbols
  id is present (plugin disabled, or an older app build), the backend falls back
  to matching by bundle basename (`main.jsbundle.map` / `index.android.bundle.map`)
  and `--app-version`. The two lanes coexist safely.

## Step 1: Build and run in release mode

Symbolication only matters for release builds: in dev mode Metro serves the JS
and stack traces are already readable. Build and launch the **release** variant
so the errors it reports come from the same Hermes bytecode you generate a map
for below.

```sh
# Android (release)
npx react-native run-android --mode release

# iOS (release)
npx react-native run-ios --mode Release
```

Then exercise the app (the **API** tab → **Record error**) so it reports an
error to LaunchDarkly.

## Step 2: The composed sourcemap + symbols id are generated by the release build

This example is wired so the **release build itself** produces the composed
(Metro + Hermes) map from the exact bundle it ships, drops it in `./build`, and
(via the Metro plugin) writes a `<map>.symbolsid` sidecar next to it. Do **not**
hand-build a separate bundle with `react-native bundle` + `hermesc` +
`compose-source-maps.js`: that produces a *different* bundle than the one Xcode /
Gradle compile and embed, so its line/column grid won't match the running app
and symbolication fails with `error extracting true error info from source map`.

- **iOS.** `ios/.xcode.env` exports `SOURCEMAP_FILE=$PODS_ROOT/../../build/main.jsbundle.map`,
  so `react-native-xcode.sh` writes the composed map to `./build/main.jsbundle.map`
  on every Release build (Debug builds run Metro and skip bundling).
- **Android.** The RN Gradle plugin already emits a composed map on release
  builds; `android/app/build.gradle` adds a `copyReleaseSourcemapToBuild` task
  that copies it to `./build/index.android.bundle.map`.

When Metro runs through the symbols-id plugin it prints the id and writes a
sidecar, e.g.:

```
[LaunchDarkly] bundle symbols id 1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d
[LaunchDarkly] wrote symbols id sidecar ./index.android.bundle.map.symbolsid
```

So after the Step 1 release build, everything you need (bundle, composed map,
and `*.symbolsid` sidecar) is already in `./build`. Nothing else to run — skip to
Step 3.

> **Why the bundle names differ per platform.** These are React Native's own
> default output names, and they're intentionally asymmetric — the map filename
> must match what the running app reports in its stack frames, so we follow the
> RN defaults exactly:
>
> - **Android → `index.android.bundle`.** The Gradle plugin's `bundleAssetName`
>   defaults to `index.android.bundle` (it ships in the APK's shared `assets/`
>   folder, so the name carries the platform). See the
>   [React Native Gradle Plugin docs](https://reactnative.dev/docs/react-native-gradle-plugin#bundleassetname).
> - **iOS → `main.jsbundle`.** The iOS build script defaults `BUNDLE_NAME` to
>   `main`, producing `main.jsbundle` (and `main.jsbundle.map`). See
>   [`react-native-xcode.sh`](https://github.com/facebook/react-native/blob/main/packages/react-native/scripts/react-native-xcode.sh).

## Step 3: Upload the symbols

`ldcli symbols upload` sends React Native symbols over the dedicated symbol
endpoint (which accepts large bundles) and lands them where the symbolication
backend reads them. `ldcli` auto-reads the `*.symbolsid` sidecar next to each map,
so the map is keyed by symbols id (the Symbols Id Lane) and no `--app-version` is
required:

```sh
ldcli symbols upload \
  --type react-native \
  --path ./build \
  --project YOUR_LAUNCHDARKLY_PROJECT_KEY
  # or, without a sidecar: --symbols-id 1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d
```

- `--type react-native` is **required** (the only symbol type supported today).
- `--project` is your LaunchDarkly [project key](https://launchdarkly.com/docs/home/account/project#project-keys)
  (not the mobile key from `.env`).
- A single upload of `./build` covers both platforms — every matching bundle,
  map, and sidecar under `--path` is uploaded.
- Requires an `ldcli` version that supports `symbols upload`, and CLI
  [authentication](https://launchdarkly.com/docs/home/getting-started/ldcli#authentication)
  (log in or pass `--access-token`).

> **Version Lane fallback.** If you disable the Metro plugin (no `*.symbolsid`
> sidecar), add `--app-version <version>` matching `SERVICE_VERSION` in
> `src/serviceVersion.ts` so the backend can match by basename + version instead.

### Uploading against a local backend

When testing against a local observability backend, point the CLI at the
**private** endpoint (not `/public`), and pass the base URI of the LaunchDarkly
instance your access token belongs to (for example staging):

```sh
ldcli symbols upload \
  --type react-native \
  --path ./build \
  --project default \
  --backend-url http://localhost:8082/private \
  --base-uri https://ld-stg.launchdarkly.com \
  --access-token YOUR_ACCESS_TOKEN
```

> **Android emulator + localhost.** An Android emulator can't reach your host
> through `localhost` (that's the emulator itself); use `10.0.2.2` for the app's
> observability endpoints. The bundled `network_security_config.xml` already
> allows cleartext HTTP to `10.0.2.2` / `localhost` so release builds can talk to
> a local backend.

# Troubleshooting

If you're having issues getting the above steps to work, see the [Troubleshooting](https://reactnative.dev/docs/troubleshooting) page.

# Learn More

To learn more about React Native, take a look at the following resources:

- [React Native Website](https://reactnative.dev) - learn more about React Native.
- [Getting Started](https://reactnative.dev/docs/environment-setup) - an **overview** of React Native and how setup your environment.
- [Learn the Basics](https://reactnative.dev/docs/getting-started) - a **guided tour** of the React Native **basics**.
- [Blog](https://reactnative.dev/blog) - read the latest official React Native **Blog** posts.
- [`@facebook/react-native`](https://github.com/facebook/react-native) - the Open Source; GitHub **repository** for React Native.
