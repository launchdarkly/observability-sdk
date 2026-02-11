import SessionReplayReactNative from './NativeSessionReplayReactNative';
import type { SessionReplayOptions } from './NativeSessionReplayReactNative';
import type { LDPlugin } from '@launchdarkly/react-native-client-sdk';
import type {
  LDPluginEnvironmentMetadata,
  LDPluginMetadata,
} from '@launchdarkly/js-sdk-common';
import type { LDClient } from '@launchdarkly/react-native-client-sdk';

const MOBILE_KEY_REQUIRED_MESSAGE =
  'Session replay requires a non-empty mobile key. Provide metadata.sdkKey or metadata.mobileKey when initializing the LaunchDarkly client.';

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

  register(_client: LDClient, metadata: LDPluginEnvironmentMetadata): void {
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
