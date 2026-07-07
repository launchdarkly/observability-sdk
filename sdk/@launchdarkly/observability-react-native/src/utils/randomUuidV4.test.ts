import { afterEach, describe, expect, it, vi } from 'vitest'
import {
	formatDataAsUuidV4,
	isSecureRandomAvailable,
	randomUuidV4,
} from './randomUuidV4'

const UUID_V4_REGEX =
	/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('randomUuidV4', () => {
	afterEach(() => {
		vi.restoreAllMocks()
		vi.unstubAllGlobals()
	})

	it('produces a valid RFC-4122 v4 UUID', () => {
		const id = randomUuidV4()
		expect(id).toMatch(UUID_V4_REGEX)
	})

	it('generates unique ids across many calls', () => {
		const ids = new Set(Array.from({ length: 1000 }, () => randomUuidV4()))
		expect(ids.size).toBe(1000)
	})

	it('uses crypto.getRandomValues when available', () => {
		const getRandomValues = vi.fn((array: Uint8Array) => {
			for (let i = 0; i < array.length; i++) array[i] = i
			return array
		})
		vi.stubGlobal('crypto', { getRandomValues })

		expect(isSecureRandomAvailable()).toBe(true)
		const id = randomUuidV4()

		expect(getRandomValues).toHaveBeenCalledTimes(1)
		expect(id).toMatch(UUID_V4_REGEX)
		// bytes 0..15, with version/variant bits forced.
		expect(id).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
	})

	it('falls back to Math.random when no secure RNG is present', () => {
		vi.stubGlobal('crypto', undefined)
		const randomSpy = vi.spyOn(Math, 'random').mockReturnValue(0)

		expect(isSecureRandomAvailable()).toBe(false)
		const id = randomUuidV4()

		expect(randomSpy).toHaveBeenCalled()
		expect(id).toMatch(UUID_V4_REGEX)
	})

	it('forces the version (4) and variant (8/9/a/b) nibbles', () => {
		// All bits set: verifies masking of the version and variant fields.
		const id = formatDataAsUuidV4(new Array(16).fill(0xff))
		expect(id).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff')
	})
})
