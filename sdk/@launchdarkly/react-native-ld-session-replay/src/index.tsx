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

class SessionReplayPluginAdapter implements LDPlugin {
  private options: SessionReplayOptions;

  constructor(options: SessionReplayOptions = {}) {
    this.options = options;
  }

  getMetadata(): LDPluginMetadata {
    return {
      name: '@launchdarkly/observability-react-native',
    };
  }

  register(_client: LDClient, metadata: LDPluginEnvironmentMetadata): void {
    const sdkKey = metadata.sdkKey || metadata.mobileKey || '';

<<<<<<< Updated upstream
    try {
      configureSessionReplay(sdkKey, this.options).then(() => {
        startSessionReplay();
      });
    } catch (e) {
      // Error handled silently - configuration failures should be handled by the native module
    }
  }
=======
		configureSessionReplay(sdkKey, this.options)
			.then(() => {
				return startSessionReplay();
			})
			.catch(() => {
				// Error handled silently - configuration failures should be handled by the native module
			});
	}
>>>>>>> Stashed changes

  getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
    return [];
  }
}

export function createSessionReplayPlugin(
  options: SessionReplayOptions = {}
): LDPlugin {
  return new SessionReplayPluginAdapter(options);
}
