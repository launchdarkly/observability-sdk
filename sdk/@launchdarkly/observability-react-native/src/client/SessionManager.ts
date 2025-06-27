import AsyncStorage from '@react-native-async-storage/async-storage'
import { AppState, Platform } from 'react-native'
import DeviceInfo from 'react-native-device-info'
import 'react-native-get-random-values'
import { v4 as uuidv4 } from 'uuid'
import { ReactNativeOptions } from '../api/Options'

export interface SessionInfo {
	sessionId: string
	userId?: string
	deviceId: string
	appVersion: string
	platform: string
	startTime: number
	installationId: string
}

export class SessionManager {
	private sessionInfo: SessionInfo | null = null
	private sessionStartTime: number

	constructor(private options: Required<ReactNativeOptions>) {
		this.sessionStartTime = Date.now()
	}

	public async initialize(): Promise<void> {
		try {
			// Get device info
			const deviceId = await DeviceInfo.getUniqueId()
			const appVersion = DeviceInfo.getVersion()

			// Try to get existing installation ID or create new one
			let installationId = await AsyncStorage.getItem(
				'@session/installationId',
			)
			if (!installationId) {
				installationId = uuidv4()
				await AsyncStorage.setItem(
					'@session/installationId',
					installationId,
				)
			}

			// Check for existing session (within configured timeout)
			const storedSession = await AsyncStorage.getItem('@session/current')
			let sessionId = uuidv4()

			if (storedSession) {
				const parsed = JSON.parse(storedSession)
				// Continue session if it's less than the configured timeout
				if (
					Date.now() - parsed.lastActivity <
					this.options.sessionTimeout
				) {
					sessionId = parsed.sessionId
				}
			}

			this.sessionInfo = {
				sessionId,
				deviceId,
				appVersion,
				platform: Platform.OS,
				startTime: this.sessionStartTime,
				installationId,
			}

			// Store session info
			await this.persistSession()
			this.setupAppStateListener()

			if (this.options.debug) {
				console.log('📱 Session initialized:', {
					sessionId: sessionId.slice(-8),
					deviceId: deviceId.slice(-8),
					installationId: installationId.slice(-8),
				})
			}
		} catch (error) {
			console.error('Failed to initialize session:', error)
			// Fallback session
			this.sessionInfo = {
				sessionId: uuidv4(),
				deviceId: 'unknown',
				appVersion: 'unknown',
				platform: Platform.OS,
				startTime: this.sessionStartTime,
				installationId: 'unknown',
			}
		}
	}

	private async persistSession(): Promise<void> {
		if (!this.sessionInfo) return

		try {
			await AsyncStorage.setItem(
				'@session/current',
				JSON.stringify({
					sessionId: this.sessionInfo.sessionId,
					lastActivity: Date.now(),
				}),
			)
		} catch (error) {
			console.error('Failed to persist session:', error)
		}
	}

	private setupAppStateListener(): void {
		AppState.addEventListener('change', (nextAppState) => {
			if (nextAppState === 'active') {
				// Update last activity on app foreground
				this.persistSession()
			}
		})
	}

	public async setUserId(userId: string): Promise<void> {
		if (this.sessionInfo) {
			this.sessionInfo.userId = userId
			await this.persistSession()
			if (this.options.debug) {
				console.log('👤 User ID set for session:', userId)
			}
		}
	}

	public getSessionInfo(): SessionInfo | null {
		return this.sessionInfo
	}

	public getSessionAttributes(): Record<string, string> {
		if (!this.sessionInfo) return {}

		return {
			'session.id': this.sessionInfo.sessionId,
			'session.device_id': this.sessionInfo.deviceId,
			'session.installation_id': this.sessionInfo.installationId,
			'session.user_id': this.sessionInfo.userId || 'anonymous',
			'session.start_time': this.sessionInfo.startTime.toString(),
			'session.duration_ms': (
				Date.now() - this.sessionInfo.startTime
			).toString(),
			'app.version': this.sessionInfo.appVersion,
			'device.platform': this.sessionInfo.platform,
		}
	}

	public getSessionContext(): Record<string, any> {
		if (!this.sessionInfo) return {}

		return {
			sessionId: this.sessionInfo.sessionId,
			userId: this.sessionInfo.userId,
			deviceId: this.sessionInfo.deviceId,
			sessionDuration: Date.now() - this.sessionInfo.startTime,
			appVersion: this.sessionInfo.appVersion,
			platform: this.sessionInfo.platform,
		}
	}
}
