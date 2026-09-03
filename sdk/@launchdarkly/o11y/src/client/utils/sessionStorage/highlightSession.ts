import { SESSION_PUSH_THRESHOLD } from '../../constants/sessions'
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
		Date.now() - storedSessionData.lastPushTime < SESSION_PUSH_THRESHOLD
	) {
		return storedSessionData as SessionData
	} else {
		removeItem(getSessionDataKey(sessionID))
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
