import {
	ReactNativeLDClient,
	AutoEnvAttributes,
	LDUser,
} from '@launchdarkly/react-native-client-sdk'
import {
	Observability,
	LDObserve,
	type ReactNativeOptions,
} from '@launchdarkly/observability-react-native'

// Test mobile key - replace with your actual key
const MOBILE_KEY = 'mob-test-key-123'

// Test user context
const user: LDUser = {
	key: 'test-user-' + Math.random().toString(36).substr(2, 9),
	name: 'Test User',
	email: 'test@example.com',
	custom: {
		appVersion: '1.0.0',
		platform: 'react-native',
	},
}

// Observability configuration (error handling is enabled by default)
const observabilityOptions: ReactNativeOptions = {
	serviceName: 'react-native-error-test-app',
	serviceVersion: '1.0.0',
	debug: true, // Enable debug logging

	// Optional error handling configuration
	errorHandling: {
		errorSampleRate: 1.0, // Capture all errors for testing
		beforeSend: (error, context) => {
			// Log errors for debugging
			console.log('📊 Capturing error:', {
				message: error.message,
				type: context.type,
				source: context.source,
				fatal: context.fatal,
			})
			return error // Allow all errors through
		},
	},

	// Custom attributes for testing
	resourceAttributes: {
		'test.environment': 'e2e',
		'test.app': 'react-native-error-testing',
	},
}

// Initialize LaunchDarkly client with observability plugin
const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
	plugins: [new Observability(observabilityOptions)],
	// Additional client options
	streamUri: 'https://clientstream.launchdarkly.com',
	baseUri: 'https://sdk.launchdarkly.com',
	eventsUri: 'https://events.launchdarkly.com',
	connectionTimeoutMillis: 10000,
	enableAutoEnvAttributes: true,
})

// Initialize the client with the test user
let isInitialized = false

export async function initializeObservability(): Promise<void> {
	if (isInitialized) {
		return
	}

	try {
		console.log('🚀 Initializing LaunchDarkly with Observability...')
		await client.identify(user, { waitForNetworkResults: false })
		isInitialized = true
		console.log('✅ LaunchDarkly initialized successfully')

		// Test that observability is working
		LDObserve.recordLog('Observability initialized', 'info', {
			userId: user.key,
			timestamp: new Date().toISOString(),
		})
	} catch (error) {
		console.error('❌ Failed to initialize LaunchDarkly:', error)
		throw error
	}
}

// Export the client and observability tools
export { client, LDObserve }

// Helper functions for testing different error scenarios
export const ErrorTestUtils = {
	// Test unhandled JavaScript exception
	triggerUnhandledException: () => {
		setTimeout(() => {
			throw new Error('Test unhandled exception from setTimeout')
		}, 100)
	},

	// Test unhandled promise rejection
	triggerUnhandledRejection: () => {
		new Promise((_, reject) => {
			setTimeout(() => {
				reject(new Error('Test unhandled promise rejection'))
			}, 100)
		})
		// Don't add .catch() - we want it to be unhandled
	},

	// Test console error
	triggerConsoleError: () => {
		console.error('Test console error', {
			errorCode: 'TEST_001',
			timestamp: new Date().toISOString(),
			userAgent: 'React Native Test',
		})
	},

	// Test manual error reporting
	triggerManualError: () => {
		const error = new Error('Test manual error report')
		error.stack =
			'Error: Test manual error report\n    at triggerManualError\n    at TestComponent'

		LDObserve.recordError(error, {
			'error.manual': true,
			'error.test_scenario': 'manual_reporting',
			component: 'ErrorTestUtils',
		})
	},

	// Test network error (will be filtered by error instrumentation)
	triggerNetworkError: async () => {
		try {
			await fetch('https://nonexistent-domain-12345.com/api/test')
		} catch (error) {
			// This should be filtered out by network error detection
			console.log('Network error caught:', error)
		}
	},

	// Test React component error (would need error boundary)
	triggerReactError: () => {
		throw new Error('Test React component error')
	},

	// Test sampling by generating multiple similar errors
	triggerSamplingTest: () => {
		for (let i = 0; i < 5; i++) {
			setTimeout(() => {
				console.error(`Sampling test error ${i + 1}`, {
					iteration: i + 1,
				})
			}, i * 500)
		}
	},
}

// Export initialization function
export default initializeObservability
