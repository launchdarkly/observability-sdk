import { Image, StyleSheet, Alert, Pressable } from 'react-native'
import { useState, useEffect } from 'react'

import { HelloWave } from '@/components/HelloWave'
import ParallaxScrollView from '@/components/ParallaxScrollView'
import { ThemedText } from '@/components/ThemedText'
import { ThemedView } from '@/components/ThemedView'
import { LDObserve } from '@launchdarkly/observability-react-native'
import { ldClient } from '../../lib/launchdarkly'

export default function HomeScreen() {
	const [context, setContext] = useState<any>(null)
	const [sessionInfo, setSessionInfo] = useState<any>(null)
	const [flagEnabled, setFlagEnabled] = useState<boolean>(false)

	useEffect(() => {
		if (ldClient) {
			const flagEnabled = ldClient.variation('test-flag', false)
			setFlagEnabled(flagEnabled)
			setContext(ldClient.getContext())
		}
	}, [])

	useEffect(() => {
		const getSessionInfo = () => {
			const info = LDObserve.getSessionInfo()

			if (Object.keys(info).length > 0) {
				setSessionInfo(info)
			} else {
				setTimeout(() => {
					getSessionInfo()
				}, 100)
			}
		}

		getSessionInfo()
	}, [])

	useEffect(() => {
		const span = LDObserve.startSpan('HomeScreen.mount', {
			attributes: {
				component: 'HomeScreen',
				'screen.name': 'home',
			},
		})
		span.end()

		LDObserve.recordLog('HomeScreen loaded', 'info', {
			component: 'HomeScreen',
			loadTime: Date.now(),
		})

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
			throw new Error('This is a test error for observability demo')
		} catch (error) {
			LDObserve.recordError(error as Error, {
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

				LDObserve.recordLog('Network request completed', 'info', {
					method: 'GET',
					url: 'https://jsonplaceholder.typicode.com/posts/1',
					status: response.status,
					responseSize: JSON.stringify(data).length,
				})

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
			LDObserve.recordError(error as Error, {
				action: 'network_request',
				endpoint: '/posts/1',
			})
			Alert.alert('Error', 'Network request failed')
		}
	}

	const handleCustomMetric = () => {
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

	const handleIdentifyUser = async () => {
		try {
			const userId = `user_${Date.now()}`
			ldClient?.identify({
				kind: 'multi',
				user: {
					key: userId,
					kind: 'user',
					email: 'test@test.com',
					anonymous: false,
				},
			})

			setContext(ldClient?.getContext())
			setSessionInfo(LDObserve.getSessionInfo())

			Alert.alert('User ID Set', `User ID set to: ${userId}`)
		} catch (error) {
			LDObserve.recordError(error as Error, {
				action: 'set_user_id',
			})
			Alert.alert('Error', 'Failed to set user ID')
		}
	}

	const handleRecordLog = () => {
		LDObserve.recordLog('Test log', 'info', {
			component: 'HomeScreen',
		})
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
				<ThemedText type="title">O11y Demo</ThemedText>
				<HelloWave />
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Test Features</ThemedText>

				<ThemedText type="default">
					The{' '}
					<ThemedText type="defaultSemiBold">test-flag</ThemedText> is{' '}
					<ThemedText type="defaultSemiBold">
						{flagEnabled ? 'enabled' : 'disabled'}
					</ThemedText>
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

				<Pressable style={styles.button} onPress={handleRecordLog}>
					<ThemedText style={styles.buttonText}>
						Record Log
					</ThemedText>
				</Pressable>

				<Pressable style={styles.button} onPress={handleCustomMetric}>
					<ThemedText style={styles.buttonText}>
						Record Custom Metrics
					</ThemedText>
				</Pressable>

				<Pressable style={styles.button} onPress={handleIdentifyUser}>
					<ThemedText style={styles.buttonText}>
						Identify User
					</ThemedText>
				</Pressable>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Session Information</ThemedText>
				{sessionInfo ? (
					<ThemedView style={styles.infoContainer}>
						<ThemedText>
							{JSON.stringify(sessionInfo, null, 2)}
						</ThemedText>
					</ThemedView>
				) : (
					<ThemedText>Loading session info...</ThemedText>
				)}
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Context Information</ThemedText>
				<ThemedView style={styles.infoContainer}>
					<ThemedText>{JSON.stringify(context, null, 2)}</ThemedText>
				</ThemedView>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">Viewing OTel Signals</ThemedText>
				<ThemedText>
					Run the OTel collector to view the signals being emitted by
					this app. See README for instructions.
				</ThemedText>
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
