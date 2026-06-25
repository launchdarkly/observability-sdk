import { describe, it, expect, vi, afterEach } from 'vitest'
import { getCorsUrlsPattern, shouldNetworkRequestBeTraced } from './utils'

describe('getCorsUrlsPattern', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('returns a nothing-matching regex when tracingOrigins is false', () => {
		expect(getCorsUrlsPattern(false)).toEqual(/^$/)
	})

	it('treats string entries as literal substrings (escapes regex metachars)', () => {
		const patterns = getCorsUrlsPattern(['api.example.com']) as RegExp[]
		expect(patterns[0]).toEqual(/api\.example\.com/)
		// The literal host matches.
		expect(patterns[0].test('https://api.example.com/posts')).toBe(true)
		// `.` must not act as a wildcard, so look-alike hosts do not match.
		expect(patterns[0].test('https://apiXexampleYcom/posts')).toBe(false)
	})

	it('passes RegExp entries through unchanged', () => {
		const re = /^https?:\/\/backend\./
		const patterns = getCorsUrlsPattern([re]) as RegExp[]
		expect(patterns[0]).toBe(re)
	})

	it('anchors the page host at the origin position', () => {
		vi.stubGlobal('window', {
			location: { host: 'example.com' },
		})
		const patterns = getCorsUrlsPattern(true) as RegExp[]
		expect(patterns).toEqual([
			/localhost/,
			/^\//,
			/^https?:\/\/([^/]+\.)?example\.com([:/?#]|$)/,
		])
		// Regression: an unanchored /example.com/ used to match third-party
		// URLs that carried the host as a query-parameter value.
		expect(
			patterns.some((p) =>
				p.test('https://api.third-party.test/?store=example.com'),
			),
		).toBe(false)
	})
})

describe('shouldNetworkRequestBeTraced', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('does not match the page host as a substring of a third-party URL', () => {
		vi.stubGlobal('window', {
			location: { host: 'example.com' },
		})
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.third-party.test/?store=example.com',
				true,
				[],
			),
		).toBe(false)
	})

	it('does not match a user-supplied host pattern against a query value', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.third-party.test/?store=example.com',
				[/example\.com/],
				[],
			),
		).toBe(false)
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.third-party.test/?store=example.com',
				['example.com'],
				[],
			),
		).toBe(false)
	})

	it('matches same-origin requests when tracingOrigins is true', () => {
		vi.stubGlobal('window', {
			location: { host: 'example.com' },
		})
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/foo',
				true,
				[],
			),
		).toBe(true)
	})

	it('matches subdomains of the page host when tracingOrigins is true', () => {
		vi.stubGlobal('window', {
			location: { host: 'example.com' },
		})
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.example.com/api/foo',
				true,
				[],
			),
		).toBe(true)
	})

	it('matches relative URLs when tracingOrigins is true', () => {
		expect(shouldNetworkRequestBeTraced('/api/foo', true, [])).toBe(true)
	})

	it('matches localhost when tracingOrigins is true', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'http://localhost:8080/api/foo',
				true,
				[],
			),
		).toBe(true)
	})

	it('honours urlBlocklist', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/v2/foo',
				true,
				['/api/v2'],
			),
		).toBe(false)
	})
})
