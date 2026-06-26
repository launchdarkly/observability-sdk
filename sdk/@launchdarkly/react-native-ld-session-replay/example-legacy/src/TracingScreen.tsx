import {useEffect, useRef, useState} from 'react';
import {
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {LDObserve} from '@launchdarkly/observability-react-native';
import {
  context,
  propagation,
  SpanStatusCode,
  trace,
  type Span,
} from '@opentelemetry/api';

/**
 * Manual test screen that exercises every recipe from the React Native
 * tracing guide:
 *   sdk/@launchdarkly/observability-react-native/guides/tracing.md
 *
 * Each button runs one recipe and appends a line (with the resulting trace/span
 * IDs where relevant) to the on-screen log. The observability plugin is wired up
 * in App.tsx with `tracingOrigins` set to the demo API hosts used below, so the
 * backend-propagation recipes (11, 12) actually inject `traceparent` / `baggage`
 * headers.
 *
 * Note: with a placeholder mobile key the SDK still creates spans locally; export
 * to LaunchDarkly only happens once a real key is configured.
 */

// Recipes below use literal demo endpoints so each one stands on its own. The
// jsonplaceholder host is listed in `tracingOrigins` (see App.tsx), so requests
// to it carry trace/baggage headers.
const short = (id?: string) => (id ? id.slice(0, 8) : 'n/a');
const traceOf = (span: Span) => short(span.spanContext().traceId);

export default function TracingScreen() {
  const [lines, setLines] = useState<string[]>([]);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const log = (msg: string) =>
    setLines(prev =>
      [`${new Date().toLocaleTimeString()}  ${msg}`, ...prev].slice(0, 200),
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

  useEffect(
    () => () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    },
    [],
  );

  // -- 1. Root span --------------------------------------------------------
  const rootSpan = () => {
    LDObserve.startActiveSpan(
      'app-cold-start',
      span => {
        span.setAttribute('launch_type', 'cold');
        span.setAttribute('device_os', Platform.OS);
        span.addEvent('splash_rendered');
        span.addEvent('home_screen_ready');
        log(`[1] root span "app-cold-start" trace=${traceOf(span)}`);
        span.end();
      },
      {root: true},
    );
  };

  // -- 2. Nested spans -----------------------------------------------------
  const nestedSpans = async () => {
    // `withSpan` ends each span automatically and `scope.child` parents off the
    // captured context, so the LoadProducts > FetchFromApi > DeserializeJson /
    // RenderUI hierarchy survives the `await`s without threading context by hand
    // (React Native's StackContextManager only tracks the active span
    // synchronously).
    const count = await LDObserve.withSpan('LoadProducts', async load => {
      const items = await load.child('FetchFromApi', async fetchScope => {
        const response = await fetch(
          'https://jsonplaceholder.typicode.com/posts',
        );
        fetchScope.span.setAttribute('http.status_code', response.status);
        const json = await response.text();
        // Parents to FetchFromApi even though we are past two awaits.
        return fetchScope.child('DeserializeJson', parseScope => {
          const result = JSON.parse(json) as unknown[];
          parseScope.span.setAttribute('product_count', result.length);
          return result;
        });
      });
      // Parents to LoadProducts (not FetchFromApi) — uses the captured context.
      load.child('RenderUI', renderScope => {
        renderScope.span.setAttribute('product_count', items.length);
      });
      return items.length;
    });
    log(`[2] LoadProducts > FetchFromApi > DeserializeJson (${count})`);
  };

  // -- 3. HTTP span with manual error handling -----------------------------
  const httpWithErrorHandling = async () => {
    await LDObserve.startActiveSpan('FetchUserProfile', async span => {
      span.setAttribute('user.id', '1');
      span.setAttribute('http.method', 'GET');
      try {
        const url = 'https://jsonplaceholder.typicode.com/users/1';
        span.setAttribute('http.url', url);
        const response = await fetch(url);
        span.setAttribute('http.status_code', response.status);
        if (!response.ok) {
          span.setStatus({code: SpanStatusCode.ERROR});
          span.setAttribute('error.type', `HTTP ${response.status}`);
          log(`[3] FetchUserProfile failed: HTTP ${response.status}`);
          return;
        }
        await response.json();
        span.setStatus({code: SpanStatusCode.OK});
        log(`[3] FetchUserProfile OK trace=${traceOf(span)}`);
      } catch (err) {
        span.recordException(err as Error);
        span.setStatus({code: SpanStatusCode.ERROR});
        log(`[3] FetchUserProfile threw: ${(err as Error).message}`);
      } finally {
        span.end();
      }
    });
  };

  // -- 4. Auto fetch instrumentation under a custom parent -----------------
  const autoInstrumentedChild = async () => {
    await LDObserve.withSpan('SyncOrders', async ({span}) => {
      span.setAttribute('sync.direction', 'pull');
      // `withSpan` makes SyncOrders active for the synchronous window, so this
      // fetch (started before the first await) auto-parents to it.
      const response = await fetch(
        'https://jsonplaceholder.typicode.com/posts?_limit=5',
      );
      span.setAttribute('http.status_code', response.status);
      const orders = (await response.json()) as unknown[];
      span.setAttribute('order_count', orders.length);
      log(`[4] SyncOrders + auto HTTP child (${orders.length} orders)`);
    });
  };

  // -- 5. Record exception and mark span failed ----------------------------
  const recordException = async () => {
    await LDObserve.startActiveSpan('ProcessPayment', async span => {
      span.setAttribute('order.id', 'order-42');
      span.setAttribute('payment.amount', 19.99);
      try {
        // Simulate a gateway failure.
        throw new Error('Payment gateway timeout');
      } catch (err) {
        const error = err as Error;
        span.recordException(error);
        span.setStatus({code: SpanStatusCode.ERROR});
        span.setAttribute('error.category', error.name);
        LDObserve.recordError(error, {'order.id': 'order-42'}, {span});
        log(`[5] ProcessPayment recorded exception: ${error.message}`);
      } finally {
        span.end();
      }
    });
  };

  // -- 6. Correlated logs inside the active span ---------------------------
  const correlatedLogs = async () => {
    await LDObserve.withSpan('ImportCatalog', async ({span, active}) => {
      // Logs correlate to a span via the active context. The first log is in the
      // synchronous window so it correlates automatically.
      LDObserve.recordLog('Import started', 'info', {source: 'demo'});
      let imported = 0;
      for (const _row of [1, 2, 3, 4, 5]) {
        await new Promise<void>(r => setTimeout(r, 5));
        imported++;
      }
      span.setAttribute('imported_count', imported);
      // After the awaits the active context is gone — re-establish it with
      // `active()` so this log still correlates to ImportCatalog.
      active(() =>
        LDObserve.recordLog('Import completed', 'info', {
          imported_count: imported,
        }),
      );
      log(`[6] ImportCatalog: 2 correlated logs, trace=${traceOf(span)}`);
    });
  };

  // -- 7. Re-establish context to correlate logs across async boundaries ----
  const correlateAcrossAsync = () => {
    const span = LDObserve.startSpan('UploadReport');
    span.setAttribute('report.type', 'daily');
    const capturedContext = LDObserve.getContextFromSpan(span);
    const tid = traceOf(span);
    span.end();

    setTimeout(() => {
      // Active context is empty here -- re-establish it explicitly.
      context.with(capturedContext, () => {
        LDObserve.recordLog('Upload processing on background tick', 'info', {
          phase: 'start',
        });
        LDObserve.recordLog('Upload complete', 'info', {phase: 'end'});
      });
      log(`[7] Re-established context for logs, trace=${tid}`);
    }, 0);
  };

  // -- 8a. Child span where automatic propagation won't work ---------------
  const detachedChildSpan = () => {
    LDObserve.withSpan('ScheduleSync', ({span, ctx}) => {
      span.setAttribute('sync.mode', 'background');
      const tid = traceOf(span);

      // setTimeout drops the active context -> capture ScheduleSync's `ctx` and
      // pass it as the explicit `parent` once the timer fires.
      setTimeout(async () => {
        await LDObserve.withSpan(
          'BackgroundSync',
          async ({span: childSpan}) => {
            const response = await fetch(
              'https://jsonplaceholder.typicode.com/posts/1',
            );
            childSpan.setAttribute('http.status_code', response.status);
            childSpan.addEvent('sync.complete');
          },
          {parent: ctx},
        );
        log(`[8a] BackgroundSync re-parented to ScheduleSync trace=${tid}`);
      }, 0);
    });
  };

  // -- 8b. Bounded polling with re-parented tick spans ---------------------
  const startBoundedPolling = () => {
    if (intervalRef.current) {
      log('[8b] polling already running');
      return;
    }
    const span = LDObserve.startSpan('StartPolling');
    const parentContext = LDObserve.getContextFromSpan(span);
    const tid = traceOf(span);
    span.end();

    let ticks = 0;
    intervalRef.current = setInterval(() => {
      ticks++;
      LDObserve.withSpan(
        'PollTick',
        ({span: pollSpan}) => {
          pollSpan.setAttribute('tick.time', new Date().toISOString());
          pollSpan.setAttribute('tick.number', ticks);
        },
        {parent: parentContext},
      );
      log(`[8b] PollTick #${ticks} parent trace=${tid}`);
      if (ticks >= 3 && intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }, 1000);
  };

  // -- 9. Sequential independent root spans --------------------------------
  // Created inside an active parent span on purpose: with `{ root: true }` each
  // analytics span must start a brand-new trace instead of nesting under the
  // parent. We then assert every child trace differs from the parent's and from
  // each other, so this actually exercises `root` rather than relying on there
  // being no ambient context.
  const independentRootSpans = () => {
    const events = [
      {type: 'view', userId: 'u1'},
      {type: 'click', userId: 'u2'},
      {type: 'purchase', userId: 'u3'},
    ];
    LDObserve.startActiveSpan('AnalyticsBatch', parent => {
      const parentTrace = parent.spanContext().traceId;
      const childTraces: string[] = [];
      for (const evt of events) {
        LDObserve.startActiveSpan(
          `Analytics:${evt.type}`,
          span => {
            span.setAttribute('event.type', evt.type);
            span.setAttribute('event.timestamp', new Date().toISOString());
            span.setAttribute('event.user_id', evt.userId);
            span.setStatus({code: SpanStatusCode.OK});
            childTraces.push(span.spanContext().traceId);
            span.end();
          },
          {root: true},
        );
      }
      parent.end();

      const detachedFromParent = childTraces.every(t => t !== parentTrace);
      const allUnique = new Set(childTraces).size === childTraces.length;
      const verdict = detachedFromParent && allUnique ? 'PASS' : 'FAIL';
      log(
        `[9] ${verdict} independence: parent=${short(parentTrace)} ` +
          `children=[${childTraces.map(short).join(', ')}] ` +
          `(detachedFromParent=${detachedFromParent}, allUnique=${allUnique})`,
      );
    });
  };

  // -- 10. Span events as lightweight checkpoints --------------------------
  const spanEvents = async () => {
    await LDObserve.withSpan('DownloadAndCacheImage', async ({span}) => {
      const imageUrl = 'https://reactnative.dev/img/tiny_logo.png';
      span.setAttribute('image.url', imageUrl);
      span.addEvent('download.started');
      const response = await fetch(imageUrl);
      const blob = await response.blob();
      span.addEvent('download.completed');
      span.setAttribute('image.size_bytes', blob.size);
      span.addEvent('cache.write.started');
      // (no real filesystem write in the demo)
      span.addEvent('cache.write.completed', {bytes: blob.size});
      log(`[10] DownloadAndCacheImage: ${blob.size} bytes, 4 events`);
    });
  };

  // -- 11. Connecting mobile traces to your backend ------------------------
  const backendDistributedTrace = async () => {
    await LDObserve.withSpan('Checkout', async ({span}) => {
      span.setAttribute('cart.id', 'cart-7');
      // traceparent is injected automatically because the host is a tracing
      // origin -> a backend span would join this trace.
      const response = await fetch(
        'https://jsonplaceholder.typicode.com/posts',
        {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({cartId: 'cart-7'}),
        },
      );
      span.setAttribute('http.status_code', response.status);
      log(`[11] Checkout POST (traceparent propagated) trace=${traceOf(span)}`);
    });
  };

  // -- 11b. Continuing a trace from incoming headers -----------------------
  const incomingHeaders = () => {
    const headers = {
      'x-session-id': 'sess-abc',
      'x-request-id': 'req-123',
    };
    LDObserve.runWithHeaders('HandlePushPayload', headers, span => {
      span.setAttribute('payload.kind', 'promo');
      log(`[11b] runWithHeaders span, trace=${traceOf(span)}`);
    });
    const ctx = LDObserve.parseHeaders(headers);
    log(`[11b] parseHeaders -> session=${ctx.sessionId} req=${ctx.requestId}`);
  };

  // -- 12. Propagating baggage --------------------------------------------
  const baggage = async () => {
    const bag = propagation.createBaggage({
      'app.tenant_id': {value: 'acme'},
      'app.user_tier': {value: 'gold'},
    });
    await context.with(
      propagation.setBaggage(context.active(), bag),
      async () => {
        // Read it back.
        const tenant = propagation
          .getActiveBaggage()
          ?.getEntry('app.tenant_id')?.value;

        await LDObserve.startActiveSpan('LoadDashboard', async span => {
          // Baggage is not copied onto spans automatically -- do it explicitly.
          propagation
            .getActiveBaggage()
            ?.getAllEntries()
            .forEach(([key, entry]) => span.setAttribute(key, entry.value));

          // Outgoing request to a tracing origin also carries the baggage header.
          await fetch('https://jsonplaceholder.typicode.com/posts/1');
          log(`[12] baggage tenant=${tenant} copied onto LoadDashboard span`);
          span.end();
        });
      },
    );
  };

  // -- Utilities -----------------------------------------------------------
  const showSessionInfo = () => {
    const info = LDObserve.getSessionInfo();
    const active = trace.getActiveSpan();
    log(
      `[i] initialized=${LDObserve.isInitialized()} session=${JSON.stringify(
        info,
      )} activeSpan=${active ? 'yes' : 'none'}`,
    );
  };

  const flush = run('flush', async () => {
    await LDObserve.flush();
    log('[i] flushed telemetry');
  });

  return (
    <View style={styles.root}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <SectionHeader title="Spans" />
        <View style={styles.col}>
          <Btn label="1 · Root span" onPress={run('1', rootSpan)} />
          <Btn label="2 · Nested spans" onPress={run('2', nestedSpans)} />
          <Btn
            label="3 · HTTP span + error handling"
            onPress={run('3', httpWithErrorHandling)}
          />
          <Btn
            label="4 · Auto fetch child span"
            onPress={run('4', autoInstrumentedChild)}
          />
          <Btn
            label="9 · Independent root spans"
            onPress={run('9', independentRootSpans)}
          />
          <Btn
            label="10 · Span events (checkpoints)"
            onPress={run('10', spanEvents)}
          />
        </View>

        <SectionHeader title="Errors & Logs" topSpacing />
        <View style={styles.col}>
          <Btn
            label="5 · Record exception + fail span"
            onPress={run('5', recordException)}
          />
          <Btn
            label="6 · Correlated logs in span"
            onPress={run('6', correlatedLogs)}
          />
        </View>

        <SectionHeader title="Context propagation" topSpacing />
        <View style={styles.col}>
          <Btn
            label="7 · Correlate logs across async"
            onPress={run('7', correlateAcrossAsync)}
          />
          <Btn
            label="8a · Detached child span"
            onPress={run('8a', detachedChildSpan)}
          />
          <Btn
            label="8b · Bounded polling (3 ticks)"
            onPress={run('8b', startBoundedPolling)}
          />
        </View>

        <SectionHeader title="Distributed tracing" topSpacing />
        <View style={styles.col}>
          <Btn
            label="11 · Backend trace (traceparent)"
            onPress={run('11', backendDistributedTrace)}
          />
          <Btn
            label="11b · Incoming headers"
            onPress={run('11b', incomingHeaders)}
          />
          <Btn label="12 · Baggage" onPress={run('12', baggage)} />
        </View>

        <SectionHeader title="Utilities" topSpacing />
        <View style={styles.row}>
          <Btn label="Session info" onPress={run('info', showSessionInfo)} />
          <Btn label="Flush" onPress={flush} />
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
        style={[styles.sectionTitle, topSpacing ? {marginTop: 16} : undefined]}>
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
  variant?: 'default' | 'danger';
}) {
  return (
    <TouchableOpacity
      style={[styles.btn, variant === 'danger' ? styles.btnDanger : undefined]}
      onPress={onPress}
      activeOpacity={0.75}>
      <Text style={styles.btnText}>{label}</Text>
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
