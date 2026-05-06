import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  ReactNativeLDClient,
  LDProvider,
  AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk';
import { useEffect, useState } from 'react';
import { createSessionReplayPlugin } from '@launchdarkly/session-replay-react-native';
import DialogsScreen from './DialogsScreen';
import MaskingScreen from './MaskingScreen';

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: true,
  maskImages: true,
  maskTestIDs: ['password', 'ssn'],
  unmaskTestIDs: ['safe'],
  minimumAlpha: 0.05,
});

// Replace with your LaunchDarkly mobile key.
// You can also set the LAUNCHDARKLY_MOBILE_KEY environment variable.
const MOBILE_KEY =
  process.env.LAUNCHDARKLY_MOBILE_KEY || 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE';

const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
  plugins: [plugin],
});
const context = { kind: 'user', key: 'user-key-123abc' };

type Tab = 'masking' | 'dialogs';

export default function App() {
  const [tab, setTab] = useState<Tab>('masking');

  useEffect(() => {
    client.identify(context).catch((e: unknown) => console.log(e));
  }, []);

  return (
    <LDProvider client={client}>
      <SafeAreaView style={{ flex: 1, backgroundColor: '#000' }}>
        <View style={styles.tabBar}>
          <TabButton
            label="Masking"
            active={tab === 'masking'}
            onPress={() => setTab('masking')}
          />
          <TabButton
            label="Dialogs"
            active={tab === 'dialogs'}
            onPress={() => setTab('dialogs')}
          />
        </View>
        {tab === 'masking' ? <MaskingScreen /> : <DialogsScreen />}
      </SafeAreaView>
    </LDProvider>
  );
}

function TabButton({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      // testID="safe" so the tab labels stay visible regardless of maskLabels —
      // they're navigation chrome, not content under test.
      testID="safe"
      style={[styles.tab, active ? styles.tabActive : undefined]}
      onPress={onPress}
    >
      <Text testID="safe" style={styles.tabText}>
        {label}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#333',
  },
  tab: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
  },
  tabActive: {
    borderBottomWidth: 2,
    borderBottomColor: '#6650A4',
  },
  tabText: {
    color: '#fff',
    fontWeight: '600',
  },
});
