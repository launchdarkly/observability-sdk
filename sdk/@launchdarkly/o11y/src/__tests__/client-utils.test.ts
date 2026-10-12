import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import * as utils from '../__mocks__/client-utils'
import * as errors from '../__mocks__/other-utils'
import * as storage from '../__mocks__/other-utils'
import * as privacy from '../__mocks__/other-utils'
import * as secureId from '../__mocks__/other-utils'
import * as graph from '../__mocks__/other-utils'

describe('Client Utils', () => {
	const originalWindow = global.window
	const originalConsole = global.console

	beforeEach(() => {
		// Mock window
		global.window = {
			...originalWindow,
			localStorage: {
				getItem: vi.fn(),
				setItem: vi.fn(),
				removeItem: vi.fn(),
			},
			location: {
				href: 'https://example.com/path?query=string',
				hostname: 'example.com',
				pathname: '/path',
				search: '?query=string',
			},
			navigator: {
				userAgent: 'Mozilla/5.0 Test User Agent',
			},
		} as any

		// Mock console
		global.console = {
			...console,
			error: vi.fn(),
			warn: vi.fn(),
			log: vi.fn(),
		}
	})

	afterEach(() => {
		global.window = originalWindow
		global.console = originalConsole
		vi.clearAllMocks()
	})

	describe('Utils', () => {
		it('should generate a valid UUID', () => {
			const uuid = utils.generateUUID()
			expect(uuid).toMatch(
				/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
			)
		})

		it('should check if a value is an object', () => {
			expect(utils.isObject({})).toBe(true)
			expect(utils.isObject([])).toBe(false)
			expect(utils.isObject(null)).toBe(false)
			expect(utils.isObject(undefined)).toBe(false)
			expect(utils.isObject('string')).toBe(false)
			expect(utils.isObject(123)).toBe(false)
		})

		it('should safely stringify objects', () => {
			const obj = { name: 'test', value: 123 }
			expect(utils.safeStringify(obj)).toBe(JSON.stringify(obj))

			// Test circular references
			const circular: any = { name: 'circular' }
			circular.self = circular
			expect(utils.safeStringify(circular)).toContain('circular')
		})

		it('should get browser details', () => {
			const details = utils.getBrowserDetails()
			expect(details).toBeDefined()
			expect(details.userAgent).toBeDefined()
		})

		it('should parse URLs', () => {
			const url = 'https://example.com/path?query=string'
			const parsed = utils.parseUrl(url)

			expect(parsed.hostname).toBe('example.com')
			expect(parsed.pathname).toBe('/path')
			expect(parsed.search).toBe('?query=string')
		})

		it('should check if a URL is external', () => {
			expect(utils.isExternalUrl('https://example.com/path')).toBe(false) // Same domain
			expect(utils.isExternalUrl('https://external.com/path')).toBe(true) // Different domain
		})

		it('should get current URL path', () => {
			const path = utils.getUrlPath()
			expect(path).toBe('/path?query=string')
		})

		it('should sanitize objects for values that should be redacted', () => {
			const input = {
				username: 'testuser',
				password: 'secret',
				creditCard: '1234-5678-9012-3456',
				nested: {
					token: 'auth-token',
					secret: 'private-key',
				},
			}

			const sanitized = utils.sanitizeObjectForValues(input)

			expect(sanitized.username).toBe('testuser')
			expect(sanitized.password).toBe('[REDACTED]')
			expect(sanitized.creditCard).toBe('[REDACTED]')
			expect(sanitized.nested.token).toBe('[REDACTED]')
			expect(sanitized.nested.secret).toBe('[REDACTED]')
		})

		it('should debounce functions', async () => {
			const fn = vi.fn()
			const debounced = utils.debounce(fn, 50)

			debounced()
			debounced()
			debounced()

			// Fast forward time
			await new Promise((resolve) => setTimeout(resolve, 100))

			expect(fn).toHaveBeenCalledTimes(1)
		})
	})

	describe('Storage Utils', () => {
		it('should set items in localStorage', () => {
			storage.setItem('test-key', 'test-value')
			expect(window.localStorage.setItem).toHaveBeenCalledWith(
				'test-key',
				'test-value',
			)
		})

		it('should handle errors when setting items in localStorage', () => {
			window.localStorage.setItem = vi.fn().mockImplementation(() => {
				throw new Error('localStorage error')
			})

			// Should not throw
			expect(() =>
				storage.setItem('test-key', 'test-value'),
			).not.toThrow()
			expect(console.error).toHaveBeenCalled()
		})

		it('should get items from localStorage', () => {
			window.localStorage.getItem = vi.fn().mockReturnValue('test-value')

			const value = storage.getItem('test-key')
			expect(value).toBe('test-value')
			expect(window.localStorage.getItem).toHaveBeenCalledWith('test-key')
		})

		it('should try to parse JSON values from localStorage', () => {
			const jsonValue = JSON.stringify({ name: 'test', value: 123 })
			window.localStorage.getItem = vi.fn().mockReturnValue(jsonValue)

			const value = storage.getItem('test-key')
			expect(value).toEqual({ name: 'test', value: 123 })
		})

		it('should handle errors when getting items from localStorage', () => {
			window.localStorage.getItem = vi.fn().mockImplementation(() => {
				throw new Error('localStorage error')
			})

			const value = storage.getItem('test-key')
			expect(value).toBeNull()
			expect(console.error).toHaveBeenCalled()
		})

		it('should remove items from localStorage', () => {
			storage.removeItem('test-key')
			expect(window.localStorage.removeItem).toHaveBeenCalledWith(
				'test-key',
			)
		})
	})

	describe('Error Utils', () => {
		it('should format error objects', () => {
			const error = new Error('Test error')
			error.stack = 'Error: Test error\n    at test.js:1:1'

			const formatted = errors.formatError(error)

			expect(formatted.name).toBe('Error')
			expect(formatted.message).toBe('Test error')
			expect(formatted.stack).toBe(
				'Error: Test error\n    at test.js:1:1',
			)
		})

		it('should handle errors without stacks', () => {
			const error = new Error('Test error')
			error.stack = undefined

			const formatted = errors.formatError(error)

			expect(formatted.name).toBe('Error')
			expect(formatted.message).toBe('Test error')
			expect(formatted.stack).toBe('')
		})

		it('should handle non-Error objects', () => {
			const nonError = { message: 'Not an error' }

			const formatted = errors.formatError(nonError as any)

			expect(formatted.name).toBe('Unknown Error')
			expect(formatted.message).toBe('Not an error')
		})
	})

	describe('Privacy Utils', () => {
		it('should redact sensitive values', () => {
			const testCases = [
				{ input: 'password', expected: true },
				{ input: 'secret', expected: true },
				{ input: 'token', expected: true },
				{ input: 'api_key', expected: true },
				{ input: 'credit_card', expected: true },
				{ input: 'username', expected: false },
				{ input: 'email', expected: false },
				{ input: 'name', expected: false },
			]

			for (const { input, expected } of testCases) {
				expect(privacy.shouldRedact(input)).toBe(expected)
			}
		})

		it('should mask sensitive values by default', () => {
			expect(privacy.maskValue('password123')).toBe('[REDACTED]')
		})

		it('should use custom masking when provided', () => {
			const customMasker = (value: any) => `HIDDEN: ${typeof value}`
			expect(privacy.maskValue('password123', customMasker)).toBe(
				'HIDDEN: string',
			)
		})
	})

	describe('Secure ID Utils', () => {
		it('should generate a secure ID', () => {
			const id = secureId.getSecureID()
			expect(id).toBeDefined()
			expect(id.length).toBeGreaterThan(0)
		})

		it('should return the same ID on subsequent calls', () => {
			const id1 = secureId.getSecureID()
			const id2 = secureId.getSecureID()
			expect(id1).toBe(id2)
		})
	})

	describe('Graph Utils', () => {
		// Mock fetch
		beforeEach(() => {
			global.fetch = vi.fn().mockResolvedValue({
				ok: true,
				json: vi
					.fn()
					.mockResolvedValue({ data: { result: 'success' } }),
			}) as any
		})

		it('should send GraphQL requests', async () => {
			const response = await graph.request({
				url: 'https://api.example.com/graphql',
				query: 'query { test }',
				variables: { id: '123' },
			})

			expect(response).toEqual({ data: { result: 'success' } })
			expect(fetch).toHaveBeenCalledWith(
				'https://api.example.com/graphql',
				expect.objectContaining({
					method: 'POST',
					headers: expect.any(Object),
					body: expect.any(String),
				}),
			)
		})

		it('should handle GraphQL errors', async () => {
			global.fetch = vi.fn().mockResolvedValue({
				ok: false,
				status: 400,
				statusText: 'Bad Request',
			}) as any

			await expect(
				graph.request({
					url: 'https://api.example.com/graphql',
					query: 'query { test }',
				}),
			).rejects.toThrow()
		})
	})
})
