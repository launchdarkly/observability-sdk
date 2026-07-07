import { AppState } from 'react-native'
import { ReactNativeOptions } from '../api/Options'
import { generateUniqueId } from '../utils/idGenerator'
import {
	SESSION_RESUME_THRESHOLD_MS,
	SESSION_STORAGE_KEY,
} from '../constants/sessions'
import { createSessionStore, SessionStore } from './storage/sessionStore'

export interface SessionInfo {
	sessionId: string
	startTime: number
}

/**
 * Result of resolving the session on a JS load: whether it continues a
 * previously persisted session (a reload) and, if so, how long the app had been
 * gone and how many times this session has been reloaded.
 */
export interface SessionResumeInfo {
	reloaded: boolean
	/** Milliseconds between the previous session's last activity and this load. */
	elapsedMs: number
	/** How many times this session has been resumed across reloads. */
	reloadCount: number
}

type PersistedSession = {
	sessionId: string
	startTime: number
	lastActivityTime: number
	reloadCount: number
}

type Options = {
	sessionTimeout?: ReactNativeOptions['sessionTimeout']
	debug?: ReactNativeOptions['debug']
}

// Minimum spacing between persistence writes triggered by activity, so a burst
// of spans doesn't hammer AsyncStorage. The resume window is measured in
// minutes, so a few seconds of write granularity is more than enough.
const PERSIST_THROTTLE_MS = 5 * 1000

export class SessionManager {
	private sessionInfo: SessionInfo
	private backgroundTime: number | null = null
	private options: Required<Options>
	private store: SessionStore
	private reloadCount = 0
	private resumeInfo: SessionResumeInfo = {
		reloaded: false,
		elapsedMs: 0,
		reloadCount: 0,
	}
	private lastPersistAt = 0

	constructor(options?: Options, store?: SessionStore) {
		this.options = {
			sessionTimeout: options?.sessionTimeout ?? 30 * 60 * 1000,
			debug: !!options?.debug,
		}
		this.store = store ?? createSessionStore()

		// Provisional session, used until initialize() resolves any persisted
		// session. Callers must await initialize() before reading attributes.
		this.sessionInfo = {
			sessionId: generateUniqueId(),
			startTime: Date.now(),
		}
	}

	public async initialize(): Promise<void> {
		try {
			await this.resolveSession()
			this.setupAppStateListener()

			if (this.options.debug) {
				console.log('📱 Session initialized:', {
					sessionId: this.sessionInfo.sessionId,
					reloaded: this.resumeInfo.reloaded,
					reloadCount: this.resumeInfo.reloadCount,
				})
			}
		} catch (error) {
			console.error('Failed to initialize session:', error)
			this.sessionInfo = {
				sessionId: generateUniqueId(),
				startTime: Date.now(),
			}
			this.resumeInfo = { reloaded: false, elapsedMs: 0, reloadCount: 0 }
		}
	}

	/**
	 * Reads any persisted session and either resumes it (when the last activity
	 * was recent enough) or starts a fresh one, then persists the decision.
	 */
	private async resolveSession(): Promise<void> {
		const previous = await this.readPersisted()
		const now = Date.now()

		if (previous && now - previous.lastActivityTime < SESSION_RESUME_THRESHOLD_MS) {
			// Continue the same session across the reload.
			this.sessionInfo = {
				sessionId: previous.sessionId,
				startTime: previous.startTime,
			}
			this.reloadCount = previous.reloadCount + 1
			this.resumeInfo = {
				reloaded: true,
				elapsedMs: now - previous.lastActivityTime,
				reloadCount: this.reloadCount,
			}
		} else {
			// Stale or missing — start a fresh session.
			this.sessionInfo = { sessionId: generateUniqueId(), startTime: now }
			this.reloadCount = 0
			this.resumeInfo = { reloaded: false, elapsedMs: 0, reloadCount: 0 }
		}

		await this.persist(now, true)
	}

	private async readPersisted(): Promise<PersistedSession | undefined> {
		if (!this.store.isPersistent) return undefined
		try {
			const raw = await this.store.getItem(SESSION_STORAGE_KEY)
			if (!raw) return undefined
			const parsed = JSON.parse(raw) as Partial<PersistedSession>
			if (
				typeof parsed?.sessionId === 'string' &&
				parsed.sessionId.length > 0 &&
				typeof parsed.startTime === 'number' &&
				typeof parsed.lastActivityTime === 'number'
			) {
				return {
					sessionId: parsed.sessionId,
					startTime: parsed.startTime,
					lastActivityTime: parsed.lastActivityTime,
					reloadCount:
						typeof parsed.reloadCount === 'number'
							? parsed.reloadCount
							: 0,
				}
			}
		} catch {
			// Corrupt / unreadable — treat as no previous session.
		}
		return undefined
	}

	private async persist(now: number, force = false): Promise<void> {
		if (!this.store.isPersistent) return
		if (!force && now - this.lastPersistAt < PERSIST_THROTTLE_MS) return
		this.lastPersistAt = now
		const payload: PersistedSession = {
			sessionId: this.sessionInfo.sessionId,
			startTime: this.sessionInfo.startTime,
			lastActivityTime: now,
			reloadCount: this.reloadCount,
		}
		try {
			await this.store.setItem(SESSION_STORAGE_KEY, JSON.stringify(payload))
		} catch {
			// Best-effort persistence; ignore write failures.
		}
	}

	/**
	 * Records that the session is still active and refreshes the persisted
	 * `lastActivityTime` (throttled). Called on telemetry activity so the resume
	 * window reflects real usage.
	 */
	public touch(): void {
		void this.persist(Date.now())
	}

	/** Whether this JS load resumed a previously persisted session. */
	public wasReloaded(): boolean {
		return this.resumeInfo.reloaded
	}

	/** Details about a resume, for populating the `app_reload` span. */
	public getResumeInfo(): SessionResumeInfo {
		return { ...this.resumeInfo }
	}

	private setupAppStateListener(): void {
		AppState.addEventListener('change', (nextAppState) => {
			if (this.options.debug) {
				console.log('🔄 App state changed:', nextAppState)
			}

			if (nextAppState === 'background') {
				this.backgroundTime = Date.now()
				// Persist immediately so a kill-while-backgrounded still leaves an
				// accurate lastActivityTime for the next launch to resume from.
				void this.persist(this.backgroundTime, true)
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

			if (timeInBackground >= this.options.sessionTimeout) {
				if (this.options.debug) {
					console.log(
						`🕐 App was in background for >${this.options.sessionTimeout / 60000} minutes, resetting session`,
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
		const now = Date.now()

		// TODO: Update resource attributes
		this.sessionInfo = {
			sessionId: newSessionId,
			startTime: now,
		}
		this.reloadCount = 0
		this.resumeInfo = { reloaded: false, elapsedMs: 0, reloadCount: 0 }
		void this.persist(now, true)

		if (this.options.debug) {
			console.log('🔄 Session reset:', {
				oldSessionId: oldSessionId,
				newSessionId: newSessionId,
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
