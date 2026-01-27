import SessionReplayReactNative from './NativeSessionReplayReactNative';
import type { SessionReplayOptions } from './NativeSessionReplayReactNative';
import type { LDPlugin } from '@launchdarkly/react-native-client-sdk';
import type {
	LDPluginEnvironmentMetadata,
	LDPluginMetadata
} from '@launchdarkly/js-sdk-common';
import type { LDClient } from '@launchdarkly/react-native-client-sdk';
import type { Hook } from '@launchdarkly/js-client-sdk-common';


export function configureSessionReplay(
  mobileKey: string,
  options: SessionReplayOptions = {}
): Promise<void> {
	console.log(`session replay options: ${options.maskAccessibilityIdentifiers}`)
	console.log(`configure function: ${SessionReplayReactNative.configure}`);

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
		}
	}

	register(
		_client: LDClient,
		metadata: LDPluginEnvironmentMetadata,
	): void {
		console.log("registering plugin")
    const sdkKey = metadata.sdkKey || metadata.mobileKey || ''

    console.log(`registering plugin with sdk key: ${sdkKey}`)
    console.log(`registering plugin with metadata: ${metadata}`)
		try {
			configureSessionReplay(sdkKey, this.options)
				.then(() => {
					startSessionReplay()
						.then(() => {
							console.log("Session replay started")
						});
				});
		} catch(e) {
			console.log(`plugin created -----> ${e}`)
		}	
	}

	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return []
	}
}

export function createSessionReplayPlugin(
	options: SessionReplayOptions = {}
): LDPlugin {
	return new SessionReplayPluginAdapter(options);
}