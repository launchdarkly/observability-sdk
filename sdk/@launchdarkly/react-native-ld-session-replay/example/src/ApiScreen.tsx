import { useState } from 'react';
import {
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { LDObserve } from '@launchdarkly/observability-react-native';
import { useLDClient } from '@launchdarkly/react-native-client-sdk';
import { context } from '@opentelemetry/api';

/**
 * Manual test screen that mirrors the iOS TestApp `MainMenuViewModel`
 * (swift-launchdarkly-observability/TestApp/Sources/MainMenuViewModel.swift),
 * exercising the public Observability + LaunchDarkly client APIs from React
 * Native.
 *
 * The iOS sample has both `LDObserve.track` and `LDClient.track` variants. The
 * React Native Observability API does not expose `track`, so every track recipe
 * here goes through the LaunchDarkly client's `track` (the Observability plugin
 * turns it into a span via its afterTrack hook).
 */

// Public endpoint used by the network-request recipe.
const NETWORK_URL = 'https://launchdarkly.com/';
// Endpoints used by the nested-span recipe (mirrors the Swift demo).
const GOOGLE_URL = 'https://www.google.com';
const ANDROID_URL = 'https://www.android.com/';

export default function ApiScreen() {
  const ldClient = useLDClient();
  const [lines, setLines] = useState<string[]>([]);

  const log = (msg: string) =>
    setLines((prev) =>
      [`${new Date().toLocaleTimeString()}  ${msg}`, ...prev].slice(0, 200)
    );

  // Wrap each recipe so a thrown error is surfaced in the log instead of
  // crashing the screen.
  const run = (label: string, fn: () => void | Promise<void>) => async () => {
    try {
      await fn();
    } catch (err) {
      log(`✗ ${label} threw: ${(err as Error).message}`);
    }
  };

  // -- Errors --------------------------------------------------------------
  const recordError = () => {
    LDObserve.recordError(new Error('Demo failure: crash'), {});
    log('[error] recordError(Demo failure)');
  };

  // -- Span + flag variation ----------------------------------------------
  const recordSpanAndVariation = () => {
    const span = LDObserve.startSpan('button-pressed');
    const value = ldClient.boolVariation('feature1', false);
    span.setAttribute('feature1', value);
    span.end();
    log(`[span] button-pressed + boolVariation(feature1)=${value}`);
  };

  // -- Nested spans (counter + log + network inside) -----------------------
  const triggerNestedSpans = async () => {
    await LDObserve.startActiveSpan('NestedSpan', async (span0) => {
      span0.setAttribute('test-true', true);
      span0.setAttribute('test-double', 3.14);
      await LDObserve.startActiveSpan('NestedSpan1', async (span1) => {
        await LDObserve.startActiveSpan('NestedSpan2', async (span2) => {
          LDObserve.recordCount({ name: 'NestedCounter', value: 10.0 });
          LDObserve.recordLog('NestedLog', 'info', {});
          await fetchUrlsForNestedSpanDemo();
          span2.end();
        });
        span1.end();
      });
      span0.end();
    });
    log(
      '[nested] NestedSpan > NestedSpan1 > NestedSpan2 (+ counter, log, http)'
    );
  };

  const fetchUrlsForNestedSpanDemo = async () => {
    try {
      await fetch(GOOGLE_URL);
      await fetch(ANDROID_URL);
    } catch {
      // ignore network errors in the demo
    }
  };

  // -- Metrics -------------------------------------------------------------
  const recordMetric = () => {
    LDObserve.recordMetric({ name: 'test-gauge', value: 50.0 });
    log('[metric] gauge test-gauge=50');
  };

  const recordHistogramMetric = () => {
    LDObserve.recordHistogram({ name: 'test-histogram', value: 15.0 });
    log('[metric] histogram test-histogram=15');
  };

  const recordCounterMetric = () => {
    LDObserve.recordCount({ name: 'test-counter', value: 10.0 });
    log('[metric] counter test-counter=10');
  };

  const recordIncrementalMetric = () => {
    LDObserve.recordIncr({ name: 'test-incremental-counter', value: 12.0 });
    log('[metric] incr test-incremental-counter=12');
  };

  const recordUpDownCounterMetric = () => {
    LDObserve.recordUpDownCounter({
      name: 'test-up-down-counter',
      value: 25.0,
    });
    log('[metric] up/down test-up-down-counter=25');
  };

  // -- Logs ----------------------------------------------------------------
  const recordLogWithContext = () => {
    const span = LDObserve.startSpan('log-context-demo', {
      attributes: { demo: 'log-with-context' },
    });
    const capturedContext = LDObserve.getContextFromSpan(span);
    span.end();

    // Simulate a detached task where the active OTel context is lost; re-attach
    // the captured span context so the log stays correlated to the span.
    setTimeout(() => {
      context.with(capturedContext, () => {
        LDObserve.recordLog('Log with span context', 'warn', {
          source: 'detached-queue-demo',
        });
      });
      log('[log] recordLog correlated to log-context-demo span');
    }, 0);
  };

  const recordLogs = () => {
    LDObserve.recordLog('logs-button-pressed', 'info', {
      'test-string': 'react-native',
      'test-true': true,
      'test-false': false,
      'test-integer': 42,
      'test-long': 9_000_000_000,
      'test-double': 3.14,
      // OTel attributes only allow primitives or homogeneous arrays of
      // primitives, so the iOS sample's nested map is represented as JSON here.
      'test-array': [3.14],
      'test-nested': JSON.stringify({ array: [1] }),
    });
    log('[log] recordLog(logs-button-pressed) with mixed attributes');
  };

  // -- Track (via LaunchDarkly client) -------------------------------------
  const trackViaLDClient = () => {
    ldClient.track('track-via-ld-client', {
      'test-string': 'react-native',
      'test-true': true,
      'test-false': false,
      'test-integer': 42,
      'test-double': 3.14,
      'test-long-number': 9_000_000_000_123,
    });
    log('[track] ldClient.track(track-via-ld-client)');
  };

  const trackNested = () => {
    ldClient.track('checkout-started', {
      name: 'Checkout Started',
      order_id: 'ord_5521',
      value: 72.0,
      currency: 'USD',
      products: [
        { product_id: 'SKU-1234', quantity: 2, price: 24.0 },
        { product_id: 'SKU-9876', quantity: 1, price: 24.0 },
      ],
    });
    log('[track] ldClient.track(checkout-started) nested payload');
  };

  // -- Network -------------------------------------------------------------
  const performNetworkRequest = async () => {
    try {
      await fetch(NETWORK_URL);
      log('[network] GET launchdarkly.com');
    } catch {
      log('[network] GET launchdarkly.com (failed, ignored)');
    }
  };

  // -- Identify ------------------------------------------------------------
  const identifyUser = async () => {
    await ldClient.identify({
      kind: 'user',
      key: 'single-userkey',
      firstName: 'Bob',
      lastName: 'Bobberson',
    });
    log('[identify] user single-userkey');
  };

  const identifyAnonymous = async () => {
    await ldClient.identify({ kind: 'user', anonymous: true });
    log('[identify] anonymous user');
  };

  const identifyMulti = async () => {
    await ldClient.identify({
      kind: 'multi',
      user: {
        key: 'multi-username',
        name: 'multi-username',
        anonymous: false,
        customerNumber: '654321',
        firstName: 'Bob',
        lastName: 'Bobberson',
        email: 'multi@multi.com',
      },
      device: {
        key: 'iphone',
        name: 'iphone',
        anonymous: false,
        platform: 'ios',
        appVersion: '10.3.2.1',
      },
    });
    log('[identify] multi (user + device)');
  };

  // -- Crash ---------------------------------------------------------------
  const crash = () => {
    // Throw outside the `run` try/catch so it surfaces as an unhandled error,
    // mirroring the iOS `fatalError()` demo button.
    setTimeout(() => {
      throw new Error('Forced crash from API demo');
    }, 0);
    log('[crash] scheduling unhandled error…');
  };

  const flush = run('flush', async () => {
    await LDObserve.flush();
    log('[i] flushed telemetry');
  });

  return (
    <View style={styles.root}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <SectionHeader title="Identify" />
        <View style={styles.row}>
          <Btn
            label="User"
            variant="identify"
            onPress={run('identify', identifyUser)}
          />
          <Btn
            label="Multi"
            variant="identify"
            onPress={run('identify-multi', identifyMulti)}
          />
          <Btn
            label="Anon"
            variant="identify"
            onPress={run('identify-anon', identifyAnonymous)}
          />
        </View>

        <SectionHeader title="Track (via LD client)" topSpacing />
        <View style={styles.col}>
          <Btn
            label="Track via LD client"
            onPress={run('track-client', trackViaLDClient)}
          />
          <Btn
            label="Track nested"
            onPress={run('track-nested', trackNested)}
          />
        </View>

        <SectionHeader title="Spans" topSpacing />
        <View style={styles.col}>
          <Btn
            label="Record span + variation"
            onPress={run('span+variation', recordSpanAndVariation)}
          />
          <Btn
            label="Nested spans"
            onPress={run('nested', triggerNestedSpans)}
          />
        </View>

        <SectionHeader title="Metrics" topSpacing />
        <View style={styles.col}>
          <Btn label="Gauge metric" onPress={run('gauge', recordMetric)} />
          <Btn
            label="Histogram metric"
            onPress={run('histogram', recordHistogramMetric)}
          />
          <Btn
            label="Counter metric"
            onPress={run('counter', recordCounterMetric)}
          />
          <Btn
            label="Incremental counter"
            onPress={run('incr', recordIncrementalMetric)}
          />
          <Btn
            label="Up/Down counter"
            onPress={run('updown', recordUpDownCounterMetric)}
          />
        </View>

        <SectionHeader title="Errors & Logs" topSpacing />
        <View style={styles.col}>
          <Btn label="Record error" onPress={run('error', recordError)} />
          <Btn label="Record logs" onPress={run('logs', recordLogs)} />
          <Btn
            label="Log with span context"
            onPress={run('log-context', recordLogWithContext)}
          />
        </View>

        <SectionHeader title="Utilities" topSpacing />
        <View style={styles.row}>
          <Btn
            label="Network request"
            onPress={run('network', performNetworkRequest)}
          />
          <Btn label="Flush" onPress={flush} />
          <Btn label="Crash" onPress={run('crash', crash)} variant="danger" />
          <Btn
            label="Clear log"
            onPress={() => setLines([])}
            variant="danger"
          />
        </View>
      </ScrollView>

      <View style={styles.logContainer}>
        <Text style={styles.logTitle}>Output log</Text>
        <ScrollView style={styles.log} contentContainerStyle={styles.logScroll}>
          {lines.length === 0 ? (
            <Text style={styles.logEmpty}>No output yet — tap a button.</Text>
          ) : (
            lines.map((line, i) => (
              <Text key={`${i}-${line}`} style={styles.logLine}>
                {line}
              </Text>
            ))
          )}
        </ScrollView>
      </View>
    </View>
  );
}

function SectionHeader({
  title,
  topSpacing,
}: {
  title: string;
  topSpacing?: boolean;
}) {
  return (
    <>
      <Text
        style={[
          styles.sectionTitle,
          topSpacing ? { marginTop: 16 } : undefined,
        ]}
      >
        {title}
      </Text>
      <View style={styles.divider} />
    </>
  );
}

function Btn({
  label,
  onPress,
  variant,
}: {
  label: string;
  onPress: () => void;
  variant?: 'default' | 'danger' | 'identify';
}) {
  return (
    <TouchableOpacity
      style={[
        styles.btn,
        variant === 'danger' ? styles.btnDanger : undefined,
        variant === 'identify' ? styles.btnIdentify : undefined,
      ]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <Text
        style={[
          styles.btnText,
          variant === 'identify' ? styles.btnIdentifyText : undefined,
        ]}
      >
        {label}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
  },
  scroll: {
    padding: 16,
    paddingBottom: 24,
  },
  sectionTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
  },
  divider: {
    height: 1,
    backgroundColor: '#555',
    marginTop: 4,
    marginBottom: 8,
  },
  col: {
    gap: 8,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  btn: {
    backgroundColor: '#6650A4',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
    alignItems: 'center',
  },
  btnDanger: {
    backgroundColor: '#F2B8B5',
  },
  // Matches the iOS TestApp identify buttons (Colors.identifyBgColor /
  // Colors.identifyTextColor in MainMenuView.swift).
  btnIdentify: {
    backgroundColor: '#121D61',
  },
  btnIdentifyText: {
    color: '#8A9EFF',
  },
  btnText: {
    color: '#fff',
    fontWeight: '600',
  },
  logContainer: {
    height: 200,
    borderTopWidth: 1,
    borderTopColor: '#333',
    backgroundColor: '#0B0B0B',
    paddingHorizontal: 12,
    paddingTop: 8,
  },
  logTitle: {
    color: '#888',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  log: {
    flex: 1,
  },
  logScroll: {
    paddingBottom: 12,
  },
  logEmpty: {
    color: '#555',
    fontSize: 13,
    fontStyle: 'italic',
  },
  logLine: {
    color: '#A5D6A7',
    fontSize: 12,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    paddingVertical: 1,
  },
});
