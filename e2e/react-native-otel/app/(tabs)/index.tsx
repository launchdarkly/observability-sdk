import { Image, StyleSheet, Alert, Pressable } from 'react-native'
import { useState, useEffect } from 'react'

import { HelloWave } from '@/components/HelloWave'
import ParallaxScrollView from '@/components/ParallaxScrollView'
import { ThemedText } from '@/components/ThemedText'
import { ThemedView } from '@/components/ThemedView'
import { LDObserve } from '@launchdarkly/observability-react-native'

export default function HomeScreen() {
	const [sessionInfo, setSessionInfo] = useState<any>(null)

	useEffect(() => {
		// Demo: Automatic session tracking
		const loadSessionInfo = async () => {
			try {
				const info = await LDObserve.getSessionInfo()
				setSessionInfo(info)
			} catch (error) {
				console.error('Failed to get session info:', error)
			}
		}

		loadSessionInfo()

		// Demo: Custom trace on component mount
		const span = LDObserve.startSpan('HomeScreen.mount', {
			attributes: {
				component: 'HomeScreen',
				'screen.name': 'home',
			},
		})
		span.end()

		// Demo: Custom log
		LDObserve.recordLog('HomeScreen loaded', 'info', undefined, undefined, {
			component: 'HomeScreen',
			loadTime: Date.now(),
		})

		// Demo: Custom metric
		LDObserve.recordIncr({
			name: 'screen_views',
			value: 1,
			attributes: {
				screen: 'home',
				component: 'HomeScreen',
			},
		})
	}, [])

	const handleTestError = () => {
		try {
			// Intentionally throw an error for demonstration
			throw new Error('This is a test error for observability demo')
		} catch (error) {
			// Demo: Custom error recording
			LDObserve.recordError(error as Error, undefined, undefined, {
				action: 'test_error_button',
				userInitiated: true,
			})

			Alert.alert(
				'Error Recorded',
				'Test error has been recorded in observability',
			)
		}
	}

	const handleNetworkRequest = async () => {
		try {
			// Demo: Network request with automatic tracing
			LDObserve.startActiveSpan('api.fetchData', async (span) => {
				span.setAttribute('http.method', 'GET')
				span.setAttribute(
					'http.url',
					'https://jsonplaceholder.typicode.com/posts/1',
				)

				const response = await fetch(
					'https://jsonplaceholder.typicode.com/posts/1',
				)
				const data = await response.json()

				span.setAttribute('http.status_code', response.status)
				span.setAttribute('response.size', JSON.stringify(data).length)

				LDObserve.recordLog(
					'Network request completed',
					'info',
					undefined,
					undefined,
					{
						method: 'GET',
						url: 'https://jsonplaceholder.typicode.com/posts/1',
						status: response.status,
						responseSize: JSON.stringify(data).length,
					},
				)

				// Demo: Custom metric for network request
				LDObserve.recordHistogram({
					name: 'api_request_duration',
					value: Math.random() * 1000, // Simulated request duration
					attributes: {
						endpoint: '/posts/1',
						method: 'GET',
						status: response.status.toString(),
					},
				})

				Alert.alert('Success', `Fetched post: ${data.title}`)
			})
		} catch (error) {
			LDObserve.recordError(error as Error, undefined, undefined, {
				action: 'network_request',
				endpoint: '/posts/1',
			})
			Alert.alert('Error', 'Network request failed')
		}
	}

	const handleCustomMetric = () => {
		// Demo: Various custom metrics
		LDObserve.recordCount({
			name: 'user_actions',
			value: 1,
			attributes: {
				action: 'button_click',
				button: 'custom_metric',
			},
		})

		LDObserve.recordMetric({
			name: 'user_engagement_score',
			value: Math.floor(Math.random() * 100),
			attributes: {
				screen: 'home',
				action: 'metric_demo',
			},
		})

		Alert.alert('Metric Recorded', 'Custom metrics have been recorded')
	}

	const handleSetUserId = async () => {
		try {
			const userId = `user_${Date.now()}`
			await LDObserve.setUserId(userId)

			// Refresh session info to show updated user
			const info = await LDObserve.getSessionInfo()
			setSessionInfo(info)

			Alert.alert('User ID Set', `User ID set to: ${userId}`)
		} catch (error) {
			LDObserve.recordError(error as Error, undefined, undefined, {
				action: 'set_user_id',
			})
			Alert.alert('Error', 'Failed to set user ID')
		}
	}

	return (
		<ParallaxScrollView
			headerBackgroundColor={{ light: '#A1CEDC', dark: '#1D3D47' }}
			headerImage={
				<Image
					source={require('@/assets/images/partial-react-logo.png')}
					style={styles.reactLogo}
				/>
			}
		>
			<ThemedView style={styles.titleContainer}>
				<ThemedText type="title">Observability Demo</ThemedText>
				<HelloWave />
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Session Information</ThemedText>
				{sessionInfo ? (
					<ThemedView style={styles.infoContainer}>
						<ThemedText>
							Session ID: {sessionInfo.sessionId || 'N/A'}
						</ThemedText>
						<ThemedText>
							User ID: {sessionInfo.userId || 'Not set'}
						</ThemedText>
						<ThemedText>
							Device ID: {sessionInfo.deviceId || 'N/A'}
						</ThemedText>
						<ThemedText>
							App Version: {sessionInfo.appVersion || 'N/A'}
						</ThemedText>
						<ThemedText>
							Platform: {sessionInfo.platform || 'N/A'}
						</ThemedText>
					</ThemedView>
				) : (
					<ThemedText>Loading session info...</ThemedText>
				)}
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Observability Features</ThemedText>
				<ThemedText>
					This app demonstrates the LaunchDarkly Observability React
					Native SDK with automatic:
				</ThemedText>
				<ThemedText>
					• Tracing of network requests and custom operations
				</ThemedText>
				<ThemedText>• Logging with session context</ThemedText>
				<ThemedText>• Metrics collection and custom metrics</ThemedText>
				<ThemedText>• Error tracking and reporting</ThemedText>
				<ThemedText>• Session management</ThemedText>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">
					Test Observability Features
				</ThemedText>

				<Pressable style={styles.button} onPress={handleTestError}>
					<ThemedText style={styles.buttonText}>
						Record Test Error
					</ThemedText>
				</Pressable>

				<Pressable style={styles.button} onPress={handleNetworkRequest}>
					<ThemedText style={styles.buttonText}>
						Make Network Request
					</ThemedText>
				</Pressable>

				<Pressable style={styles.button} onPress={handleCustomMetric}>
					<ThemedText style={styles.buttonText}>
						Record Custom Metrics
					</ThemedText>
				</Pressable>

				<Pressable style={styles.button} onPress={handleSetUserId}>
					<ThemedText style={styles.buttonText}>
						Set Random User ID
					</ThemedText>
				</Pressable>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Console Logging</ThemedText>
				<ThemedText>
					Check your development console to see automatic logging of:
				</ThemedText>
				<ThemedText>
					• Console messages (hooked automatically)
				</ThemedText>
				<ThemedText>• Trace spans and events</ThemedText>
				<ThemedText>• Error details and stack traces</ThemedText>
				<ThemedText>• Performance metrics</ThemedText>
			</ThemedView>
		</ParallaxScrollView>
	)
}

const styles = StyleSheet.create({
	titleContainer: {
		flexDirection: 'row',
		alignItems: 'center',
		gap: 8,
	},
	stepContainer: {
		gap: 8,
		marginBottom: 8,
	},
	reactLogo: {
		height: 178,
		width: 290,
		bottom: 0,
		left: 0,
		position: 'absolute',
	},
	infoContainer: {
		backgroundColor: 'rgba(0,0,0,0.1)',
		padding: 16,
		borderRadius: 8,
		gap: 4,
	},
	button: {
		backgroundColor: '#007AFF',
		padding: 12,
		borderRadius: 8,
		marginVertical: 4,
		alignItems: 'center',
	},
	buttonText: {
		color: 'white',
		fontWeight: 'bold',
	},
})
