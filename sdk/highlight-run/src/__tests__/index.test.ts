import { getCorsUrlsPattern } from '../client/otel'
import { GenerateSecureID } from '../client/utils/secure-id'

describe('getCorsUrlsPattern', () => {
	it('handles `tracingOrigins: false` correctly', () => {
		expect(getCorsUrlsPattern(false)).toEqual(/^$/)
	})

	it('handles `tracingOrigins: true` correctly', () => {
		expect(getCorsUrlsPattern(true)).toEqual([
			/localhost/,
			/^\//,
			/localhost:3000/,
		])
	})

	it('handles `tracingOrigins: [string, string]` correctly', () => {
		expect(
			getCorsUrlsPattern([
				'example.com',
				'localhost:3000',
				'pub.highlight.io',
			]),
		).toEqual([/example.com/, /localhost:3000/, /pub.highlight.io/])
	})
})

describe('GenerateSecureID', () => {
	it('should generate random IDs when no key is provided', () => {
		const id1 = GenerateSecureID()
		const id2 = GenerateSecureID()

		expect(id1.length).toBe(28)
		expect(id2.length).toBe(28)
		expect(id1).not.toBe(id2)
	})

	it('should generate deterministic IDs when key is provided', () => {
		const key = 'test-key-123'
		const id1 = GenerateSecureID(key)
		const id2 = GenerateSecureID(key)

		expect(id1.length).toBe(28)
		expect(id2.length).toBe(28)
		expect(id1).toBe(id2)
	})

	it('should generate different IDs for different keys', () => {
		const keys = [
			'task123',
			'task124',
			'task125',
			'user1',
			'user2',
			'user3',
			'project-a',
			'project-b',
			'project-c',
			'session-1',
			'session-2',
			'session-3',
			'ld-credential-1',
			'ld-credential-2',
			'ld-credential-3',
		]

		const ids = keys.map((key) => GenerateSecureID(key))
		const uniqueIds = new Set(ids)

		// Should have no collisions (all IDs should be unique)
		expect(uniqueIds.size).toBe(keys.length)

		// Each ID should be 28 characters
		ids.forEach((id) => {
			expect(id.length).toBe(28)
			// Should only contain valid characters
			expect(id).toMatch(/^[a-zA-Z0-9]{28}$/)
		})
	})
})
