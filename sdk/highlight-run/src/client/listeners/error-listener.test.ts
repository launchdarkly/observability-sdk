import { afterEach, describe, expect, it } from 'vitest'
import { ErrorListener } from './error-listener'
import { ErrorMessage } from '../types/shared-types'

/**
 * The recorded error URL used to be `window.location.href` verbatim, so any
 * secret sitting in the query string or fragment (OAuth tokens, magic links,
 * password reset tokens) was uploaded alongside the error.
 */
describe('ErrorListener recorded url', () => {
	const cleanups: (() => void)[] = []

	const listen = () => {
		const captured: ErrorMessage[] = []
		cleanups.push(
			ErrorListener((e) => captured.push(e), {
				enablePromisePatch: false,
			}),
		)
		return captured
	}

	afterEach(() => {
		while (cleanups.length) cleanups.pop()!()
		window.history.replaceState({}, '', '/')
	})

	it('redacts secret query parameters from the error url', () => {
		window.history.replaceState({}, '', '/victim?access_token=SECRET_QUERY')
		const captured = listen()

		window.onerror?.('boom', 'app.js', 1, 1, new Error('boom'))

		expect(captured).toHaveLength(1)
		expect(captured[0].url).not.toContain('SECRET_QUERY')
		expect(captured[0].url).toContain('access_token=REDACTED')
	})

	it('strips token-bearing fragments from the error url', () => {
		window.history.replaceState({}, '', '/victim#id_token=SECRET_HASH')
		const captured = listen()

		window.onerror?.('boom', 'app.js', 1, 1, new Error('boom'))

		expect(captured).toHaveLength(1)
		expect(captured[0].url).not.toContain('SECRET_HASH')
	})

	it('preserves the path and harmless params', () => {
		window.history.replaceState({}, '', '/dashboard?page=2#section')
		const captured = listen()

		window.onerror?.('boom', 'app.js', 1, 1, new Error('boom'))

		expect(captured[0].url).toContain('/dashboard')
		expect(captured[0].url).toContain('page=2')
		expect(captured[0].url).toContain('#section')
	})
})
