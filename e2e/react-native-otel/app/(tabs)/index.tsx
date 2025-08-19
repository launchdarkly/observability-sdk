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

		// Test that error reporting is working
		console.log(
			'Testing error reporting with a message to verify console error handler',
		)
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

				// wait to break out of current context
				await new Promise((resolve) => setTimeout(resolve, 100))

				const context = LDObserve.getContextFromSpan(span)
				LDObserve.startActiveSpan(
					'api.fetchData.child',
					async (childSpan) => {
						childSpan.setAttribute('context', 'test')
						childSpan.end()
					},
					{},
					context,
				)

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

	const handleNestedOperations = () => {
		LDObserve.startActiveSpan('parent-operation', (parentSpan) => {
			const parentContext = parentSpan.spanContext()

			parentSpan.setAttribute('operation.type', 'complex')
			parentSpan.setAttribute('component', 'HomeScreen')
			parentSpan.setAttribute('test.scenario', 'context_propagation')

			LDObserve.startActiveSpan('child-operation-1', (childSpan) => {
				const childContext = childSpan.spanContext()

				childSpan.setAttribute('step', '1')
				childSpan.setAttribute('operation.nested', true)

				LDObserve.startActiveSpan(
					'sync-grandchild',
					(grandchildSpan) => {
						grandchildSpan.setAttribute('step', '1.1')
						grandchildSpan.setAttribute('sync', true)
						grandchildSpan.end()
					},
				)

				const currentTraceId = parentContext.traceId
				const currentSpanId = childContext.spanId

				setTimeout(() => {
					const context = LDObserve.getContextFromSpan(parentSpan)
					LDObserve.startActiveSpan(
						'async-child',
						(asyncSpan) => {
							const asyncContext = asyncSpan.spanContext()

							asyncSpan.setAttribute('async', true)
							asyncSpan.setAttribute('step', '1.2')
							asyncSpan.setAttribute('delay_ms', 100)
							asyncSpan.setAttribute(
								'parent_trace_id',
								currentTraceId,
							)
							asyncSpan.setAttribute(
								'expected_parent_span_id',
								currentSpanId,
							)

							LDObserve.recordLog(
								'Async operation completed',
								'info',
								{
									step: '1.2',
									parentOperation: 'parent-operation',
									contextLost:
										asyncContext.traceId !== currentTraceId,
								},
							)

							asyncSpan.end()
						},
						{},
						context,
					)
				}, 100)

				childSpan.end()
			})

			LDObserve.startActiveSpan('child-operation-2', (childSpan2) => {
				childSpan2.setAttribute('step', '2')
				childSpan2.setAttribute('operation.parallel', true)

				LDObserve.recordCount({
					name: 'nested_operations',
					value: 1,
					attributes: {
						operation: 'child-operation-2',
						step: '2',
					},
				})

				childSpan2.end()
			})

			parentSpan.end()
		})

		Alert.alert(
			'Span Hierarchy Test',
			'Nested spans with context propagation have been created.',
		)
	}

	// Exception test handlers
	const handleUncaughtException = () => {
		// This will cause an uncaught exception that should be caught by the global handler
		setTimeout(() => {
			// Using setTimeout to ensure it's not caught by React's error boundary
			const obj: any = null
			obj.nonExistentMethod() // Will throw TypeError: Cannot read property 'nonExistentMethod' of null
		}, 100)
	}

	const handleUnhandledPromiseRejection = () => {
		// This creates an unhandled Promise rejection
		setTimeout(() => {
			// Using setTimeout to escape from the current execution context
			new Promise((resolve, reject) => {
				// Explicit rejection without a catch handler
				reject(new Error('Unhandled Promise Rejection Test'))
			})
			// Log to confirm execution
			console.log('Unhandled Promise rejection triggered')
		}, 0)
	}

	const handleRecursiveError = () => {
		// This creates a stack overflow error
		// Wrap in setTimeout to bypass React's error boundaries
		setTimeout(() => {
			const recursiveFunction = (depth: number = 0): number => {
				// Add some protection to prevent actually crashing the app
				if (depth > 5000) {
					throw new Error(
						'Maximum recursion depth reached (stack overflow simulation)',
					)
				}
				return recursiveFunction(depth + 1)
			}

			recursiveFunction()
		}, 0)
	}

	const handleAsyncError = async () => {
		// This creates an error in an async function
		// Use setTimeout to ensure we're out of the current execution context
		setTimeout(() => {
			const makeAsyncError = async () => {
				await new Promise((resolve) => setTimeout(resolve, 500))
				throw new Error('Async operation failed')
			}

			// Run without awaiting or catching the error to ensure it becomes unhandled
			makeAsyncError()

			// Add a log to help track when the error should occur
			console.log('Async error scheduled (should occur in ~500ms)')
		}, 0)
	}

	const handleNetworkError = () => {
		// This creates a network error by attempting to fetch from a non-existent URL
		setTimeout(() => {
			console.log('Triggering network error to non-existent domain')
			fetch('https://non-existent-domain-123456789.xyz').then(
				(response) => response.json(),
			)
			// Intentionally no catch handler to make it unhandled

			// Using a separate fetch to ensure we're getting a real network error
			fetch('https://invalid-url-that-will-fail.error')
		}, 0)
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

				<Pressable
					style={styles.button}
					onPress={handleNestedOperations}
				>
					<ThemedText style={styles.buttonText}>
						Test Span Hierarchy
					</ThemedText>
				</Pressable>

				<ThemedText type="subtitle" style={{ marginTop: 16 }}>
					Error Testing
				</ThemedText>

				<Pressable
					style={[styles.button, styles.errorButton]}
					onPress={handleUncaughtException}
				>
					<ThemedText style={styles.buttonText}>
						Trigger Uncaught Exception
					</ThemedText>
				</Pressable>

				<Pressable
					style={[styles.button, styles.errorButton]}
					onPress={handleUnhandledPromiseRejection}
				>
					<ThemedText style={styles.buttonText}>
						Trigger Unhandled Promise Rejection
					</ThemedText>
				</Pressable>

				<Pressable
					style={[styles.button, styles.errorButton]}
					onPress={handleRecursiveError}
				>
					<ThemedText style={styles.buttonText}>
						Trigger Stack Overflow Error
					</ThemedText>
				</Pressable>

				<Pressable
					style={[styles.button, styles.errorButton]}
					onPress={handleAsyncError}
				>
					<ThemedText style={styles.buttonText}>
						Trigger Async Error
					</ThemedText>
				</Pressable>

				<Pressable
					style={[styles.button, styles.errorButton]}
					onPress={handleNetworkError}
				>
					<ThemedText style={styles.buttonText}>
						Trigger Network Error
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
	errorButton: {
		backgroundColor: '#FF3B30', // Red color for error buttons
	},
	buttonText: {
		color: 'white',
		fontWeight: 'bold',
	},
})
