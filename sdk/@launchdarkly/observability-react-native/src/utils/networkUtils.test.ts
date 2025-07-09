import { describe, it, expect } from 'vitest'
import {
	getCorsUrlsPattern,
	shouldNetworkRequestBeTraced,
} from './networkUtils'

describe('getCorsUrlsPattern', () => {
	it('returns localhost patterns when tracingOrigins is true', () => {
		const result = getCorsUrlsPattern(true)
		expect(result).toEqual([
			/localhost/,
			/^\//,
			/^http:\/\/localhost/,
			/^https:\/\/localhost/,
		])
	})

	it('returns mapped patterns when tracingOrigins is array', () => {
		const result = getCorsUrlsPattern(['example.com', /.*api.*/])
		expect(result).toHaveLength(2)
		expect(result[0]).toBeInstanceOf(RegExp)
		expect(result[1]).toBeInstanceOf(RegExp)
	})

	it('returns empty pattern when tracingOrigins is false', () => {
		const result = getCorsUrlsPattern(false)
		expect(result).toEqual([/^$/])
	})

	it('returns empty pattern when tracingOrigins is undefined', () => {
		const result = getCorsUrlsPattern(undefined)
		expect(result).toEqual([/^$/])
	})
})

describe('shouldNetworkRequestBeTraced', () => {
	it('returns true for localhost when tracingOrigins is true', () => {
		expect(
			shouldNetworkRequestBeTraced('http://localhost/api', true, []),
		).toBe(true)
		expect(
			shouldNetworkRequestBeTraced('https://localhost/api', true, []),
		).toBe(true)
	})

	it('returns true for relative URLs when tracingOrigins is true', () => {
		expect(shouldNetworkRequestBeTraced('/api/test', true, [])).toBe(true)
	})

	it('returns false for external URLs when tracingOrigins is true', () => {
		expect(
			shouldNetworkRequestBeTraced('https://example.com/api', true, []),
		).toBe(false)
	})

	it('returns true when URL matches tracingOrigins array', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.example.com/test',
				['api.example.com'],
				[],
			),
		).toBe(true)
	})

	it('returns false when URL is in blocklist', () => {
		expect(
			shouldNetworkRequestBeTraced('https://blocked.com/api', true, [
				'blocked.com',
			]),
		).toBe(false)
	})

	it('returns false when tracingOrigins is empty array', () => {
		expect(
			shouldNetworkRequestBeTraced('https://example.com/api', [], []),
		).toBe(false)
	})

	it('supports regex patterns in tracingOrigins', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://api.example.com/test',
				[/.*example\.com.*/],
				[],
			),
		).toBe(true)
	})

	it('returns false when tracingOrigins is false', () => {
		expect(
			shouldNetworkRequestBeTraced('https://example.com/api', false, []),
		).toBe(false)
	})

	it('handles case insensitive blocklist matching', () => {
		expect(
			shouldNetworkRequestBeTraced('https://BLOCKED.COM/api', true, [
				'blocked.com',
			]),
		).toBe(false)
	})
})
