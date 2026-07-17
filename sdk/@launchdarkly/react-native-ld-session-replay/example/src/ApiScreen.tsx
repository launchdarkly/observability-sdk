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
import type { SpanScope } from '@launchdarkly/observability-react-native';
import { useLDClient } from '@launchdarkly/react-native-client-sdk';
import { context, SpanStatusCode, type Span } from '@opentelemetry/api';
import { SERVICE_VERSION } from './serviceVersion';

// Hermes/V8 truncate `error.stack` at Error.stackTraceLimit (often ~10 frames).
// Raise it so the recorded error carries the full call chain.
(Error as ErrorConstructor & { stackTraceLimit?: number }).stackTraceLimit = 50;

// A realistic failure that originates several frames deep. `error.stack` is
// captured where `new Error()` runs, so throwing from the bottom of this chain
// gives a full, real stack trace (loadAppVersion -> parseVersionResponse ->
// decodeVersionField) rather than just the button handler. In a Hermes release
// build these become bytecode offsets that the uploaded source map resolves
// back to these functions.
//
// The thrown message embeds the running `service.version` so the demo error is
// easy to tell apart between builds when reading the symbolicated stack trace.
function loadAppVersion(version: string): never {
  return parseVersionResponse(version);
}

function parseVersionResponse(version: string): never {
  return decodeVersionField(version);
}

function decodeVersionField(version: string): never {
  throw new Error(`Demo failure: crash while decoding app version ${version}`);
}

/**
 * Manual test screen that mirrors the iOS TestApp `MainMenuViewModel`
 * (swift-launchdarkly-observability/TestApp/Sources/MainMenuViewModel.swift),
 * exercising the public Observability + LaunchDarkly client APIs from React
 * Native.
 *
 * The iOS sample has both `LDObserve.track` and `LDClient.track` variants, and
 * so does this screen. Both accept a plain dictionary (matching the native
 * `[String: Any]` / `Map<String, Any?>` surfaces): nested objects and arrays of
 * objects are flattened into dotted attribute keys (e.g. `products.0.price`).
 *   - `LDObserve.track(...)` records a `track` span directly through the
 *     Observability API.
 *   - `ldClient.track(...)` goes through the LaunchDarkly client; the
 *     Observability plugin turns it into the same `track` span via its
 *     afterTrack hook.
 */

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
    try {
      // Trigger the failure deep in the call chain so the captured stack is
      // real and multi-frame. Pass the running service.version so the error
      // message pins the build the reported stack trace came from.
      loadAppVersion(SERVICE_VERSION);
    } catch (err) {
      LDObserve.recordError(err as Error, {});
      log(`[error] recordError(${(err as Error).message})`);
    }
  };

  // -- Span + flag variation ----------------------------------------------
  const recordSpanAndVariation = () => {
    const span = LDObserve.startSpan('button-pressed');
    const value = ldClient.boolVariation('feature1', false);
    span.setAttribute('feature1', value);
    span.end();
    log(`[span] button-pressed + boolVariation(feature1)=${value}`);
  };

  // -- Flag evaluation -----------------------------------------------------
  // Evaluates a flag directly, exercising the Observability afterEvaluation
  // hook (which emits a `feature_flag` exposure span). Tapping repeatedly is
  // a handy way to see exposure deduplication in action: identical results
  // within the dedupe window are collapsed to a single exposure span.
  const evaluateFlag = () => {
    const value = ldClient.boolVariation('feature1', false);
    log(`[flag] boolVariation(feature1)=${value}`);
  };

  // -- Nested spans (counter + log + network inside) -----------------------
  // Uses `withSpan` / `scope.child` so the hierarchy survives the `await`s. On
  // React Native the active OTel context is tracked only synchronously, so
  // reading it back after an `await` (e.g. plain nested `startActiveSpan`)
  // would re-root the inner spans — `scope.child` captures the parent context
  // explicitly instead. See the distributed tracing guide.
  const triggerNestedSpans = async () => {
    await LDObserve.withSpan('NestedSpan', async (scope0) => {
      scope0.span.setAttribute('test-true', true);
      scope0.span.setAttribute('test-double', 3.14);
      await scope0.child('NestedSpan1', async (scope1) => {
        await scope1.child('NestedSpan2', async (scope2) => {
          LDObserve.recordCount({ name: 'NestedCounter', value: 10.0 });
          LDObserve.recordLog('NestedLog', 'info', {});
          await fetchUrlsForNestedSpanDemo(scope2);
        });
      });
    });
    log(
      '[nested] NestedSpan > NestedSpan1 > NestedSpan2 (+ counter, log, http)'
    );
  };

  // -- getTracer(): standard OpenTelemetry startActiveSpan ----------------
  const tracerStartActiveSpan = async () => {
    const tracer = LDObserve.getTracer();
    await tracer.startActiveSpan('Checkout', async (span: Span) => {
      span.setAttribute('cart.id', 'cart-7');
      try {
        const response = await fetch(
          'https://jsonplaceholder.typicode.com/posts'
        );
        span.setAttribute('http.status_code', response.status);
        span.setStatus({ code: SpanStatusCode.OK });
        log(`[tracer] startActiveSpan Checkout status=${response.status}`);
      } catch (err) {
        span.recordException(err as Error);
        span.setStatus({ code: SpanStatusCode.ERROR });
        log(`[tracer] Checkout threw: ${(err as Error).message}`);
      } finally {
        span.end();
      }
    });
  };

  // -- getTracer(): async-safe nested spans via tracer.withSpan -------------
  const tracerWithSpanNested = async () => {
    const tracer = LDObserve.getTracer();
    await tracer.withSpan('LoadProducts', async (load: SpanScope) => {
      const items = await load.child(
        'FetchFromApi',
        async (fetchScope: SpanScope) => {
          const response = await fetch(
            'https://jsonplaceholder.typicode.com/posts'
          );
          fetchScope.span.setAttribute('http.status_code', response.status);
          const json = await response.text();
          return fetchScope.child(
            'DeserializeJson',
            (parseScope: SpanScope) => {
              const result = JSON.parse(json) as unknown[];
              parseScope.span.setAttribute('product_count', result.length);
              return result;
            }
          );
        }
      );
      load.child('RenderUI', (renderScope: SpanScope) => {
        renderScope.span.setAttribute('product_count', items.length);
      });
      log(
        `[tracer] withSpan LoadProducts > FetchFromApi > DeserializeJson / RenderUI (${items.length})`
      );
    });
  };

  // Auto-instrumented fetch spans only attach to the active span when `fetch`
  // is *invoked* synchronously. The active context is lost across each `await`,
  // so wrap every call in `scope.active(...)` to keep both requests parented to
  // NestedSpan2 (the second one would otherwise become its own root trace).
  const fetchUrlsForNestedSpanDemo = async (scope: SpanScope) => {
    try {
      await scope.active(() => fetch('https://www.google.com'));
      await scope.active(() => fetch('https://www.android.com/'));
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

  // -- Track (via Observability API) ---------------------------------------
  const trackViaLDObserve = () => {
    // Records a `track` span directly through the Observability API, mirroring
    // the iOS `LDObserve.shared.track(...)` demo. `properties` is a plain
    // dictionary; the nested map is flattened to dotted keys (e.g.
    // `test-map.test-string`).
    LDObserve.track('track-via-ld-observe', {
      'test-string': 'react-native',
      'test-true': true,
      'test-false': false,
      'test-integer': 42,
      // A 64-bit value beyond Int32 range (e.g. epoch nanoseconds).
      'test-long': 9_000_000_000_123,
      'test-double': 3.14,
      'test-map': { 'test-string': 'val' },
    });
    log('[track] LDObserve.track(track-via-ld-observe)');
  };

  const trackNestedViaLDObserve = () => {
    // A nested `track` payload via the plain-dictionary variant, mirroring the
    // iOS `trackNested` demo. The `products` array of objects is flattened to
    // `products.0.product_id`, `products.1.price`, etc.
    LDObserve.track('checkout-started', {
      name: 'Checkout Started',
      order_id: 'ord_5521',
      value: 72.0,
      currency: 'USD',
      products: [
        { product_id: 'SKU-1234', quantity: 2, price: 24.0 },
        { product_id: 'SKU-9876', quantity: 1, price: 24.0 },
      ],
    });
    log('[track] LDObserve.track(checkout-started) nested payload');
  };

  const trackViaLDObserveWithMetric = () => {
    // The optional third argument is the numeric metric value used by
    // LaunchDarkly experimentation for numeric custom metrics.
    LDObserve.track('purchase-completed', { currency: 'USD' }, 72.0);
    log('[track] LDObserve.track(purchase-completed, value=72)');
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
      await fetch('https://launchdarkly.com/');
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

        <SectionHeader title="Track (via Observability API)" topSpacing />
        <View style={styles.col}>
          <Btn
            label="Track via LDObserve"
            onPress={run('track-observe', trackViaLDObserve)}
          />
          <Btn
            label="Track nested via LDObserve"
            onPress={run('track-observe-nested', trackNestedViaLDObserve)}
          />
          <Btn
            label="Track via LDObserve + metric value"
            onPress={run('track-observe-metric', trackViaLDObserveWithMetric)}
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
          <Btn label="Flag Eval" onPress={run('flag-eval', evaluateFlag)} />
          <Btn
            label="Nested spans"
            onPress={run('nested', triggerNestedSpans)}
          />
        </View>

        <SectionHeader title="OpenTelemetry Tracer (getTracer)" topSpacing />
        <View style={styles.col}>
          <Btn
            label="Tracer · startActiveSpan"
            onPress={run('tracer-active', tracerStartActiveSpan)}
          />
          <Btn
            label="Tracer · withSpan (nested)"
            onPress={run('tracer-withSpan', tracerWithSpanNested)}
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
