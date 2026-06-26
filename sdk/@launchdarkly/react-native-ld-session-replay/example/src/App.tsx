import {
  Platform,
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
import { Observability } from '@launchdarkly/observability-react-native';
import DialogsScreen from './DialogsScreen';
import MaskingScreen from './MaskingScreen';
import TracingScreen from './TracingScreen';
import ApiScreen from './ApiScreen';

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  // Forwarded to the native observability + session replay instances so their
  // spans report the same service.name / service.version as the JS observability
  // plugin below. serviceVersion only affects observability-emitted signals.
  serviceName: 'session-replay-rn-example',
  serviceVersion: '1.0.0',
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: false,
  maskImages: false,
  maskTestIDs: ['password', 'ssn'],
  unmaskTestIDs: ['safe'],
  minimumAlpha: 0.05,
});

// The observability plugin powers the distributed tracing examples on the
// "Tracing" tab. `tracingOrigins` opts the demo API hosts into W3C
// `traceparent` / `baggage` header propagation so device spans can link to a
// backend trace (see the tracing guide, sections 11 and 12).
const observability = new Observability({
  serviceName: 'session-replay-rn-example',
  serviceVersion: '1.0.0',
  debug: true,
  tracingOrigins: ['jsonplaceholder.typicode.com', 'reactnative.dev'],
});

// Replace with your LaunchDarkly mobile key.
// You can also set the LAUNCHDARKLY_MOBILE_KEY environment variable.
const MOBILE_KEY =
  process.env.LAUNCHDARKLY_MOBILE_KEY || 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE';

// Observability must come before the session replay plugin: the replay plugin
// reads the observability session id during registration so the native replay /
// observability instance can adopt it (shared `session.id` across JS + native).
const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
  plugins: [observability, plugin],
});
const context = { kind: 'user', key: 'user-key-123abc' };

// The New Architecture installs the Fabric UIManager on the JS global; its
// absence means the app is running on the legacy bridge architecture.
const IS_NEW_ARCH =
  (global as { nativeFabricUIManager?: unknown }).nativeFabricUIManager != null;
const RN_VERSION = (() => {
  const v = Platform.constants.reactNativeVersion;
  return `${v.major}.${v.minor}.${v.patch}`;
})();
const ARCH_LABEL = `${
  IS_NEW_ARCH ? 'New' : 'Legacy'
} Architecture · React Native ${RN_VERSION}`;

type Tab = 'masking' | 'dialogs' | 'api' | 'tracing';

export default function App() {
  const [tab, setTab] = useState<Tab>('masking');

  useEffect(() => {
    client.identify(context).catch((e: unknown) => console.log(e));
  }, []);

  return (
    <LDProvider client={client}>
      <SafeAreaView style={{ flex: 1, backgroundColor: '#000' }}>
        <View
          testID="safe"
          style={[
            styles.archBanner,
            IS_NEW_ARCH ? styles.archBannerNew : styles.archBannerLegacy,
          ]}
        >
          <Text testID="safe" style={styles.archBannerText}>
            {ARCH_LABEL}
          </Text>
        </View>
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
          <TabButton
            label="API"
            active={tab === 'api'}
            onPress={() => setTab('api')}
          />
          <TabButton
            label="Tracing"
            active={tab === 'tracing'}
            onPress={() => setTab('tracing')}
          />
        </View>
        {tab === 'masking' && <MaskingScreen />}
        {tab === 'dialogs' && <DialogsScreen />}
        {tab === 'api' && <ApiScreen />}
        {tab === 'tracing' && <TracingScreen />}
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
  archBanner: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignItems: 'center',
  },
  archBannerLegacy: {
    backgroundColor: '#7A3E00',
  },
  archBannerNew: {
    backgroundColor: '#0B5D1E',
  },
  archBannerText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '700',
  },
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
