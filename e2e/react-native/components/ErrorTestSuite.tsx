import React, { useState, useEffect } from 'react'
import {
	View,
	Text,
	TouchableOpacity,
	StyleSheet,
	ScrollView,
	Alert,
	Switch,
} from 'react-native'
import { ErrorTestUtils, LDObserve } from '@/app/observability'

interface TestResult {
	name: string
	status: 'pending' | 'success' | 'error'
	message: string
	timestamp?: Date
}

interface ErrorBoundaryState {
	hasError: boolean
	error?: Error
}

// Error Boundary Component for testing React errors
class ErrorBoundary extends React.Component<
	{ children: React.ReactNode; onError?: (error: Error) => void },
	ErrorBoundaryState
> {
	constructor(props: any) {
		super(props)
		this.state = { hasError: false }
	}

	static getDerivedStateFromError(error: Error): ErrorBoundaryState {
		return { hasError: true, error }
	}

	componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
		console.log('Error Boundary caught error:', error)
		console.log('Error Info:', errorInfo)

		// Manually report the error with React context
		LDObserve.recordError(error, {
			'error.boundary': true,
			'error.component_stack': errorInfo.componentStack,
			'react.error_boundary': 'ErrorTestSuite',
		})

		this.props.onError?.(error)
	}

	render() {
		if (this.state.hasError) {
			return (
				<View style={styles.errorBoundary}>
					<Text style={styles.errorText}>
						🚨 React Error Caught: {this.state.error?.message}
					</Text>
					<TouchableOpacity
						style={styles.resetButton}
						onPress={() =>
							this.setState({ hasError: false, error: undefined })
						}
					>
						<Text style={styles.buttonText}>Reset</Text>
					</TouchableOpacity>
				</View>
			)
		}

		return this.props.children
	}
}

// Component that throws an error when rendered
const ErrorThrowingComponent: React.FC<{ shouldThrow: boolean }> = ({
	shouldThrow,
}) => {
	if (shouldThrow) {
		throw new Error('Intentional React component error for testing')
	}
	return (
		<Text style={styles.successText}>
			✅ Component rendered successfully
		</Text>
	)
}

export const ErrorTestSuite: React.FC = () => {
	const [testResults, setTestResults] = useState<TestResult[]>([])
	const [autoErrorsEnabled, setAutoErrorsEnabled] = useState(false)
	const [throwReactError, setThrowReactError] = useState(false)

	// Auto-generate errors for continuous testing
	useEffect(() => {
		if (!autoErrorsEnabled) return

		const interval = setInterval(() => {
			const randomTests = [
				() => ErrorTestUtils.triggerConsoleError(),
				() => ErrorTestUtils.triggerUnhandledRejection(),
				() => ErrorTestUtils.triggerManualError(),
			]

			const randomTest =
				randomTests[Math.floor(Math.random() * randomTests.length)]
			randomTest()
		}, 10000) // Every 10 seconds

		return () => clearInterval(interval)
	}, [autoErrorsEnabled])

	const addTestResult = (
		name: string,
		status: TestResult['status'],
		message: string,
	) => {
		const result: TestResult = {
			name,
			status,
			message,
			timestamp: new Date(),
		}

		setTestResults((prev) => [result, ...prev.slice(0, 9)]) // Keep last 10 results
	}

	const runTest = async (
		testName: string,
		testFn: () => void | Promise<void>,
	) => {
		try {
			addTestResult(testName, 'pending', 'Running test...')
			await testFn()
			addTestResult(testName, 'success', 'Test executed successfully')
		} catch (error) {
			addTestResult(testName, 'error', `Test failed: ${error}`)
		}
	}

	const testScenarios = [
		{
			name: 'Unhandled Exception',
			description: 'Throws an error in setTimeout',
			test: () => ErrorTestUtils.triggerUnhandledException(),
			color: '#FF6B6B',
		},
		{
			name: 'Unhandled Rejection',
			description: 'Promise rejection without catch',
			test: () => ErrorTestUtils.triggerUnhandledRejection(),
			color: '#4ECDC4',
		},
		{
			name: 'Console Error',
			description: 'Logs error to console',
			test: () => ErrorTestUtils.triggerConsoleError(),
			color: '#45B7D1',
		},
		{
			name: 'Manual Error',
			description: 'Manually reported error',
			test: () => ErrorTestUtils.triggerManualError(),
			color: '#96CEB4',
		},
		{
			name: 'Network Error',
			description: 'Failed network request (filtered)',
			test: () => ErrorTestUtils.triggerNetworkError(),
			color: '#FECA57',
		},
		{
			name: 'Sampling Test',
			description: 'Multiple similar errors',
			test: () => ErrorTestUtils.triggerSamplingTest(),
			color: '#FF9FF3',
		},
		{
			name: 'React Error',
			description: 'Component error boundary',
			test: () => setThrowReactError(true),
			color: '#F38BA8',
		},
	]

	const clearResults = () => {
		setTestResults([])
	}

	return (
		<ScrollView style={styles.container}>
			<View style={styles.header}>
				<Text style={styles.title}>
					🧪 Error Instrumentation Test Suite
				</Text>
				<Text style={styles.subtitle}>
					Test automatic error capture in React Native
				</Text>
			</View>

			{/* Auto-generate errors toggle */}
			<View style={styles.autoErrorsContainer}>
				<Text style={styles.autoErrorsLabel}>
					Auto-generate errors:
				</Text>
				<Switch
					value={autoErrorsEnabled}
					onValueChange={setAutoErrorsEnabled}
					trackColor={{ false: '#767577', true: '#81b0ff' }}
					thumbColor={autoErrorsEnabled ? '#f5dd4b' : '#f4f3f4'}
				/>
			</View>

			{/* Test buttons */}
			<View style={styles.testsContainer}>
				<Text style={styles.sectionTitle}>Test Scenarios:</Text>
				{testScenarios.map((scenario, index) => (
					<TouchableOpacity
						key={index}
						style={[
							styles.testButton,
							{ backgroundColor: scenario.color },
						]}
						onPress={() => runTest(scenario.name, scenario.test)}
					>
						<Text style={styles.testButtonTitle}>
							{scenario.name}
						</Text>
						<Text style={styles.testButtonDescription}>
							{scenario.description}
						</Text>
					</TouchableOpacity>
				))}
			</View>

			{/* React Error Boundary Test */}
			<View style={styles.reactErrorContainer}>
				<Text style={styles.sectionTitle}>
					React Error Boundary Test:
				</Text>
				<ErrorBoundary
					onError={() => {
						addTestResult(
							'React Error',
							'success',
							'Error caught by boundary',
						)
						// Reset after a delay
						setTimeout(() => setThrowReactError(false), 2000)
					}}
				>
					<ErrorThrowingComponent shouldThrow={throwReactError} />
				</ErrorBoundary>
			</View>

			{/* Test Results */}
			<View style={styles.resultsContainer}>
				<View style={styles.resultsHeader}>
					<Text style={styles.sectionTitle}>Test Results:</Text>
					<TouchableOpacity
						style={styles.clearButton}
						onPress={clearResults}
					>
						<Text style={styles.clearButtonText}>Clear</Text>
					</TouchableOpacity>
				</View>

				{testResults.length === 0 ? (
					<Text style={styles.noResults}>No tests run yet</Text>
				) : (
					testResults.map((result, index) => (
						<View key={index} style={styles.resultItem}>
							<View style={styles.resultHeader}>
								<Text style={styles.resultName}>
									{result.name}
								</Text>
								<Text
									style={[
										styles.resultStatus,
										{
											color: getStatusColor(
												result.status,
											),
										},
									]}
								>
									{getStatusIcon(result.status)}{' '}
									{result.status}
								</Text>
							</View>
							<Text style={styles.resultMessage}>
								{result.message}
							</Text>
							{result.timestamp && (
								<Text style={styles.resultTimestamp}>
									{result.timestamp.toLocaleTimeString()}
								</Text>
							)}
						</View>
					))
				)}
			</View>

			{/* Instructions */}
			<View style={styles.instructionsContainer}>
				<Text style={styles.sectionTitle}>📋 Instructions:</Text>
				<Text style={styles.instruction}>
					1. Check your observability dashboard for captured errors
				</Text>
				<Text style={styles.instruction}>
					2. Errors should appear with proper context and attributes
				</Text>
				<Text style={styles.instruction}>
					3. Network errors should be filtered out automatically
				</Text>
				<Text style={styles.instruction}>
					4. Similar errors should be deduplicated within 5 seconds
				</Text>
				<Text style={styles.instruction}>
					5. Check console logs for debugging information
				</Text>
			</View>
		</ScrollView>
	)
}

const getStatusColor = (status: TestResult['status']) => {
	switch (status) {
		case 'success':
			return '#4CAF50'
		case 'error':
			return '#F44336'
		case 'pending':
			return '#FF9800'
		default:
			return '#757575'
	}
}

const getStatusIcon = (status: TestResult['status']) => {
	switch (status) {
		case 'success':
			return '✅'
		case 'error':
			return '❌'
		case 'pending':
			return '⏳'
		default:
			return '❓'
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: '#f5f5f5',
	},
	header: {
		padding: 20,
		backgroundColor: '#fff',
		borderBottomWidth: 1,
		borderBottomColor: '#eee',
	},
	title: {
		fontSize: 24,
		fontWeight: 'bold',
		color: '#333',
		marginBottom: 8,
	},
	subtitle: {
		fontSize: 16,
		color: '#666',
	},
	autoErrorsContainer: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'space-between',
		padding: 20,
		backgroundColor: '#fff',
		marginTop: 10,
	},
	autoErrorsLabel: {
		fontSize: 16,
		color: '#333',
	},
	testsContainer: {
		padding: 20,
	},
	sectionTitle: {
		fontSize: 18,
		fontWeight: 'bold',
		color: '#333',
		marginBottom: 15,
	},
	testButton: {
		padding: 15,
		borderRadius: 10,
		marginBottom: 10,
		elevation: 2,
		shadowColor: '#000',
		shadowOffset: { width: 0, height: 2 },
		shadowOpacity: 0.1,
		shadowRadius: 4,
	},
	testButtonTitle: {
		fontSize: 16,
		fontWeight: 'bold',
		color: '#fff',
		marginBottom: 4,
	},
	testButtonDescription: {
		fontSize: 14,
		color: '#fff',
		opacity: 0.9,
	},
	reactErrorContainer: {
		padding: 20,
		backgroundColor: '#fff',
		marginBottom: 10,
	},
	errorBoundary: {
		padding: 15,
		backgroundColor: '#ffebee',
		borderRadius: 8,
		borderWidth: 1,
		borderColor: '#f44336',
	},
	errorText: {
		color: '#d32f2f',
		fontSize: 14,
		marginBottom: 10,
	},
	resetButton: {
		backgroundColor: '#f44336',
		padding: 8,
		borderRadius: 4,
		alignSelf: 'flex-start',
	},
	buttonText: {
		color: '#fff',
		fontSize: 12,
		fontWeight: 'bold',
	},
	successText: {
		color: '#4caf50',
		fontSize: 14,
		fontStyle: 'italic',
	},
	resultsContainer: {
		padding: 20,
		backgroundColor: '#fff',
		marginBottom: 10,
	},
	resultsHeader: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
		marginBottom: 15,
	},
	clearButton: {
		backgroundColor: '#ff5722',
		paddingHorizontal: 12,
		paddingVertical: 6,
		borderRadius: 4,
	},
	clearButtonText: {
		color: '#fff',
		fontSize: 12,
		fontWeight: 'bold',
	},
	noResults: {
		fontSize: 14,
		color: '#999',
		fontStyle: 'italic',
		textAlign: 'center',
		padding: 20,
	},
	resultItem: {
		backgroundColor: '#f9f9f9',
		padding: 12,
		borderRadius: 8,
		marginBottom: 8,
		borderLeftWidth: 4,
		borderLeftColor: '#2196f3',
	},
	resultHeader: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
		marginBottom: 4,
	},
	resultName: {
		fontSize: 14,
		fontWeight: 'bold',
		color: '#333',
	},
	resultStatus: {
		fontSize: 12,
		fontWeight: 'bold',
	},
	resultMessage: {
		fontSize: 12,
		color: '#666',
		marginBottom: 4,
	},
	resultTimestamp: {
		fontSize: 10,
		color: '#999',
	},
	instructionsContainer: {
		padding: 20,
		backgroundColor: '#fff',
		marginBottom: 20,
	},
	instruction: {
		fontSize: 14,
		color: '#666',
		marginBottom: 8,
		paddingLeft: 8,
	},
})

export default ErrorTestSuite
