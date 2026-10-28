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
	;(
		globalThis as { persistentSessionSecureID?: string }
	).persistentSessionSecureID = undefined
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

	it('keeps a fresh cookie-only entry with no matching localStorage value', () => {
		// Parallel tab on a different subdomain (or after localStorage was
		// cleared) leaves only the cookie behind. The freshness check has to
		// fall back to the cookie value or it will unconditionally prune it.
		const recent = makeSessionData('cookie-only', {
			lastPushTime: Date.now() - 1000,
		})
		cookieStorage.setItem(
			sessionDataKey('cookie-only'),
			JSON.stringify(recent),
		)

		setSessionData(makeSessionData('new-session'))

		expect(cookieStorage.getItem(sessionDataKey('cookie-only'))).not.toBe(
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

// Observe-only sessions have no replay push loop to refresh lastPushTime.
// These tests use real configureSession page loads: telemetry activity
// (touchSession) over a session started more than 15 minutes ago must
// resume it, while a true 16-minute idle gap must start a new one.
//
// touchSession's throttle lives in module state, so the modules under test
// are re-imported per test (with their clock reset to the fixed base time)
// rather than shared with the statically imported ones above.
describe('touchSession', () => {
	let getPreviousSessionData: typeof import('../client/utils/sessionStorage/highlightSession').getPreviousSessionData
	let touchSession: typeof import('../client/utils/sessionStorage/highlightSession').touchSession
	let Plugin: typeof import('../plugins/common').Plugin

	beforeEach(async () => {
		vi.useFakeTimers()
		vi.setSystemTime(new Date(2024, 0, 1, 12, 0, 0))
		clearAllSessionData()
		vi.resetModules()
		;({ getPreviousSessionData, touchSession } =
			await import('../client/utils/sessionStorage/highlightSession'))
		;({ Plugin } = await import('../plugins/common'))
	})

	afterEach(() => {
		clearAllSessionData()
		vi.useRealTimers()
	})

	const advance = (ms: number) => {
		vi.setSystemTime(new Date(Date.now() + ms))
	}

	// Each Plugin instance simulates a page load: configureSession re-runs
	// the same resume-or-create decision the real SDK makes when a page
	// loads.
	const startSessionPageLoad = (options?: { sessionKey?: string }) => {
		const plugin = new Plugin()
		plugin.configureSession('test-sdk-credential', options)
		return getPreviousSessionData()
	}

	it('resumes a session reloaded more than 15 minutes after its start when the user stayed active', () => {
		const firstLoad = startSessionPageLoad()
		expect(firstLoad?.sessionSecureID).toBeDefined()

		// The user keeps generating telemetry for 19.5 minutes, then does a
		// full page load at the 20-minute mark.
		advance(19.5 * 60 * 1000)
		touchSession()
		const stored = getPreviousSessionData()
		expect(stored?.lastPushTime).toBe(Date.now())

		advance(0.5 * 60 * 1000)
		const secondLoad = startSessionPageLoad()

		expect(secondLoad?.sessionSecureID).toBe(firstLoad?.sessionSecureID)
		// The resumed session keeps its original start time.
		expect(secondLoad?.sessionStartTime).toBe(firstLoad?.sessionStartTime)
	})

	it('creates a new session after 16 idle minutes', () => {
		const firstLoad = startSessionPageLoad()

		advance(16 * 60 * 1000)
		const secondLoad = startSessionPageLoad()

		expect(secondLoad?.sessionSecureID).not.toBe(firstLoad?.sessionSecureID)
	})

	it('rotates a session that outlived the 4-hour maximum even if it was active seconds before the reload', () => {
		const firstLoad = startSessionPageLoad()

		// Active until just before the 4-hour maximum.
		advance(4 * 60 * 60 * 1000 - 10 * 1000)
		touchSession()
		expect(getPreviousSessionData()?.lastPushTime).toBe(Date.now())

		// Past the maximum, a touch no longer refreshes: the stored data is
		// dropped so the next page load has to start a new session.
		advance(2 * 60 * 1000)
		touchSession()
		expect(getPreviousSessionData()).toBeUndefined()

		const nextLoad = startSessionPageLoad()
		expect(nextLoad?.sessionSecureID).not.toBe(firstLoad?.sessionSecureID)
	})

	it('rotates a session reloaded just past the 4-hour maximum before a touch dropped it', () => {
		const firstLoad = startSessionPageLoad()

		// The last touch lands just before the maximum; the reload comes just
		// after it, before any telemetry on the old page could drop the data.
		advance(4 * 60 * 60 * 1000 - 5 * 1000)
		touchSession()
		advance(10 * 1000)

		const nextLoad = startSessionPageLoad()
		expect(nextLoad?.sessionSecureID).toBeDefined()
		expect(nextLoad?.sessionSecureID).not.toBe(firstLoad?.sessionSecureID)
	})

	it('keeps a sessionKey session past the 4-hour maximum, like the replay reset', () => {
		const firstLoad = startSessionPageLoad({ sessionKey: 'sign-up-funnel' })

		advance(4 * 60 * 60 * 1000 + 5 * 60 * 1000)
		touchSession()
		const stored = getPreviousSessionData()
		expect(stored?.sessionSecureID).toBe(firstLoad?.sessionSecureID)
		expect(stored?.lastPushTime).toBe(Date.now())
	})

	it('limits storage writes to one per 10 seconds', () => {
		startSessionPageLoad()

		advance(30 * 1000)
		touchSession()
		const firstTouch = getPreviousSessionData()?.lastPushTime
		expect(firstTouch).toBe(Date.now())

		// Within the throttle window nothing is written.
		advance(3 * 1000)
		touchSession()
		expect(getPreviousSessionData()?.lastPushTime).toBe(firstTouch)

		// Past the window the next activity writes again.
		advance(8 * 1000)
		touchSession()
		expect(getPreviousSessionData()?.lastPushTime).toBe(Date.now())
	})

	it('is a no-op with no stored session data', () => {
		// Clear the throttle window so the touch isn't skipped for a reason
		// unrelated to the missing session data.
		advance(30 * 1000)
		expect(() => touchSession()).not.toThrow()
		expect(getPreviousSessionData()).toBeUndefined()
	})

	it('does not throw on unparseable stored session data', () => {
		const firstLoad = startSessionPageLoad()
		setItem(sessionDataKey(firstLoad!.sessionSecureID), '{not json')

		advance(30 * 1000)
		expect(() => touchSession()).not.toThrow()
	})
})
