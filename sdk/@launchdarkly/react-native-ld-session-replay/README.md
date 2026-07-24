# session-replay-react-native

session replay for react native

## Installation

```sh
npm install session-replay-react-native
```

## Supported React Native versions

The LaunchDarkly session replay plugin for React Native targets React Native
`0.75+`. Both the **New Architecture** and the **Legacy Architecture** (bridge /
`NativeModules`) are supported. The native module registers itself on whichever
architecture the host app uses, so no per-app configuration is required for the
module to load.

Expo (SDK `51+`) is supported as well — see [Expo](#expo) for setup and version
reporting.

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

## Automatically captured lifecycle events

Registering the plugin initializes the native session replay / observability layer,
which auto-instruments **app lifecycle** on both iOS and Android. No extra wiring is
required — these spans are emitted alongside your other telemetry:

| Span | When | Notes |
| --- | --- | --- |
| `app_launch` | The native app **process** launches. | Carries `event.launch_type` (`relaunch` / `install` / `update`). This is a native process launch, distinct from a JS / OTA reload (see `app_reload` below). |
| `app_foreground` | The app enters the foreground (including resume from background). | `event.lifecycle_state = foreground`. |
| `app_background` | The app enters the background. | `event.lifecycle_state = background`. |

In addition, when the JavaScript runtime reloads **while the native process stays
alive** (a React Native soft reload or an OTA bundle reload) and the previous session
is still within the resume window, the observability SDK emits an `app_reload` span so
the session stays stitched together across the reload. A full cold start (the process
was terminated and relaunched, i.e. `app_launch` above) always begins a **new** session
rather than resuming the previous one.

See the [event taxonomy](../../../analytics-taxonomy.md) for the full `event.*` payload
of each event.

## Expo

Expo SDK `51+` is supported. Session replay is a **native module**, so it does
not run in **Expo Go** — use an
[Expo development build](https://docs.expo.dev/develop/development-builds/introduction/)
(`expo-dev-client`) or a standalone build from EAS Build / `expo prebuild`.

### Passing version information

The SDK does not read any version from the Expo runtime automatically — it only
uses the `serviceVersion` string you pass, which defaults to `'1.0.0'` when unset.
To attribute telemetry to a specific build, read the version from Expo and pass it
into the plugins yourself.

Read the store-visible app version with
[`expo-application`](https://docs.expo.dev/versions/latest/sdk/application/) and
set it as `serviceVersion` on both plugins:

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

### Over-the-air (OTA) updates

If you ship over-the-air updates with
[Expo Updates](https://docs.expo.dev/versions/latest/sdk/updates/), the native app
version stays fixed across updates, so pass the update identifiers as extra
`resourceAttributes` on the observability plugin to tell builds apart:

```tsx
import * as Application from 'expo-application';
import * as Updates from 'expo-updates';

const observability = new Observability({
  serviceName: 'my-expo-app',
  serviceVersion: Application.nativeApplicationVersion ?? '1.0.0',
  resourceAttributes: {
    'ota.update_id': Updates.updateId ?? 'embedded',
    'ota.runtime_version': Updates.runtimeVersion ?? undefined,
  },
});
```

`Updates.updateId` identifies the running OTA bundle (`null` — shown here as
`'embedded'` — when the app runs the bundle shipped with the binary), and
`Updates.runtimeVersion` is the native-compatibility gate for OTA delivery.

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

## Identifying tapped elements

Taps are captured natively and reported as `click` events. To reliably identify an element in
product analytics — regardless of layout, visible text, or A/B copy — give it a stable id.

Wrap it in `<LDClick>`:

```tsx
import { LDClick } from '@launchdarkly/session-replay-react-native';

<LDClick id="checkout.pay_button">
  <Button title="Pay" onPress={pay} />
</LDClick>;
```

`<LDClick>` reports the given `id` as `event.id` on the `click` event. A tap on any descendant
resolves to the nearest enclosing `<LDClick>` id, so wrapping a composite control tags the whole
thing. It also survives React Native view flattening and works with components that don't otherwise
expose a native view.

For a single element such as a `Button`, you can skip the wrapper and set React Native's built-in
`nativeID` prop directly:

```tsx
<Button title="Pay" nativeID="checkout.pay_button" onPress={pay} />
```

`nativeID` is carried to the native view by React Native itself (no extra setup), and the native SDK
reads it as `event.id`. Use `<LDClick>` when you instead want to tag a whole subtree, an element that
may be flattened away, or a component that doesn't forward `nativeID`.

The click id is a dedicated channel: unlike `testID`, it is not overloaded with e2e testing and is
never stripped by session-replay privacy masking. When an element also has a `testID`, the click id
(`<LDClick>` or `nativeID`) takes precedence for `event.id`.

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
