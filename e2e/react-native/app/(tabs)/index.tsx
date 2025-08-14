import { Image, StyleSheet, Platform, Alert } from 'react-native'
import { useEffect, useState } from 'react'

import { HelloWave } from '@/components/HelloWave'
import ParallaxScrollView from '@/components/ParallaxScrollView'
import { ThemedText } from '@/components/ThemedText'
import { ThemedView } from '@/components/ThemedView'
import initializeObservability, { LDObserve } from '../observability'

export default function HomeScreen() {
	const [isInitialized, setIsInitialized] = useState(false)

	useEffect(() => {
		const init = async () => {
			try {
				await initializeObservability()
				setIsInitialized(true)
				
				// Log that the home screen loaded
				LDObserve.recordLog('Home screen loaded successfully', 'info', {
					screen: 'HomeScreen',
					timestamp: new Date().toISOString(),
					platform: Platform.OS
				})
			} catch (error) {
				console.error('Failed to initialize observability:', error)
				Alert.alert(
					'Initialization Error',
					'Failed to initialize observability. Check console for details.'
				)
			}
		}

		init()
	}, [])

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
				<ThemedText type="title">LaunchDarkly Observability</ThemedText>
				<HelloWave />
			</ThemedView>
			
			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">
					🔧 Error Instrumentation Test App
				</ThemedText>
				<ThemedText>
					This app demonstrates automatic error capture and reporting 
					using the LaunchDarkly Observability React Native SDK.
				</ThemedText>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">
					📊 Status: {isInitialized ? '✅ Ready' : '⏳ Initializing...'}
				</ThemedText>
				<ThemedText>
					{isInitialized 
						? 'Observability SDK is initialized and capturing errors automatically.'
						: 'Initializing LaunchDarkly client with observability plugin...'
					}
				</ThemedText>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">🧪 Test Error Capture</ThemedText>
				<ThemedText>
					Go to the{' '}
					<ThemedText type="defaultSemiBold">Explore</ThemedText> tab 
					to access the comprehensive error testing suite. You can trigger 
					different types of errors and see them captured in real-time.
				</ThemedText>
			</ThemedView>

			<ThemedView style={styles.stepContainer}>
				<ThemedText type="subtitle">🎯 What's Being Captured</ThemedText>
				<ThemedText>
					• Unhandled JavaScript exceptions{'\n'}
					• Unhandled promise rejections{'\n'}
					• Console errors and warnings{'\n'}
					• React component errors{'\n'}
					• Manual error reports{'\n'}
					• Network errors (filtered){'\n'}
					• Error deduplication and sampling
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
})
