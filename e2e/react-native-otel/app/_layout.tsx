import {
	DarkTheme,
	DefaultTheme,
	ThemeProvider,
} from '@react-navigation/native'
import { useFonts } from 'expo-font'
import { Stack } from 'expo-router'
import * as SplashScreen from 'expo-splash-screen'
import { StatusBar } from 'expo-status-bar'
import { useEffect } from 'react'
import 'react-native-reanimated'

import { useColorScheme } from '@/hooks/useColorScheme'
import { initializeLaunchDarkly } from '@/lib/launchdarkly'
import { LDObserve } from '@launchdarkly/observability-react-native'
import Constants from 'expo-constants'
import { Platform, LogBox } from 'react-native'

// Enable detailed promise rejection logs - these are normally suppressed in React Native
LogBox.ignoreLogs(['Possible Unhandled Promise Rejection'])

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync()

// Will need to be buffered
LDObserve.startActiveSpan('RootLayoutSpan', (span) => {
	span.end()
	return
})

LDObserve.recordLog('appLoading', 'info', {
	component: 'RootLayout',
	device_name: Constants.deviceName,
	os: Platform.OS,
	os_version: Platform.Version,
})

// Initialize LaunchDarkly before initial render.
initializeLaunchDarkly()

export default function RootLayout() {
	const colorScheme = useColorScheme()
	const [loaded] = useFonts({
		SpaceMono: require('../assets/fonts/SpaceMono-Regular.ttf'),
	})

	// Add global error handlers directly in the React component
	useEffect(() => {
		// Direct error handler for promise rejections
		const errorHandler = (error: any) => {
			console.log('[RootLayout] Caught unhandled error:', error)
			if (error instanceof Error) {
				LDObserve.recordError(error, {
					'error.unhandled': true,
					'error.caught_by': 'root_component_handler',
				})
			}
		}

		// Register a direct listener for unhandled rejections at the app level
		const rejectionHandler = (event: any) => {
			const error = event.reason || new Error('Unknown promise rejection')
			console.log(
				'[RootLayout] Caught unhandled promise rejection:',
				error,
			)
			LDObserve.recordError(
				error instanceof Error ? error : new Error(String(error)),
				{
					'error.unhandled': true,
					'error.caught_by': 'root_component_promise_handler',
					'promise.handled': false,
				},
			)
		}

		// Network error handler to catch fetch errors
		const networkErrorHandler = (error: any) => {
			console.log('[RootLayout] Caught network error:', error)
			LDObserve.recordError(
				error instanceof Error ? error : new Error(String(error)),
				{
					'error.unhandled': true,
					'error.caught_by': 'root_component_network_handler',
					'error.type': 'network',
				},
			)
		}

		// Set up the handlers
		if (global.ErrorUtils) {
			const originalGlobalHandler = global.ErrorUtils.getGlobalHandler()
			global.ErrorUtils.setGlobalHandler((error, isFatal) => {
				errorHandler(error)
				if (originalGlobalHandler) {
					originalGlobalHandler(error, isFatal)
				}
			})
		}

		// React Native doesn't fully support the standard addEventListener API for unhandledrejection
		// This is a workaround using Promise patches
		const originalPromiseReject = Promise.reject
		Promise.reject = function (reason) {
			const result = originalPromiseReject.call(this, reason)
			setTimeout(() => {
				// If the rejection isn't handled in the next tick, report it
				if (!result._handled) {
					rejectionHandler({ reason })
				}
			}, 0)
			return result
		}

		// Patch fetch to catch network errors
		const originalFetch = global.fetch
		global.fetch = function (...args) {
			return originalFetch.apply(this, args).catch((error) => {
				networkErrorHandler(error)
				throw error // re-throw to preserve original behavior
			})
		} as typeof fetch

		return () => {
			// Cleanup if component unmounts (unlikely for root layout)
			if (global.ErrorUtils && originalGlobalHandler) {
				global.ErrorUtils.setGlobalHandler(originalGlobalHandler)
			}
			Promise.reject = originalPromiseReject
			global.fetch = originalFetch
		}
	}, [])

	useEffect(() => {
		if (loaded) {
			SplashScreen.hideAsync()
		}
	}, [loaded])

	if (!loaded) {
		// Async font loading only occurs in development.
		return null
	}

	return (
		<ThemeProvider
			value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}
		>
			<Stack>
				<Stack.Screen name="(tabs)" options={{ headerShown: false }} />
				<Stack.Screen name="+not-found" />
			</Stack>
			<StatusBar style="auto" />
		</ThemeProvider>
	)
}
