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
import {useEffect, useState} from 'react';
import {createSessionReplayPlugin} from '@launchdarkly/session-replay-react-native';
import {Observability} from '@launchdarkly/observability-react-native';
import {
  LAUNCHDARKLY_MOBILE_KEY,
  LAUNCHDARKLY_OTLP_ENDPOINT,
  LAUNCHDARKLY_BACKEND_URL,
} from '@env';
import DialogsScreen from './DialogsScreen';
import MaskingScreen from './MaskingScreen';
import TracingScreen from './TracingScreen';
import ApiScreen from './ApiScreen';

// Optional endpoint overrides from .env. When unset, the SDK falls back to its
// production defaults. Passed to both the JS observability plugin (traces, logs,
// metrics, errors) and the native session replay plugin (which forwards them to
// the native observability instance, so on-device replay uploads to the same
// environment), e.g. staging.
const OTLP_ENDPOINT = LAUNCHDARKLY_OTLP_ENDPOINT || undefined;
const BACKEND_URL = LAUNCHDARKLY_BACKEND_URL || undefined;

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  serviceName: 'session-replay-rn-legacy-example',
  serviceVersion: '1.0.0',
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: false,
  maskImages: false,
  maskTestIDs: ['password', 'ssn'],
  unmaskTestIDs: ['safe'],
  minimumAlpha: 0.05,
  sampleRate: 0.99,
  ...(OTLP_ENDPOINT ? {otlpEndpoint: OTLP_ENDPOINT} : {}),
  ...(BACKEND_URL ? {backendUrl: BACKEND_URL} : {}),
});

const observability = new Observability({
  serviceName: 'session-replay-rn-legacy-example',
  serviceVersion: '1.0.0',
  debug: true,
  tracingOrigins: ['jsonplaceholder.typicode.com', 'reactnative.dev'],
  ...(OTLP_ENDPOINT ? {otlpEndpoint: OTLP_ENDPOINT} : {}),
  ...(BACKEND_URL ? {backendUrl: BACKEND_URL} : {}),
});

// Set the values in example-legacy/.env (see .env.example) to record real
// sessions against your chosen environment.
const MOBILE_KEY =
  LAUNCHDARKLY_MOBILE_KEY ?? 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE';

const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
  plugins: [observability, plugin],
});
const context = {kind: 'user', key: 'user-key-123abc'};

// The New Architecture installs the Fabric UIManager on the JS global; its
// absence means the app is running on the legacy bridge architecture.
const IS_NEW_ARCH =
  (global as {nativeFabricUIManager?: unknown}).nativeFabricUIManager != null;
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
  const [status, setStatus] = useState('identifying…');

  useEffect(() => {
    client
      .identify(context)
      .then(() => setStatus('identified — session replay active'))
      .catch((e: unknown) => {
        console.log(e);
        setStatus(`identify failed: ${String(e)}`);
      });
  }, []);

  return (
    <LDProvider client={client}>
      <SafeAreaView style={{flex: 1, backgroundColor: '#000'}}>
        <View
          testID="safe"
          style={[
            styles.archBanner,
            IS_NEW_ARCH ? styles.archBannerNew : styles.archBannerLegacy,
          ]}>
          <Text testID="safe" style={styles.archBannerText}>
            {ARCH_LABEL}
          </Text>
        </View>
        <Text testID="safe" style={styles.status}>
          {status}
        </Text>
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
      testID="safe"
      style={[styles.tab, active ? styles.tabActive : undefined]}
      onPress={onPress}>
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
  status: {
    color: '#9c9',
    fontSize: 12,
    paddingHorizontal: 12,
    paddingVertical: 4,
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
