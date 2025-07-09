import { describe, it, expect } from 'vitest'
import { getCorsUrlsPattern, getIgnoreUrlsPattern } from './networkUtils'

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

describe('getIgnoreUrlsPattern', () => {
	it('returns ignore-all pattern when tracingOrigins is false', () => {
		const result = getIgnoreUrlsPattern(false)
		expect(result).toEqual([/.*/])
	})

	it('returns ignore-all pattern when tracingOrigins is undefined', () => {
		const result = getIgnoreUrlsPattern(undefined)
		expect(result).toEqual([/.*/])
	})

	it('returns pattern to ignore non-localhost URLs when tracingOrigins is true', () => {
		const result = getIgnoreUrlsPattern(true)
		expect(result).toHaveLength(1)
		expect(result[0]).toBeInstanceOf(RegExp)

		const ignorePattern = result[0] as RegExp
		expect(ignorePattern.test('http://localhost/api')).toBe(false)
		expect(ignorePattern.test('https://localhost/api')).toBe(false)
		expect(ignorePattern.test('/api/test')).toBe(false)

		expect(ignorePattern.test('https://example.com/api')).toBe(true)
	})

	it('returns pattern to ignore URLs that do not match tracingOrigins array', () => {
		const result = getIgnoreUrlsPattern(['api.example.com'])
		expect(result).toHaveLength(1)
		expect(result[0]).toBeInstanceOf(RegExp)

		const ignorePattern = result[0] as RegExp
		expect(ignorePattern.test('https://api.example.com/test')).toBe(false)

		expect(ignorePattern.test('https://other.com/api')).toBe(true)
	})

	it('returns ignore-all pattern when tracingOrigins is empty array', () => {
		const result = getIgnoreUrlsPattern([])
		expect(result).toEqual([/.*/])
	})
})
