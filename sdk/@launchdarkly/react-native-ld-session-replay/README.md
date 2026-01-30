# session-replay-react-native

session replay for react native

## Installation


```sh
npm install session-replay-react-native
```


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
