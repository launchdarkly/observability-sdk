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

# Uploading sourcemaps for error monitoring

To get readable stack traces for JavaScript errors in the LaunchDarkly UI, upload
this app's sourcemaps with the [LaunchDarkly CLI](https://launchdarkly.com/docs/home/getting-started/ldcli-commands#use-ldcli-for-uploading-sourcemaps).

## Step 1: Generate the JS bundle + sourcemap

React Native doesn't emit a sourcemap during `run-ios` / `run-android`, so generate
one explicitly. Run from this example's directory:

```sh
mkdir -p build

# iOS
npx react-native bundle \
  --platform ios \
  --dev false \
  --entry-file index.js \
  --bundle-output ./build/main.jsbundle \
  --sourcemap-output ./build/main.jsbundle.map

# Android
npx react-native bundle \
  --platform android \
  --dev false \
  --entry-file index.js \
  --bundle-output ./build/index.android.bundle \
  --sourcemap-output ./build/index.android.bundle.map
```

## Step 2: Upload the sourcemap(s)

```sh
ldcli sourcemaps upload \
  --app-version 1.0.0 \
  --path ./build \
  --base-path ./ \
  --project YOUR_LAUNCHDARKLY_PROJECT_KEY
```

- `--app-version 1.0.0` must match the `serviceVersion` configured in `src/App.tsx`.
  If you change one, change the other or the uploaded map won't match reported errors.
- `--project` is your LaunchDarkly [project key](https://launchdarkly.com/docs/home/account/project#project-keys)
  (not the mobile key from `.env`).
- `ldcli sourcemaps upload` uploads every sourcemap under `--path`, so a single
  upload of `./build` covers both platforms. Requires an `ldcli` version that
  supports React Native bundles (`.jsbundle` / `.bundle`).
- Uploading requires CLI [authentication](https://launchdarkly.com/docs/home/getting-started/ldcli#authentication)
  (log in or pass an access token).

# Troubleshooting

If you're having issues getting the above steps to work, see the [Troubleshooting](https://reactnative.dev/docs/troubleshooting) page.

# Learn More

To learn more about React Native, take a look at the following resources:

- [React Native Website](https://reactnative.dev) - learn more about React Native.
- [Getting Started](https://reactnative.dev/docs/environment-setup) - an **overview** of React Native and how setup your environment.
- [Learn the Basics](https://reactnative.dev/docs/getting-started) - a **guided tour** of the React Native **basics**.
- [Blog](https://reactnative.dev/blog) - read the latest official React Native **Blog** posts.
- [`@facebook/react-native`](https://github.com/facebook/react-native) - the Open Source; GitHub **repository** for React Native.
