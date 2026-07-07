# session-replay-react-native

session replay for react native

## Installation

```sh
npm install session-replay-react-native
```

## Supported React Native and Expo versions

The LaunchDarkly session replay plugin for React Native targets the following
React Native and Expo versions:

| Framework | Supported versions |
| --- | --- |
| React Native | 0.75+ |
| Expo | 51+ |

Both the **New Architecture** and the **Legacy Architecture** (bridge /
`NativeModules`) are supported. The native module registers itself on whichever
architecture the host app uses, so no per-app configuration is required for the
module to load.

## Usage

Use the session replay plugin with the LaunchDarkly React Native client:

```js
import {
  ReactNativeLDClient,
  AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk';
import { createSessionReplayPlugin } from 'session-replay-react-native';

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: true,
  maskImages: true,
});

const client = new ReactNativeLDClient(
  'YOUR_LAUNCHDARKLY_MOBILE_KEY',
  AutoEnvAttributes.Enabled,
  { plugins: [plugin] }
);
```

Or use the imperative API:

```js
import {
  configureSessionReplay,
  startSessionReplay,
  stopSessionReplay,
} from 'session-replay-react-native';

await configureSessionReplay('YOUR_LAUNCHDARKLY_MOBILE_KEY', {
  isEnabled: true,
});
await startSessionReplay();
// later: await stopSessionReplay();
```

## Custom endpoints

By default the native session replay and observability instances report to
LaunchDarkly's production endpoints. To target a different environment (for
example staging or a proxy), set `otlpEndpoint` and/or `backendUrl`:

```js
const plugin = createSessionReplayPlugin({
  isEnabled: true,
  otlpEndpoint: 'https://otel.observability.app.launchdarkly.com:4318',
  backendUrl: 'https://pub.observability.app.launchdarkly.com',
});
```

`backendUrl` drives the session replay upload endpoint as well: the native
session replay plugin derives its upload URL from the shared observability
options, so setting `backendUrl` routes both observability and session replay to
the same environment. Either option falls back to its production default when
unset. Both are applied on iOS and Android.

## Capture and sampling options

These options control how often frames are captured, at what resolution, and whether
this session is selected for recording. All are forwarded to the native session replay
SDK on **iOS and Android**.

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `frameRate` | `number` | `1.0` | Target capture rate in frames per second. |
| `scale` | `number` | `1.0` | Capture/export resolution multiplier (`1.0` = 1x / 160 DPI, `2.0` = 2x, etc.). Non-positive values are treated as `1.0`. |
| `sampleRate` | `number` | `1.0` | Probability from `0.0` to `1.0` that replay starts when `isEnabled` is `true`. `0.0` never records; `1.0` always records. Evaluated once per enable cycle and reset when replay is stopped. |

```js
const plugin = createSessionReplayPlugin({
  isEnabled: true,
  frameRate: 2.0,
  scale: 1.0,
  sampleRate: 0.25, // record roughly 25% of sessions
});
```

When a session is sampled out, `isEnabled` may stay `true` but no frames, interactions,
or identify payloads are exported for that enable cycle — matching native iOS and Android
behavior.

## Service version (Expo)

The SDK does not read the app version from the native binary automatically.
`serviceVersion` defaults to `'1.0.0'` when unset. In an Expo app, use
[`expo-application`](https://docs.expo.dev/versions/latest/sdk/application/) to
read the store-visible version and pass it to both plugins:

```tsx
import * as Application from 'expo-application';
import { createSessionReplayPlugin } from '@launchdarkly/session-replay-react-native';
import { Observability } from '@launchdarkly/observability-react-native';

const serviceVersion =
  Application.nativeApplicationVersion ?? '1.0.0';

const observability = new Observability({
  serviceName: 'my-expo-app',
  serviceVersion,
});

const sessionReplay = createSessionReplayPlugin({
  isEnabled: true,
  serviceName: 'my-expo-app',
  serviceVersion,
});
```

`Application.nativeApplicationVersion` maps to `CFBundleShortVersionString` on iOS
and `versionName` on Android — the same version users see in the app store listing.

## Masking sensitive content

### How the SDK decides what to mask

For each view, the SDK evaluates these rules in order and stops at the first that applies:

1. **Explicit masking (highest priority)**: is this view — or any of its ancestors — explicitly masked (wrapped in `<LDMask>`, or `testID` matched by `maskTestIDs`)?
   - **Yes**: the view is **masked**. This overrides everything below.
2. **Explicit unmasking**: is this view — or any of its ancestors — explicitly unmasked (wrapped in `<LDUnmask>`, or `testID` matched by `unmaskTestIDs`)?
   - **Yes**: the view is **unmasked**.
3. **Global configuration**: does the global privacy config (`maskTextInputs`, `maskLabels`, `maskImages`, `maskWebViews`, `minimumAlpha`) apply to this view?
   - **Yes**: the view follows the global config.

When two rules conflict at the same level, **masking wins over unmasking**.

### Global toggles by component type

Set on the plugin config; each affects every instance of that RN component across the app.

```ts
createSessionReplayPlugin({
  maskTextInputs: true, // default — masks every <TextInput>
  maskLabels: false, // when true, masks every <Text>
  maskImages: false, // when true, masks every <Image>
  maskWebViews: false, // when true, masks every <WebView>
  minimumAlpha: 0.02, // mask views below this opacity (0.0–1.0); default 0.02
});
```

### By `testID`

Name specific views to mask or unmask. Match is exact string equality — `'password'` matches `<View testID="password" />` but not `<View testID="password_field" />`.

```ts
createSessionReplayPlugin({
  maskTestIDs: ['password', 'ssn'],
  unmaskTestIDs: ['greeting'],
});
```

### Wrapper components

Redact a subtree without giving it a `testID`.

```tsx
import { LDMask, LDUnmask } from '@launchdarkly/session-replay-react-native';

<LDMask>
  <Text>account balance: $1,234</Text>
</LDMask>;

<LDUnmask>
  <Text>safe even when maskLabels is on</Text>
</LDUnmask>;
```

`<LDMask>` propagates to descendants and beats any `<LDUnmask>` further down the tree — once a subtree is wrapped in `<LDMask>`, nothing inside it can opt back in.

## Troubleshooting

### Android: `generateCodegenArtifactsFromSchema` fails with `Cannot read properties of undefined (reading 'map')`

This crash originates inside React Native's own codegen
(`GenerateModuleJniCpp.js`), not in this package, and it indicates a **codegen
version mismatch in your app's dependency tree**: an older `@react-native/codegen`
parser produces a schema using the legacy `spec.properties` field, while the
generator from your app's React Native version reads `spec.methods`, so it
iterates `undefined`.

It is not caused by this module's spec (the spec generates cleanly on a single,
consistent codegen version). To fix it, deduplicate `@react-native/codegen` so a
single version — matching your app's React Native — is used for both parsing and
generation. For example, with Yarn:

```sh
yarn why @react-native/codegen   # find the duplicate/older copy
yarn dedupe @react-native/codegen
```

or pin a single version via a `resolutions` / `overrides` entry that matches your
React Native release, then reinstall and clean the Android build.

### iOS: `'SessionReplayReactNative-Swift.h' file not found`

This package mixes Swift and Objective-C++, so the pod must define a Clang module
for the Swift interop header to be generated. The podspec sets `DEFINES_MODULE`,
and the import is guarded with `__has_include` to work under both framework and
static-library linking. If you still hit this on an older toolchain, enable
modular headers for the pod in your `Podfile`:

```ruby
pod 'SessionReplayReactNative', :modular_headers => true
# or, app-wide:
use_frameworks! :linkage => :static
```

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
