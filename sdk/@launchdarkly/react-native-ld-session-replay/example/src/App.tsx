import { Text, View, StyleSheet, TextInput } from 'react-native';
import {
  ReactNativeLDClient,
  LDProvider,
  AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk';

import { useEffect, useState } from 'react';

import { createSessionReplayPlugin } from '@launchdarkly/session-replay-react-native';

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: true,
  maskImages: true,
  maskAccessibilityIdentifiers: ['password', 'ssn', 'welcome_text', 'good_bye'],
  minimumAlpha: 0.05,
});

const options = {
  plugins: [plugin],
};

// Replace with your LaunchDarkly mobile key
// You can set LAUNCHDARKLY_MOBILE_KEY as an environment variable
// or replace this placeholder directly
const MOBILE_KEY =
  process.env.LAUNCHDARKLY_MOBILE_KEY || 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE';

const client = new ReactNativeLDClient(
  MOBILE_KEY,
  AutoEnvAttributes.Enabled,
  options
);
const context = { kind: 'user', key: 'user-key-123abc' };

export default function App() {
  useEffect(() => {
    console.log('App started');
    client.identify(context).catch((e: any) => console.log(e));
  }, []);

  const [text, setText] = useState<string>('');

  return (
    <LDProvider client={client}>
      <View style={styles.container}>
        <Text>Hello World from react native</Text>
        <Text testID="welcome_text">Session Replay from native swift code</Text>
        <Text testID="good_bye">Good Bye</Text>

        <TextInput
          style={styles.input}
          placeholder="Type something..."
          value={text}
          onChangeText={(value: string) => setText(value)}
        />
        <TextInput
          style={styles.input}
          placeholder="Type something..."
          value={text}
          onChangeText={(value: string) => setText(value)}
        />
      </View>
    </LDProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 10,
    borderRadius: 5,
  },
});
