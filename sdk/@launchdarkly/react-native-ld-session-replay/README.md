# session-replay-react-native

session replay for react native

## Installation


```sh
npm install @launchdarkly/session-replay-react-native
# or
yarn add @launchdarkly/session-replay-react-native
```

### iOS setup

The current React Native (0.83), CocoaPods (1.16.x), and Xcode (26.x)
combination ships with a few build-system bugs that this package needs to
work around. We expose them as a small Podfile helper so consumers don't have
to copy-paste workaround blocks into their own Podfile.

Add the following two changes to your `ios/Podfile`:

1. At the top of the file, after the `require react_native_pods.rb` line,
   `require_relative` the helper from `node_modules` and call
   `launchdarkly_sr_pre_install` before your `target` block:

   ```ruby
   require_relative '../node_modules/@launchdarkly/session-replay-react-native/scripts/podfile_setup'

   platform :ios, min_ios_version_supported
   prepare_react_native_project!

   launchdarkly_sr_pre_install
   ```

2. Inside your `post_install` block, after `react_native_post_install`, call
   `launchdarkly_sr_post_install`:

   ```ruby
   target 'YourApp' do
     config = use_native_modules!

     use_react_native!(
       :path => config[:reactNativePath],
       :app_path => "#{Pod::Config.instance.installation_root}/.."
     )

     post_install do |installer|
       react_native_post_install(
         installer,
         config[:reactNativePath],
         :mac_catalyst_enabled => false,
       )

       launchdarkly_sr_post_install(installer)
     end
   end
   ```

Then install the pods as usual:

```sh
cd ios && pod install
```

> A complete reference Podfile lives at
> [`example/ios/Podfile`](./example/ios/Podfile).

#### What the helpers do

- `launchdarkly_sr_pre_install` declares `SwiftProtobuf` and `SocketRocket` as top-level
  pods with `:modular_headers => true`. CocoaPods 1.16.x + Xcode 26 sometimes
  fails to write the `Pods/Headers/Public/<Pod>/<Pod>.modulemap` symlinks
  for Swift-only static pods that are pulled in transitively (such as
  `SwiftProtobuf`, which `LaunchDarklyObservability` depends on), which leads
  to `Module map file '.../SwiftProtobuf.modulemap' not found` errors. We
  can't simply enable `use_modular_headers!` globally because that causes
  `React-RuntimeCore` and `React-jsitooling` (which both declare
  `module react_runtime`) to collide.
- `launchdarkly_sr_post_install(installer)` disables the Xcode 26 explicit-modules build
  path (`SWIFT_ENABLE_EXPLICIT_MODULES`, `_EXPERIMENTAL_CLANG_EXPLICIT_MODULES`,
  `CLANG_ENABLE_EXPLICIT_MODULES`) on every pod target *and* on the host
  app's targets. Without this, RN 0.83's `React-RuntimeHermes` (and other
  mixed C++/Swift pods) produce cascading
  `Could not build module 'CoreFoundation' / 'Foundation' /
  '_DarwinFoundation1'` errors. It also bumps any pods stuck on
  `IPHONEOS_DEPLOYMENT_TARGET < 15.1` up to `15.1`. The minimum can be
  customized: `launchdarkly_sr_post_install(installer, min_ios_deployment_target: '16.0')`.

These workarounds are temporary; they will be removed as React Native, Xcode,
and CocoaPods catch up to each other.

### Android setup

No additional configuration is required. Auto-linking handles everything.

## Usage

Use the session replay plugin with the LaunchDarkly React Native client:

```js
import { ReactNativeLDClient, AutoEnvAttributes } from '@launchdarkly/react-native-client-sdk';
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

await configureSessionReplay('YOUR_LAUNCHDARKLY_MOBILE_KEY', { isEnabled: true });
await startSessionReplay();
// later: await stopSessionReplay();
```


## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
