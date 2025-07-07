import {
	ReactNativeLDClient,
	AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk'
import { Observability } from '@launchdarkly/observability-react-native'
import Constants from 'expo-constants'

let ldClient: ReactNativeLDClient | null = null

const user = {
	key: 'example-user',
	name: 'Example User',
	email: 'example@test.com',
	anonymous: false,
}

const OTLP_HTTP = 'https://otel.observability.app.launchdarkly.com:4318'
const OTLP_HTTP_DEV = 'http://localhost:4318'

const mobileKey = Constants.expoConfig?.extra?.sdkKey
const otlpEndpoint = __DEV__ ? OTLP_HTTP_DEV : OTLP_HTTP
const serviceName = 'react-native-otel'

export async function initializeLaunchDarkly() {
	try {
		if (!mobileKey) {
			console.warn(
				'LaunchDarkly mobile key not configured. Observability will still be initialized for demo purposes.',
			)
			return
		}

		// Initialize LaunchDarkly client with observability plugin
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

		await ldClient.identify(user)
		console.log('LaunchDarkly client initialized with observability')
		console.log(`OTLP endpoint: ${otlpEndpoint}`)
	} catch (error) {
		console.error('Failed to initialize LaunchDarkly:', error)
	}
}

export function getLDClient(): ReactNativeLDClient | null {
	return ldClient
}

export async function closeLDClient() {
	if (ldClient) {
		await ldClient.close()
		ldClient = null
	}
}
