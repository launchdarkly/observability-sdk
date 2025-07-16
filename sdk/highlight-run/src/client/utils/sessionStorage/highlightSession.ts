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
	projectID: number
	payloadID: number
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

	// Walk backwards so index order isn’t upset by removals.
	for (let i = getPersistentStorage().length - 1; i >= 0; i--) {
		const key = getPersistentStorage().key(i)
		if (key && key.startsWith(prefix) && key !== keepKey) {
			try {
				const sessionData = JSON.parse(
					getItem(key) || '{}',
				) as SessionData
				if (
					sessionData.lastPushTime &&
					Date.now() - sessionData.lastPushTime >=
						SESSION_PUSH_THRESHOLD
				) {
					internalLogOnce(
						'highlightSession',
						'pruneSessionData',
						'debug',
						`removing session data for stale key ${key}`,
					)
					removeItem(key)
				}
			} catch (e) {
				internalLogOnce(
					'highlightSession',
					'pruneSessionData',
					'error',
					`failed to parse session data for key ${key}`,
					e,
				)
				removeItem(key)
			}
		}
	}
}
