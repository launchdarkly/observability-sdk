import { AppState } from 'react-native'
import { ReactNativeOptions } from '../api/Options'
import { generateUniqueId } from '../utils/idGenerator'

export interface SessionInfo {
	sessionId: string
	startTime: number
}

export class SessionManager {
	private sessionInfo: SessionInfo
	private backgroundTime: number | null = null
	private readonly BACKGROUND_TIMEOUT = 10 * 60 * 1000 // 10 mins

	constructor(private options: Required<ReactNativeOptions>) {
		this.sessionInfo = {
			sessionId: generateUniqueId(),
			startTime: Date.now(),
		}
	}

	public async initialize(): Promise<void> {
		try {
			this.setupAppStateListener()

			if (this.options.debug) {
				console.log('📱 Session initialized:', {
					sessionId: this.sessionInfo.sessionId.slice(-8),
				})
			}
		} catch (error) {
			console.error('Failed to initialize session:', error)
			this.sessionInfo = {
				sessionId: generateUniqueId(),
				startTime: Date.now(),
			}
		}
	}

	private setupAppStateListener(): void {
		AppState.addEventListener('change', (nextAppState) => {
			if (this.options.debug) {
				console.log('🔄 App state changed:', nextAppState)
			}

			if (nextAppState === 'background') {
				this.backgroundTime = Date.now()
				if (this.options.debug) {
					console.log('📱 App went to background')
				}
			} else if (nextAppState === 'active') {
				this.handleAppForeground()
			}
		})
	}

	private handleAppForeground(): void {
		if (this.backgroundTime) {
			const timeInBackground = Date.now() - this.backgroundTime

			if (timeInBackground >= this.BACKGROUND_TIMEOUT) {
				if (this.options.debug) {
					console.log(
						'🕐 App was in background for >10 minutes, resetting session',
					)
				}
				this.resetSession()
			} else {
				if (this.options.debug) {
					console.log(
						'📱 App returned to foreground, continuing session',
					)
				}
			}

			this.backgroundTime = null
		}
	}

	private resetSession(): void {
		const oldSessionId = this.sessionInfo.sessionId
		const newSessionId = generateUniqueId()

		this.sessionInfo = {
			sessionId: newSessionId,
			startTime: Date.now(),
		}

		if (this.options.debug) {
			console.log('🔄 Session reset:', {
				oldSessionId: oldSessionId.slice(-8),
				newSessionId: newSessionId.slice(-8),
			})
		}
	}

	public getSessionInfo(): SessionInfo {
		return { ...this.sessionInfo }
	}

	public getSessionAttributes(): Record<string, string> {
		return {
			'session.id': this.sessionInfo.sessionId,
			'session.start_time': this.sessionInfo.startTime.toString(),
		}
	}

	public getSessionContext(): Record<string, any> {
		return {
			sessionId: this.sessionInfo.sessionId,
			sessionDuration: Date.now() - this.sessionInfo.startTime,
		}
	}
}
