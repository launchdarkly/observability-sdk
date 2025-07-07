import AsyncStorage from '@react-native-async-storage/async-storage'
import { AppState } from 'react-native'
import { ReactNativeOptions } from '../api/Options'
import { generateUniqueId } from '../utils/idGenerator'

export interface SessionInfo {
	sessionId: string
	startTime: number
}

export class SessionManager {
	private sessionInfo: SessionInfo | null = null
	private sessionStartTime: number

	constructor(private options: Required<ReactNativeOptions>) {
		this.sessionStartTime = Date.now()
	}

	public async initialize(): Promise<void> {
		try {
			let installationId = await AsyncStorage.getItem(
				'@session/installationId',
			)
			if (!installationId) {
				installationId = generateUniqueId()
				await AsyncStorage.setItem(
					'@session/installationId',
					installationId,
				)
			}

			const storedSession = await AsyncStorage.getItem('@session/current')
			let sessionId = generateUniqueId()

			if (storedSession) {
				const parsed = JSON.parse(storedSession)
				if (
					Date.now() - parsed.lastActivity <
					this.options.sessionTimeout
				) {
					sessionId = parsed.sessionId
				}
			}

			this.sessionInfo = {
				sessionId,
				startTime: this.sessionStartTime,
			}

			await this.persistSession()
			this.setupAppStateListener()

			if (this.options.debug) {
				console.log('📱 Session initialized:', {
					sessionId: sessionId.slice(-8),
				})
			}
		} catch (error) {
			console.error('Failed to initialize session:', error)
			this.sessionInfo = {
				sessionId: generateUniqueId(),
				startTime: this.sessionStartTime,
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
			console.log('🔄 App state changed:', nextAppState)

			if (nextAppState === 'active') {
				this.persistSession()
			}
		})
	}

	public getSessionInfo(): SessionInfo | null {
		return this.sessionInfo ? { ...this.sessionInfo } : null
	}

	public getSessionAttributes(): Record<string, string> {
		if (!this.sessionInfo) return {}

		return {
			'session.id': this.sessionInfo.sessionId,
			'session.start_time': this.sessionInfo.startTime.toString(),
		}
	}

	public getSessionContext(): Record<string, any> {
		if (!this.sessionInfo) return {}

		return {
			sessionId: this.sessionInfo.sessionId,
			sessionDuration: Date.now() - this.sessionInfo.startTime,
		}
	}
}
