This is a new [**React Native**](https://reactnative.dev) project, bootstrapped using [`@react-native-community/cli`](https://github.com/react-native-community/cli).

# Getting Started

> **Note**: Make sure you have completed the [Set Up Your Environment](https://reactnative.dev/docs/set-up-your-environment) guide before proceeding.

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

Then exercise the app so it reports an error to LaunchDarkly. The app sends a
`serviceVersion` (set in `src/App.tsx`, currently `1.0.2`) that must match the
`--app-version` you upload with.

## Step 2: Generate the release bundle + composed sourcemap

`run-*` doesn't leave a usable release map behind, so generate one explicitly.
Use `--minify false` and the Hermes compiler flags below: these mirror what the
release build does for Hermes (the Gradle plugin sets `minifyEnabled = !hermes`),
so the resulting bytecode — and therefore the map's column offsets — matches the
build running on the device. On macOS use the `osx-bin` Hermes compiler
(`linux64-bin` / `win64-bin` on other hosts). Run from this example's directory:

```sh
mkdir -p build

# --- Android ---
# 1. JS bundle + Metro packager sourcemap
npx react-native bundle \
  --platform android \
  --dev false \
  --minify false \
  --entry-file index.js \
  --bundle-output ./build/index.android.bundle \
  --sourcemap-output ./build/index.android.packager.map

# 2. Compile to Hermes bytecode, emitting the Hermes sourcemap (*.hbc.map)
./node_modules/react-native/sdks/hermesc/osx-bin/hermesc \
  -O -emit-binary -output-source-map \
  -out ./build/index.android.hbc \
  ./build/index.android.bundle

# 3. Compose into the final map (bytecode -> original source)
node ./node_modules/react-native/scripts/compose-source-maps.js \
  ./build/index.android.packager.map \
  ./build/index.android.hbc.map \
  -o ./build/index.android.bundle.map
```

```sh
# --- iOS ---
npx react-native bundle \
  --platform ios \
  --dev false \
  --minify false \
  --entry-file index.js \
  --bundle-output ./build/main.jsbundle \
  --sourcemap-output ./build/main.packager.map

./node_modules/react-native/sdks/hermesc/osx-bin/hermesc \
  -O -emit-binary -output-source-map \
  -out ./build/main.hbc \
  ./build/main.jsbundle

node ./node_modules/react-native/scripts/compose-source-maps.js \
  ./build/main.packager.map \
  ./build/main.hbc.map \
  -o ./build/main.jsbundle.map
```

The uploader only picks up `index.android.bundle`(`.map`) and
`main.jsbundle`(`.map`), so the intermediate `*.packager.map`, `*.hbc`, and
`*.hbc.map` files left in `./build` are ignored.

> **Shortcut (Android):** the release build in Step 1 already writes a composed
> map to `android/app/build/generated/sourcemaps/react/release/index.android.bundle.map`.
> You can upload that instead of re-running the commands above.

## Step 3: Upload the symbols

`ldcli symbols upload` sends React Native symbols over the dedicated symbol
endpoint (which accepts large bundles) and lands them where the symbolication
backend reads them.

```sh
ldcli symbols upload \
  --type react-native \
  --app-version 1.0.2 \
  --path ./build \
  --project YOUR_LAUNCHDARKLY_PROJECT_KEY
```

- `--type react-native` is **required** (the only symbol type supported today).
- `--app-version 1.0.2` must match the `serviceVersion` in `src/App.tsx`. If you
  change one, change the other or the uploaded map won't match reported errors.
- `--project` is your LaunchDarkly [project key](https://launchdarkly.com/docs/home/account/project#project-keys)
  (not the mobile key from `.env`).
- A single upload of `./build` covers both platforms — every matching bundle and
  map under `--path` is uploaded.
- Requires an `ldcli` version that supports `symbols upload`, and CLI
  [authentication](https://launchdarkly.com/docs/home/getting-started/ldcli#authentication)
  (log in or pass `--access-token`).

### Uploading against a local backend

When testing against a local observability backend, point the CLI at the
**private** endpoint (not `/public`), and pass the base URI of the LaunchDarkly
instance your access token belongs to (for example staging):

```sh
ldcli symbols upload \
  --type react-native \
  --app-version 1.0.2 \
  --path ./build \
  --project default \
  --backend-url http://localhost:8082/private \
  --base-uri https://ld-stg.launchdarkly.com \
  --access-token YOUR_ACCESS_TOKEN
```

# Troubleshooting

If you're having issues getting the above steps to work, see the [Troubleshooting](https://reactnative.dev/docs/troubleshooting) page.

# Learn More

To learn more about React Native, take a look at the following resources:

- [React Native Website](https://reactnative.dev) - learn more about React Native.
- [Getting Started](https://reactnative.dev/docs/environment-setup) - an **overview** of React Native and how setup your environment.
- [Learn the Basics](https://reactnative.dev/docs/getting-started) - a **guided tour** of the React Native **basics**.
- [Blog](https://reactnative.dev/blog) - read the latest official React Native **Blog** posts.
- [`@facebook/react-native`](https://github.com/facebook/react-native) - the Open Source; GitHub **repository** for React Native.
