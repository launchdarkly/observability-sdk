import SessionReplayReactNative from './NativeSessionReplayReactNative';
import type { SessionReplayOptions } from './NativeSessionReplayReactNative';
import type {
  LDPlugin,
  LDClientMin,
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
    const parts: string[] = [];
    for (const [kindName, value] of Object.entries(context)) {
      if (kindName !== 'kind' && typeof value === 'object' && value !== null) {
        parts.push(
          `${kindName}:${escapeContextKey((value as { key: string }).key)}`
        );
      }
    }
    return parts.sort().join(':');
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
  return SessionReplayReactNative.configure(key, options);
}

export function startSessionReplay(): Promise<void> {
  return SessionReplayReactNative.startSessionReplay();
}

export function stopSessionReplay(): Promise<void> {
  return SessionReplayReactNative.stopSessionReplay();
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
    configureSessionReplay(key, this.options)
      .then(() => {
        return startSessionReplay();
      })
      .catch((error) => {
        console.error(
          '[SessionReplay] Failed to initialize session replay:',
          error
        );
      });
  }
}

export function createSessionReplayPlugin(
  options: SessionReplayOptions = {}
): LDPlugin {
  return new SessionReplayPluginAdapter(options);
}

export type { SessionReplayOptions };
