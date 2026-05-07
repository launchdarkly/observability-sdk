# session-replay-react-native

session replay for react native

## Installation

```sh
npm install session-replay-react-native
```

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

## Masking sensitive content

### How the SDK decides what to mask

For each view, the SDK evaluates these rules in order and stops at the first that applies:

1. **Explicit masking (highest priority)**: is this view — or any of its ancestors — explicitly masked (wrapped in `<LDMask>`, or `testID` matched by `maskTestIDs`)?
   - **Yes**: the view is **masked**. This overrides everything below.
2. **Explicit unmasking**: is this view — or any of its ancestors — explicitly unmasked (wrapped in `<LDUnmask>`, or `testID` matched by `unmaskTestIDs`)?
   - **Yes**: the view is **unmasked**.
3. **Global configuration**: does the global privacy config (`maskTextInputs`, `maskLabels`, `maskImages`, `maskWebViews`) apply to this view?
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

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
