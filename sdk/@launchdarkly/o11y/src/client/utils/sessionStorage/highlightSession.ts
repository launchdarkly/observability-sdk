import {
	MAX_SESSION_LENGTH,
	SESSION_PUSH_THRESHOLD,
} from '../../constants/sessions'
import {
	cookieStorage,
	getItem,
	removeItem,
	setItem,
	getPersistentStorage,
} from '../storage'
import { SESSION_STORAGE_KEYS } from './sessionStorageKeys'
import { internalLogOnce } from '../../../sdk/util'

export type SessionData = {
	sessionSecureID: string
	sessionKey?: string
	projectID: number
	sessionStartTime?: number
	lastPushTime?: number
	userIdentifier?: string
	userObject?: Object
}

const getSessionDataKey = (sessionID: string): string => {
	return `${SESSION_STORAGE_KEYS.SESSION_DATA}_${sessionID}`
}

interface GlobalThis {
	persistentSessionSecureID?: string
}
declare var globalThis: GlobalThis | undefined

export const getPersistentSessionSecureID = (): string => {
	if (
		typeof globalThis !== 'undefined' &&
		globalThis.persistentSessionSecureID?.length
	) {
		return globalThis.persistentSessionSecureID
	}
	return getSessionSecureID()
}

export const setPersistentSessionSecureID = (secureID: string) => {
	// for duplicate tab functionality, secureID is ''
	// avoid clearing the local secureID used for network request instrumentation
	if (typeof globalThis !== 'undefined' && secureID?.length) {
		globalThis.persistentSessionSecureID = secureID
	}
}

export const getSessionSecureID = (): string => {
	return getItem(SESSION_STORAGE_KEYS.SESSION_ID) ?? ''
}

export const setSessionSecureID = (secureID: string) => {
	// for duplicate tab functionality, secureID is ''
	setItem(SESSION_STORAGE_KEYS.SESSION_ID, secureID)
}

const getSessionData = (sessionID: string): SessionData | undefined => {
	const key = getSessionDataKey(sessionID)
	let storedSessionData = JSON.parse(getItem(key) || '{}')
	return storedSessionData as SessionData
}

// Deterministic sessionKey sessions are exempt, matching the replay reset in
// RecordSDK._save.
const exceedsMaxSessionLength = (sessionData: SessionData, now: number) =>
	!sessionData.sessionKey &&
	!!sessionData.sessionStartTime &&
	now - sessionData.sessionStartTime > MAX_SESSION_LENGTH

export const getPreviousSessionData = (
	sessionID?: string,
): SessionData | undefined => {
	if (!sessionID) {
		sessionID = getSessionSecureID()
	}
	let storedSessionData = getSessionData(sessionID)
	if (
		storedSessionData &&
		storedSessionData.lastPushTime &&
		Date.now() - storedSessionData.lastPushTime < SESSION_PUSH_THRESHOLD &&
		// A recent push does not extend a session past its maximum length.
		!exceedsMaxSessionLength(storedSessionData, Date.now())
	) {
		return storedSessionData as SessionData
	} else {
		removeItem(getSessionDataKey(sessionID))
	}
}

/**
 * Reads the stored record for a session (defaulting to the stored session
 * ID) whether or not it is expired, without pruning it. Used when a page
 * load cannot resume the stored session: getPreviousSessionData removes an
 * expired record when it is read, and the expired record's ID is what links
 * the replacement session to the one it replaced.
 */
export const getStoredSessionData = (
	sessionID?: string,
): SessionData | undefined => {
	const secureID = sessionID ?? getSessionSecureID()
	if (!secureID) {
		return undefined
	}
	try {
		return getSessionData(secureID)
	} catch {
		return undefined
	}
}

/**
 * The session the current page load's session replaced, when a new session
 * was created in place of a stored one. Set while configuring the session;
 * the span processor reads it to stamp `session.previous_id` on the new
 * session's telemetry so related sessions can be chained in analysis.
 */
let previousSessionSecureID: string | undefined

export const getPreviousSessionSecureID = (): string | undefined =>
	previousSessionSecureID

export const setPreviousSessionSecureID = (secureID?: string): void => {
	previousSessionSecureID = secureID
}

// Only session replay has a push loop that refreshes `lastPushTime`
// (RecordSDK._save). Everything an observe-only session does while the user
// is active goes out through telemetry, so telemetry must refresh it too —
// otherwise a page load more than SESSION_PUSH_THRESHOLD after the session
// _start_ would rotate the session even though the user never went idle.
const SESSION_TOUCH_THROTTLE_MS = 10 * 1000

let lastSessionTouchTime = 0

/**
 * Refresh the session's last-activity timestamp, keeping the current
 * session across full page loads while the user is active.
 * Throttled so frequent telemetry does not hammer storage; a session never
 * outlives MAX_SESSION_LENGTH — once that is reached the stored data is
 * removed so the next page load starts a new session (mirroring the replay
 * reset in RecordSDK._save; deterministic sessionKey sessions keep the
 * same exemption).
 */
export const touchSession = (): void => {
	const now = Date.now()
	if (now - lastSessionTouchTime < SESSION_TOUCH_THROTTLE_MS) {
		return
	}
	lastSessionTouchTime = now

	// Called from the span export path, so it must never throw (e.g. on
	// unparseable stored session data).
	try {
		const sessionData = getSessionData(getPersistentSessionSecureID())
		if (!sessionData?.sessionSecureID) {
			return
		}
		if (exceedsMaxSessionLength(sessionData, now)) {
			removeItem(getSessionDataKey(sessionData.sessionSecureID))
			return
		}
		setSessionData({ ...sessionData, lastPushTime: now })
	} catch (e) {
		internalLogOnce(
			'highlightSession',
			'touchSession',
			'debug',
			'failed to refresh session activity',
			e,
		)
	}
}

export const setSessionData = function (sessionData?: SessionData) {
	if (!sessionData?.sessionSecureID) return
	const secureID = sessionData.sessionSecureID!
	setPersistentSessionSecureID(secureID)
	const key = getSessionDataKey(secureID)
	setItem(key, JSON.stringify(sessionData))
	// delete old session data
	pruneSessionData(key)
}

export const loadCookieSessionData = function () {
	const sessionSecureID = cookieStorage.getItem(
		SESSION_STORAGE_KEYS.SESSION_ID,
	)
	setSessionSecureID(sessionSecureID)
	const sessionDataKey = getSessionDataKey(sessionSecureID)
	const sessionDataStr = cookieStorage.getItem(sessionDataKey)
	try {
		setSessionData(JSON.parse(sessionDataStr) as SessionData)
	} catch (e) {}
}

function pruneSessionData(keepKey: string): void {
	const prefix = `${SESSION_STORAGE_KEYS.SESSION_DATA}_`
	const candidates = new Set<string>()

	const storage = getPersistentStorage()
	for (let i = storage.length - 1; i >= 0; i--) {
		const key = storage.key(i)
		if (key && key.startsWith(prefix) && key !== keepKey) {
			candidates.add(key)
		}
	}

	// Cookies are written alongside localStorage but persist independently
	// (they survive localStorage being cleared, and outlive many sessions
	// via the SESSION_PUSH_THRESHOLD expiry). Without enumerating them here,
	// sessionData_* cookies pile up over time and bloat the Cookie header
	// enough to break unrelated API calls.
	for (const key of cookieStorage.keys()) {
		if (key.startsWith(prefix) && key !== keepKey) {
			candidates.add(key)
		}
	}

	for (const key of candidates) {
		let stale = true
		try {
			// Cookie-only entries (e.g. from a parallel tab that hasn't
			// hydrated localStorage in this context) won't be found by
			// getItem, which only reads persistent storage. Fall back to
			// the cookie value so we don't unconditionally prune fresh
			// data belonging to active sessions.
			const raw = getItem(key) || cookieStorage.getItem(key) || '{}'
			const sessionData = JSON.parse(raw) as SessionData
			// Fall back to sessionStartTime so entries from sessions that
			// ended before the first push still age out. Entries with no
			// usable timestamp (foreign / corrupt / truncated) are stale.
			const ts = sessionData.lastPushTime ?? sessionData.sessionStartTime
			stale =
				ts === undefined || Date.now() - ts >= SESSION_PUSH_THRESHOLD
		} catch {
			// Truncated past the 4KB cookie limit or otherwise corrupt —
			// drop silently rather than logging on every page load.
		}
		if (stale) {
			internalLogOnce(
				'highlightSession',
				'pruneSessionData',
				'debug',
				`removing session data for stale key ${key}`,
			)
			removeItem(key)
		}
	}
}
