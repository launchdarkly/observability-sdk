import { View, type StyleProp, type ViewStyle } from 'react-native';
import type { ReactNode } from 'react';
import SessionReplayReactNative from './NativeSessionReplayReactNative';
import type { SessionReplayOptions } from './NativeSessionReplayReactNative';
import {
  LDObserve,
  type LDPlugin,
  type LDClientMin,
} from '@launchdarkly/observability-react-native';
import type {
  LDContext,
  LDPluginEnvironmentMetadata,
  LDPluginMetadata,
} from '@launchdarkly/js-sdk-common';
import type {
  Hook,
  HookMetadata,
  IdentifySeriesContext,
  IdentifySeriesData,
  IdentifySeriesResult,
} from '@launchdarkly/react-native-client-sdk';

const MOBILE_KEY_REQUIRED_MESSAGE =
  'Session replay requires a non-empty mobile key. Provide metadata.sdkKey or metadata.mobileKey when initializing the LaunchDarkly client.';

// testIDs reserved for <LDMask> / <LDUnmask>. Prepended to maskTestIDs / unmaskTestIDs
// in withInternalSentinels before options are forwarded to native.
const LD_INTERNAL_MASK_TEST_ID = '__LD_INTERNAL_MASK__';
const LD_INTERNAL_UNMASK_TEST_ID = '__LD_INTERNAL_UNMASK__';

function withInternalSentinels(
  options: SessionReplayOptions
): SessionReplayOptions {
  return {
    ...options,
    maskTestIDs: [LD_INTERNAL_MASK_TEST_ID, ...(options.maskTestIDs ?? [])],
    unmaskTestIDs: [
      LD_INTERNAL_UNMASK_TEST_ID,
      ...(options.unmaskTestIDs ?? []),
    ],
  };
}

// Mirrors escapeKey() in LDObserveContext.kt (observability-android)
function escapeContextKey(key: string): string {
  return key.replace(/%/g, '%25').replace(/:/g, '%3A');
}

// Mirrors SessionReplayHook.afterIdentify() in SessionReplayHook.kt (observability-android)
function contextKeysFromContext(context: LDContext): Record<string, string> {
  const keys: Record<string, string> = {};
  if (!('kind' in context)) {
    // Legacy LDUser — no 'kind' field, implicitly 'user'
    keys['user'] = context.key;
    return keys;
  }
  if (context.kind === 'multi') {
    for (const [kindName, value] of Object.entries(context)) {
      if (kindName !== 'kind' && typeof value === 'object' && value !== null) {
        keys[kindName] = (value as { key: string }).key;
      }
    }
    return keys;
  }
  keys[context.kind] = context.key as string;
  return keys;
}

// Mirrors LDObserveContext.fullyQualifiedKey in LDObserveContext.kt (observability-android)
function canonicalKeyFromContext(context: LDContext): string {
  if (!('kind' in context)) {
    // Legacy LDUser
    return context.key;
  }
  if (context.kind === 'multi') {
    const entries: Array<[string, string]> = [];
    for (const [kindName, value] of Object.entries(context)) {
      if (kindName !== 'kind' && typeof value === 'object' && value !== null) {
        entries.push([kindName, (value as { key: string }).key]);
      }
    }
    // Sort by kind name only, matching LDObserveContext.fullyQualifiedKey in (observability-android)
    entries.sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
    return entries.map(([k, v]) => `${k}:${escapeContextKey(v)}`).join(':');
  }
  if (context.kind === 'user') {
    return context.key;
  }
  return `${context.kind}:${escapeContextKey(context.key as string)}`;
}

export function afterIdentify(
  context: LDContext,
  completed: boolean
): Promise<void> {
  const contextKeys = contextKeysFromContext(context);
  const canonicalKey = canonicalKeyFromContext(context);
  return SessionReplayReactNative.afterIdentify(
    contextKeys,
    canonicalKey,
    completed
  );
}

export function configureSessionReplay(
  mobileKey: string,
  options: SessionReplayOptions = {}
): Promise<void> {
  const key = typeof mobileKey === 'string' ? mobileKey.trim() : '';
  if (!key) {
    return Promise.reject(new Error(MOBILE_KEY_REQUIRED_MESSAGE));
  }
  return SessionReplayReactNative.configure(
    key,
    withInternalSentinels(options)
  );
}

export function startSessionReplay(): Promise<void> {
  return SessionReplayReactNative.startSessionReplay();
}

export function stopSessionReplay(): Promise<void> {
  return SessionReplayReactNative.stopSessionReplay();
}

export type LDMaskProps = {
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
};

/**
 * Marks its children as sensitive in the session replay recording. Everything inside
 * is rendered as a mask, regardless of `maskLabels`, `maskImages`, or other global
 * rules. An ancestor `<LDMask>` overrides any `<LDUnmask>` further down the tree —
 * once a subtree is masked, nothing inside it can opt back in.
 */
export function LDMask({ children, style }: LDMaskProps) {
  return (
    <View collapsable={false} testID={LD_INTERNAL_MASK_TEST_ID} style={style}>
      {children}
    </View>
  );
}

/**
 * Marks its children as explicitly *not* sensitive in the session replay recording.
 * Overrides global rules like `maskLabels: true` for the wrapped subtree. An ancestor
 * `<LDMask>` still wins.
 */
export function LDUnmask({ children, style }: LDMaskProps) {
  return (
    <View collapsable={false} testID={LD_INTERNAL_UNMASK_TEST_ID} style={style}>
      {children}
    </View>
  );
}

export type LDClickProps = {
  /**
   * Stable, developer-chosen identifier reported as `event.id` on the `click` span (and as the
   * replay click selector) when a tap lands anywhere inside this wrapper. Use it to reliably
   * identify an element in product analytics regardless of layout, visible text, or A/B copy.
   */
  id: string;
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
};

/**
 * Tags its children with a stable analytics [id] for click tracking, reported as `event.id` on the
 * `click` event (and as the replay click selector). Use it to reliably identify an element in
 * product analytics regardless of layout, visible text, or A/B copy.
 *
 * A tap on any descendant resolves to the nearest enclosing `<LDClick>` id (the native SDK walks up
 * the view hierarchy), so wrapping a composite control tags the whole thing. The wrapper also
 * prevents React Native view flattening so the tag always reaches native.
 *
 * The id takes precedence over `testID` when the native SDK resolves `event.id`. Unlike `testID`, it
 * is a dedicated channel: not overloaded with e2e testing and never stripped by session-replay
 * privacy masking.
 */
export function LDClick({ id, children, style }: LDClickProps) {
  return (
    <View collapsable={false} nativeID={id} style={style}>
      {children}
    </View>
  );
}

class SessionReplayHook implements Hook {
  getMetadata(): HookMetadata {
    return { name: 'session-replay-react-native' };
  }

  afterIdentify(
    hookContext: IdentifySeriesContext,
    data: IdentifySeriesData,
    result: IdentifySeriesResult
  ): IdentifySeriesData {
    afterIdentify(hookContext.context, result.status === 'completed').catch(
      (error) => {
        console.error(
          '[SessionReplay] Failed to forward identify context:',
          error
        );
      }
    );
    return data;
  }
}

class SessionReplayPluginAdapter implements LDPlugin {
  private options: SessionReplayOptions;

  constructor(options: SessionReplayOptions = {}) {
    this.options = options;
  }

  getMetadata(): LDPluginMetadata {
    return {
      name: 'session-replay-react-native',
    };
  }

  getHooks(_metadata: LDPluginEnvironmentMetadata): Hook[] {
    return [new SessionReplayHook()];
  }

  register(_client: LDClientMin, metadata: LDPluginEnvironmentMetadata): void {
    const sdkKey = metadata.sdkKey || metadata.mobileKey || '';
    const key = typeof sdkKey === 'string' ? sdkKey.trim() : '';
    if (!key) {
      console.error('[SessionReplay]', MOBILE_KEY_REQUIRED_MESSAGE);
      return;
    }
    // Resolving observability's session id can require awaiting its async init
    // (see resolveObservabilitySessionId), so run the whole configure/start flow
    // off the (synchronous) register call.
    void this.configureAndStart(key);
  }

  private async configureAndStart(key: string): Promise<void> {
    // The JS observability SDK is the single source of truth for `session.id`.
    // Seed the native replay / observability instance with observability's
    // *resolved* session id so both pipelines report one `session.id`.
    //
    // Observability persists its session (AsyncStorage) and resumes it across a
    // JS reload, so on a soft/OTA reload it recovers the same id the native
    // singleton is already frozen on from the cold-start seed — they stay
    // aligned without ever reading anything back from native.
    const sessionId = await this.resolveObservabilitySessionId();
    const options: SessionReplayOptions = sessionId
      ? { ...this.options, sessionId }
      : this.options;

    try {
      await configureSessionReplay(key, options);
      await startSessionReplay();
    } catch (error) {
      console.error(
        '[SessionReplay] Failed to initialize session replay:',
        error
      );
    }
  }

  /**
   * Resolves observability's session id to seed native with.
   *
   * Prefers the *resolved* id (getSessionIdWhenReady waits out observability's
   * async init, including a persisted-session resume) rather than the
   * provisional id, so native isn't seeded with an id that init later replaces.
   * Falls back to the synchronous id for older observability builds without the
   * seam, and returns undefined when observability isn't present (native then
   * owns its own session).
   */
  private async resolveObservabilitySessionId(): Promise<string | undefined> {
    try {
      if (typeof LDObserve.getSessionIdWhenReady === 'function') {
        return await LDObserve.getSessionIdWhenReady();
      }
    } catch {
      // fall through to the synchronous read
    }
    try {
      const id = LDObserve.getSessionInfo()?.sessionId;
      return typeof id === 'string' && id.length > 0 ? id : undefined;
    } catch {
      return undefined;
    }
  }
}

export function createSessionReplayPlugin(
  options: SessionReplayOptions = {}
): LDPlugin {
  return new SessionReplayPluginAdapter(options);
}

export type { SessionReplayOptions };
