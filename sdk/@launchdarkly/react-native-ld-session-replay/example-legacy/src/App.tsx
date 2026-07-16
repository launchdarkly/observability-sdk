import {
  DevSettings,
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
import {
  LDObserve,
  Observability,
} from '@launchdarkly/observability-react-native';
import {
  LAUNCHDARKLY_MOBILE_KEY,
  LAUNCHDARKLY_ENV,
  LAUNCHDARKLY_OTLP_ENDPOINT,
  LAUNCHDARKLY_BACKEND_URL,
} from '@env';
import {resolveLDEnvironment} from './ldEnvironments';
import DialogsScreen from './DialogsScreen';
import MaskingScreen from './MaskingScreen';
import TracingScreen from './TracingScreen';
import ApiScreen from './ApiScreen';

// Pick the LaunchDarkly instance once for the client-side URLs (they must stay
// paired with the mobile key, or identify fails with a 401). The observability
// URLs default to the same bundle but can be pointed at localhost or any staging
// server via LAUNCHDARKLY_OTLP_ENDPOINT / LAUNCHDARKLY_BACKEND_URL in .env.
const {env: LD_ENV, endpoints} = resolveLDEnvironment(LAUNCHDARKLY_ENV, {
  otlpEndpoint: LAUNCHDARKLY_OTLP_ENDPOINT,
  backendUrl: LAUNCHDARKLY_BACKEND_URL,
});

// Regenerated every time the JS bundle loads. A soft reload (DevSettings.reload)
// restarts the JS runtime in the same process, so this changes while the native
// session replay singleton — and its sampling decision — persist untouched.
// It is also passed as a JS `resourceAttribute` below to demonstrate that JS
// observability resource attributes ARE reapplied on soft reload (unlike the
// native session replay config, which is frozen until a full cold start).
const JS_LOAD_ID = Math.random().toString(36).slice(2, 8);
console.log(`[soft-reload] JS_LOAD_ID=${JS_LOAD_ID} (new value each JS load)`);

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  serviceName: 'session-replay-rn-legacy-example',
  serviceVersion: '1.0.5',
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: false,
  maskImages: false,
  maskTestIDs: ['password', 'ssn'],
  unmaskTestIDs: ['safe'],
  minimumAlpha: 0.05,
  sampleRate: 1.0,
  otlpEndpoint: endpoints.otlpEndpoint,
  backendUrl: endpoints.backendUrl,
});

const observability = new Observability({
  serviceName: 'session-replay-rn-legacy-example',
  serviceVersion: '1.0.5',
  debug: true,
  tracingOrigins: ['jsonplaceholder.typicode.com', 'reactnative.dev'],
  otlpEndpoint: endpoints.otlpEndpoint,
  backendUrl: endpoints.backendUrl,
  // Reapplied on every JS (soft) reload: the JS observability SDK is fully
  // recreated when the runtime restarts, so this attribute reflects the current
  // JS load. Compare with the native SR service.version, which stays frozen.
  resourceAttributes: {
    'js.load_id': JS_LOAD_ID,
  },
});

// Set the values in example-legacy/.env (see .env.example) to record real
// sessions against your chosen environment.
const MOBILE_KEY =
  LAUNCHDARKLY_MOBILE_KEY ?? 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE';

console.log(
  `[env] LaunchDarkly env = ${LD_ENV}, mobile key prefix = ${MOBILE_KEY.slice(
    0,
    4,
  )}`,
); // expect "mob-"
console.log(
  `[env] observability otlpEndpoint = ${endpoints.otlpEndpoint}, backendUrl = ${endpoints.backendUrl}`,
);

const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
  plugins: [observability, plugin],
  streamUri: endpoints.streamUri,
  baseUri: endpoints.baseUri,
  eventsUri: endpoints.eventsUri,
});
const context = {kind: 'user', key: 'user-key-123abc'};

// Emulates an OTA "soft reload": restarts only the JS runtime, leaving the native
// process (and the SR singleton) alive. Sampling is intentionally NOT re-rolled —
// the native enable cycle never resets, so a sampled-out session stays out and a
// recording session keeps its original decision. Only a full cold start re-rolls.
async function softReload() {
  console.log(
    `[soft-reload] reloading JS runtime; native SR singleton persists, sampling NOT re-exercised (was JS_LOAD_ID=${JS_LOAD_ID})`,
  );

  // No manual `app_reload` span is needed: the observability SDK persists the
  // session and, on the next JS load, resumes it and emits `app_reload`
  // automatically. We just flush any buffered telemetry so nothing is lost when
  // the reload tears down the JS runtime.
  try {
    await LDObserve.flush();
  } catch (e) {
    console.warn('[soft-reload] failed to flush before reload', e);
  }

  if (typeof DevSettings?.reload === 'function') {
    DevSettings.reload('SR soft-reload test');
  } else {
    console.warn(
      '[soft-reload] DevSettings.reload is unavailable (release build); use a dev build to test soft reload',
    );
  }
}

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
        <View style={styles.actionBar}>
          <Text testID="safe" style={styles.actionLabel}>
            JS load: {JS_LOAD_ID}
          </Text>
          <TouchableOpacity
            testID="safe"
            style={styles.actionButton}
            onPress={softReload}>
            <Text testID="safe" style={styles.actionButtonText}>
              Soft reload
            </Text>
          </TouchableOpacity>
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
  actionBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    paddingBottom: 8,
  },
  actionLabel: {
    color: '#888',
    fontSize: 12,
  },
  actionButton: {
    backgroundColor: '#6650A4',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 12,
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
