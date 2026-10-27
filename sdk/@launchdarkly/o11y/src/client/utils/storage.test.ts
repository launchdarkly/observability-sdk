import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const setMock = vi.fn()
const removeMock = vi.fn()

vi.mock('js-cookie', () => ({
	default: {
		get: vi.fn(() => undefined),
		set: (...args: unknown[]) => setMock(...args),
		remove: (...args: unknown[]) => removeMock(...args),
	},
}))

import {
	cookieStorage,
	setCookieDomain,
	setCookieWriteEnabled,
} from './storage'

describe('cookieStorage sessionCookieDomain', () => {
	beforeEach(() => {
		setMock.mockClear()
		removeMock.mockClear()
		setCookieWriteEnabled(true)
		setCookieDomain(undefined)
	})

	afterEach(() => {
		setCookieDomain(undefined)
	})

	it('writes a host-only cookie by default (no domain attribute)', () => {
		cookieStorage.setItem('key', 'value')
		expect(setMock).toHaveBeenCalledTimes(1)
		expect(setMock).toHaveBeenCalledWith(
			'key',
			'value',
			expect.objectContaining({ expires: expect.any(Date) }),
		)
		const attributes = setMock.mock.calls[0][2] as Record<string, unknown>
		expect('domain' in attributes).toBe(false)
	})

	it('passes the configured domain when writing a cookie', () => {
		setCookieDomain('example.com')
		cookieStorage.setItem('key', 'value')
		expect(setMock).toHaveBeenCalledWith(
			'key',
			'value',
			expect.objectContaining({
				expires: expect.any(Date),
				domain: 'example.com',
			}),
		)
	})

	it('passes the domain to remove so a domain cookie can be deleted', () => {
		setCookieDomain('example.com')
		cookieStorage.removeItem('key')
		expect(removeMock).toHaveBeenCalledWith('key', {
			domain: 'example.com',
		})
	})

	it('removes without a domain when no sessionCookieDomain is set', () => {
		cookieStorage.removeItem('key')
		expect(removeMock).toHaveBeenCalledWith('key', undefined)
	})

	it('resets the domain when set back to undefined', () => {
		setCookieDomain('example.com')
		setCookieDomain(undefined)
		cookieStorage.setItem('key', 'value')
		const attributes = setMock.mock.calls[0][2] as Record<string, unknown>
		expect('domain' in attributes).toBe(false)
	})

	it('does not write when cookie writes are disabled', () => {
		setCookieWriteEnabled(false)
		setCookieDomain('example.com')
		cookieStorage.setItem('key', 'value')
		expect(setMock).not.toHaveBeenCalled()
	})
})
