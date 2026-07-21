import { AppState } from 'react-native'
import { ReactNativeOptions } from '../api/Options'
import { generateUniqueId } from '../utils/idGenerator'
import {
	SESSION_RESUME_THRESHOLD_MS,
	SESSION_STORAGE_KEY,
} from '../constants/sessions'
import { createSessionStore, SessionStore } from './storage/sessionStore'
import { getNativeProcessStartTimeMs } from './nativeProcess'

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
	/**
	 * Resolves the current OS process's start time (ms since epoch), used to
	 * distinguish a cold start from a surviving process. Injectable for tests;
	 * defaults to reading the native session-replay module. Returns `undefined`
	 * when no native signal is available.
	 */
	getProcessStartTimeMs?: () => number | undefined
}

// Minimum spacing between persistence writes triggered by activity, so a burst
// of spans doesn't hammer AsyncStorage. Backgrounding and each JS load persist
// unconditionally (force=true), so this only throttles mid-run activity writes.
const PERSIST_THROTTLE_MS = 5 * 1000

export class SessionManager {
	private sessionInfo: SessionInfo
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
			sessionTimeout:
				options?.sessionTimeout ?? SESSION_RESUME_THRESHOLD_MS,
			debug: !!options?.debug,
			getProcessStartTimeMs:
				options?.getProcessStartTimeMs ?? getNativeProcessStartTimeMs,
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
			if (!this.store.isPersistent) {
				// Persistence is what keeps the session id stable across a JS
				// reload (and aligned with native session replay). Without it every
				// reload mints a new session. AsyncStorage is a required peer
				// dependency on native; warn loudly if it is missing.
				console.warn(
					'[LaunchDarkly] No persistent session store available. ' +
						'Install @react-native-async-storage/async-storage so the ' +
						'session id survives reloads and stays aligned with session replay.',
				)
			}
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

		// A cold start (the app was terminated and relaunched) must always begin a
		// fresh session: the native session-replay recording restarts its payload
		// sequence at 0 on a new process, so reusing the previous session id would
		// collide with and corrupt that session's earlier recording on the backend.
		// We detect it by comparing the previous session's last activity against
		// this process's start time — if the activity predates the process, it came
		// from a prior process (a cold restart). A surviving process (soft/OTA
		// reload) keeps activity newer than its start time, so it may resume within
		// the window. When no native signal is available we can't tell, so we don't
		// block resume (purely time-based, matching observability-only behavior).
		const processStartMs = this.options.getProcessStartTimeMs()
		const isColdStart =
			previous !== undefined &&
			processStartMs !== undefined &&
			previous.lastActivityTime < processStartMs

		if (
			previous &&
			!isColdStart &&
			now - previous.lastActivityTime < this.options.sessionTimeout
		) {
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
			// Stale or missing — start a fresh session. Keep the provisional id
			// generated in the constructor rather than minting a new one, so the
			// session id is stable and can be read synchronously (e.g. by the
			// session replay plugin adopting `session.id`) before this async init
			// completes.
			this.reloadCount = 0
			this.resumeInfo = { reloaded: false, elapsedMs: 0, reloadCount: 0 }

			if (this.options.debug && isColdStart) {
				console.log(
					'📱 Cold start detected — starting a fresh session instead of ' +
						`resuming ${previous?.sessionId} (last activity ` +
						`${now - (previous?.lastActivityTime ?? now)}ms ago, before ` +
						'this process started)',
				)
			}
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
			await this.store.setItem(
				SESSION_STORAGE_KEY,
				JSON.stringify(payload),
			)
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

			// The session id is never rotated in-process. The native session
			// replay / observability instance we seed treats an externally
			// supplied id as a *custom* session and holds it for the whole
			// process lifetime (Android LDSessionManager.isCustomSession; iOS
			// isCustomSession) — it cannot follow an in-process rotation. So the
			// JS side must not rotate either; session boundaries are decided only
			// at the next JS load, from persisted `lastActivityTime` vs
			// `sessionTimeout` (see resolveSession). We only persist on background
			// so a kill-while-backgrounded still leaves an accurate
			// lastActivityTime for that next-load decision.
			if (nextAppState === 'background') {
				void this.persist(Date.now(), true)
				if (this.options.debug) {
					console.log('📱 App went to background')
				}
			}
		})
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
