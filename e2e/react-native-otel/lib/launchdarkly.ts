import {
	ReactNativeLDClient,
	AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk'
import { Observability } from '@launchdarkly/observability-react-native'
import Constants from 'expo-constants'

let ldClient: ReactNativeLDClient | null = null

// Example user context - in a real app, this would come from your authentication system
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
						serviceName: 'react-native-otel-demo',
						serviceVersion:
							Constants.expoConfig?.version || '1.0.0',
						otlpEndpoint,
						enableTracing: true,
						enableLogs: true,
						enableMetrics: true,
						enableErrorTracking: true,
						enablePerformanceMonitoring: true,
						enableConsoleLogging: true, // Enable for debugging
						debug: __DEV__, // Enable debug mode in development
						customHeaders: {
							// Add any custom headers for your OTLP endpoint
							'x-service-name': 'react-native-otel-demo',
							'x-environment': __DEV__
								? 'development'
								: 'production',
						},
						sessionTimeout: 30 * 60 * 1000, // 30 minutes
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
