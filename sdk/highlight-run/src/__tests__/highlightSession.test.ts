import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Cookies from 'js-cookie'

import {
	setSessionData,
	type SessionData,
} from '../client/utils/sessionStorage/highlightSession'
import { SESSION_PUSH_THRESHOLD } from '../client/constants/sessions'
import { cookieStorage, setItem } from '../client/utils/storage'

const sessionDataKey = (id: string) => `sessionData_${id}`

const makeSessionData = (
	id: string,
	overrides: Partial<SessionData> = {},
): SessionData => ({
	sessionSecureID: id,
	projectID: 1,
	sessionStartTime: Date.now(),
	lastPushTime: Date.now(),
	...overrides,
})

const clearAllSessionData = () => {
	if (typeof window !== 'undefined') {
		try {
			window.localStorage.clear()
		} catch {}
	}
	for (const key of Object.keys(Cookies.get() ?? {})) {
		Cookies.remove(key)
	}
}

describe('pruneSessionData', () => {
	beforeEach(() => {
		vi.useFakeTimers()
		vi.setSystemTime(new Date(2024, 0, 1, 12, 0, 0))
		clearAllSessionData()
	})

	afterEach(() => {
		clearAllSessionData()
		vi.useRealTimers()
	})

	it('removes stale sessionData_* cookies that have no matching localStorage entry', () => {
		// Simulate cookies left behind from prior sessions, with no matching
		// localStorage entries (e.g. user cleared site data, different subdomain,
		// or localStorage was unavailable when they were written).
		const stale = makeSessionData('old-1', {
			lastPushTime: Date.now() - SESSION_PUSH_THRESHOLD - 1000,
		})
		cookieStorage.setItem(sessionDataKey('old-1'), JSON.stringify(stale))

		const fresh = makeSessionData('new-session')
		setSessionData(fresh)

		expect(cookieStorage.getItem(sessionDataKey('old-1'))).toBe('')
		expect(cookieStorage.getItem(sessionDataKey('new-session'))).not.toBe(
			'',
		)
	})

	it('removes sessionData_* cookies with corrupt/truncated values without logging', () => {
		const consoleError = vi
			.spyOn(console, 'error')
			.mockImplementation(() => {})
		cookieStorage.setItem(sessionDataKey('truncated'), '{"sessionSecu')

		setSessionData(makeSessionData('new-session'))

		expect(cookieStorage.getItem(sessionDataKey('truncated'))).toBe('')
		expect(consoleError).not.toHaveBeenCalled()
		consoleError.mockRestore()
	})

	it('removes entries with no timestamp metadata', () => {
		cookieStorage.setItem(
			sessionDataKey('no-ts'),
			JSON.stringify({ sessionSecureID: 'no-ts', projectID: 1 }),
		)

		setSessionData(makeSessionData('new-session'))

		expect(cookieStorage.getItem(sessionDataKey('no-ts'))).toBe('')
	})

	it('keeps recent session data from a parallel tab', () => {
		// Parallel tabs write to both cookies and localStorage via setItem.
		const recent = makeSessionData('parallel', {
			lastPushTime: Date.now() - 1000,
		})
		setItem(sessionDataKey('parallel'), JSON.stringify(recent))

		setSessionData(makeSessionData('new-session'))

		expect(cookieStorage.getItem(sessionDataKey('parallel'))).not.toBe('')
		expect(cookieStorage.getItem(sessionDataKey('new-session'))).not.toBe(
			'',
		)
	})

	it('falls back to sessionStartTime when lastPushTime is missing', () => {
		const stale = {
			sessionSecureID: 'started-never-pushed',
			projectID: 1,
			sessionStartTime: Date.now() - SESSION_PUSH_THRESHOLD - 1000,
		}
		cookieStorage.setItem(
			sessionDataKey('started-never-pushed'),
			JSON.stringify(stale),
		)

		setSessionData(makeSessionData('new-session'))

		expect(
			cookieStorage.getItem(sessionDataKey('started-never-pushed')),
		).toBe('')
	})
})
