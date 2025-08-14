import React, { useState, useEffect } from 'react'
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native'
import { LDObserve } from '@/app/observability'

interface ErrorEvent {
	id: string
	message: string
	type: string
	timestamp: Date
	attributes?: Record<string, any>
}

export const ErrorMonitor: React.FC = () => {
	const [errors, setErrors] = useState<ErrorEvent[]>([])
	const [isVisible, setIsVisible] = useState(false)
	const [errorCount, setErrorCount] = useState(0)

	useEffect(() => {
		// Monkey patch the recordError method to monitor captured errors
		const originalRecordError = LDObserve.recordError
		
		LDObserve.recordError = function(error: Error, attributes?: Record<string, any>) {
			// Log to our monitor
			const errorEvent: ErrorEvent = {
				id: Math.random().toString(36).substr(2, 9),
				message: error.message,
				type: error.name,
				timestamp: new Date(),
				attributes
			}
			
			setErrors(prev => [errorEvent, ...prev.slice(0, 9)]) // Keep last 10
			setErrorCount(prev => prev + 1)
			
			// Call original method
			return originalRecordError.call(this, error, attributes)
		}
		
		return () => {
			// Restore original method
			LDObserve.recordError = originalRecordError
		}
	}, [])

	const clearErrors = () => {
		setErrors([])
		setErrorCount(0)
	}

	const toggleVisibility = () => {
		setIsVisible(!isVisible)
	}

	return (
		<View style={styles.container}>
			{/* Floating indicator */}
			<TouchableOpacity 
				style={[styles.indicator, errorCount > 0 && styles.indicatorActive]}
				onPress={toggleVisibility}
			>
				<Text style={styles.indicatorText}>
					🐛 {errorCount}
				</Text>
			</TouchableOpacity>

			{/* Error details panel */}
			{isVisible && (
				<View style={styles.panel}>
					<View style={styles.header}>
						<Text style={styles.title}>Error Monitor</Text>
						<View style={styles.headerButtons}>
							<TouchableOpacity style={styles.clearButton} onPress={clearErrors}>
								<Text style={styles.clearButtonText}>Clear</Text>
							</TouchableOpacity>
							<TouchableOpacity style={styles.closeButton} onPress={toggleVisibility}>
								<Text style={styles.closeButtonText}>×</Text>
							</TouchableOpacity>
						</View>
					</View>

					{errors.length === 0 ? (
						<Text style={styles.noErrors}>No errors captured yet</Text>
					) : (
						errors.map((error) => (
							<View key={error.id} style={styles.errorItem}>
								<View style={styles.errorHeader}>
									<Text style={styles.errorType}>{error.type}</Text>
									<Text style={styles.errorTime}>
										{error.timestamp.toLocaleTimeString()}
									</Text>
								</View>
								<Text style={styles.errorMessage} numberOfLines={2}>
									{error.message}
								</Text>
								{error.attributes && (
									<Text style={styles.errorAttributes} numberOfLines={1}>
										{JSON.stringify(error.attributes)}
									</Text>
								)}
							</View>
						))
					)}
				</View>
			)}
		</View>
	)
}

const styles = StyleSheet.create({
	container: {
		position: 'absolute',
		top: 50,
		right: 20,
		zIndex: 1000,
	},
	indicator: {
		backgroundColor: '#666',
		borderRadius: 20,
		paddingHorizontal: 12,
		paddingVertical: 6,
		shadowColor: '#000',
		shadowOffset: { width: 0, height: 2 },
		shadowOpacity: 0.3,
		shadowRadius: 4,
		elevation: 5,
	},
	indicatorActive: {
		backgroundColor: '#f44336',
	},
	indicatorText: {
		color: '#fff',
		fontSize: 12,
		fontWeight: 'bold',
	},
	panel: {
		backgroundColor: '#fff',
		borderRadius: 8,
		marginTop: 10,
		minWidth: 280,
		maxWidth: 320,
		maxHeight: 400,
		shadowColor: '#000',
		shadowOffset: { width: 0, height: 4 },
		shadowOpacity: 0.3,
		shadowRadius: 8,
		elevation: 10,
	},
	header: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
		padding: 12,
		borderBottomWidth: 1,
		borderBottomColor: '#eee',
	},
	title: {
		fontSize: 16,
		fontWeight: 'bold',
		color: '#333',
	},
	headerButtons: {
		flexDirection: 'row',
		gap: 8,
	},
	clearButton: {
		backgroundColor: '#ff5722',
		paddingHorizontal: 8,
		paddingVertical: 4,
		borderRadius: 4,
	},
	clearButtonText: {
		color: '#fff',
		fontSize: 10,
		fontWeight: 'bold',
	},
	closeButton: {
		backgroundColor: '#666',
		width: 24,
		height: 24,
		borderRadius: 12,
		alignItems: 'center',
		justifyContent: 'center',
	},
	closeButtonText: {
		color: '#fff',
		fontSize: 16,
		fontWeight: 'bold',
		lineHeight: 20,
	},
	noErrors: {
		padding: 20,
		textAlign: 'center',
		color: '#666',
		fontStyle: 'italic',
	},
	errorItem: {
		padding: 12,
		borderBottomWidth: 1,
		borderBottomColor: '#f0f0f0',
	},
	errorHeader: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
		marginBottom: 4,
	},
	errorType: {
		fontSize: 12,
		fontWeight: 'bold',
		color: '#f44336',
	},
	errorTime: {
		fontSize: 10,
		color: '#999',
	},
	errorMessage: {
		fontSize: 12,
		color: '#333',
		marginBottom: 4,
	},
	errorAttributes: {
		fontSize: 10,
		color: '#666',
		fontFamily: 'monospace',
	},
})

export default ErrorMonitor