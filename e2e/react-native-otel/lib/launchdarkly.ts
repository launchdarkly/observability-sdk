import {
	ReactNativeLDClient,
	AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk'
import { Observability } from '@launchdarkly/observability-react-native'
import Constants from 'expo-constants'

export let ldClient: ReactNativeLDClient | null = null

const OTLP_HTTP = 'https://otel.observability.app.launchdarkly.com:4318'
const OTLP_HTTP_DEV = 'http://localhost:4318'

// TODO: Figure out why this isn't available in the device builds. Also seems to
// be unavailable after hot reload in the simulator. Can hardcode here for now
// to override.
const mobileKey = Constants.expoConfig?.extra?.sdkKey
const otlpEndpoint = OTLP_HTTP // __DEV__ ? OTLP_HTTP_DEV : OTLP_HTTP
const serviceName = 'react-native-otel'
let initialized = false

export async function initializeLaunchDarkly() {
	try {
		if (initialized) {
			console.log('LaunchDarkly client already initialized')
			return
		}

		if (!mobileKey) {
			console.warn(
				'LaunchDarkly mobile key not configured. Observability will still be initialized for demo purposes.',
			)
			return
		}

		ldClient = new ReactNativeLDClient(
			mobileKey,
			AutoEnvAttributes.Enabled,
			{
				plugins: [
					new Observability({
						serviceName,
						serviceVersion:
							Constants.expoConfig?.version || '1.0.0',
						otlpEndpoint,
						debug: __DEV__,
						customHeaders: {
							'x-service-name': serviceName,
							'x-environment': __DEV__
								? 'development'
								: 'production',
						},
						sessionTimeout: 60 * 60 * 1000, // 1 hour
					}),
				],
			},
		)
		console.log(
			'LaunchDarkly client initialized - sending data to',
			otlpEndpoint,
		)

		await ldClient.identify({
			key: '1234567890',
			kind: 'device',
			anonymous: true,
		})
	} catch (error) {
		console.error('Failed to initialize LaunchDarkly:', error)
	}
}
