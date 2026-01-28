import SessionReplayReactNative from './NativeSessionReplayReactNative';
import type { SessionReplayOptions } from './NativeSessionReplayReactNative';
import type { LDPlugin } from '@launchdarkly/react-native-client-sdk';
import type {
  LDPluginEnvironmentMetadata,
  LDPluginMetadata,
} from '@launchdarkly/js-sdk-common';
import type { LDClient } from '@launchdarkly/react-native-client-sdk';
import type { Hook } from '@launchdarkly/js-client-sdk-common';

export function configureSessionReplay(
  mobileKey: string,
  options: SessionReplayOptions = {}
): Promise<void> {
  return SessionReplayReactNative.configure(mobileKey, options);
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

		configureSessionReplay(sdkKey, this.options)
			.then(() => {
				return startSessionReplay();
			})
			.catch(() => {
				// Error handled silently - configuration failures should be handled by the native module
			});
  }

  getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
    return [];
  }
}

export function createSessionReplayPlugin(
  options: SessionReplayOptions = {}
): LDPlugin {
  return new SessionReplayPluginAdapter(options);
}
