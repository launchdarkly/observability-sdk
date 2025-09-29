import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { RecordSDK } from '../sdk/record'

const recordSpy = vi.fn((...args: any[]) => {
	return vi.fn()
})
vi.mock('rrweb', () => ({
	addCustomEvent: vi.fn(),
	record: (...args: any[]) => recordSpy(...args),
}))

// Mock GraphQL generated SDK used inside RecordSDK.initializeSession
vi.mock('../client/graph/generated/operations', () => ({
	getSdk: () => ({
		initializeSession: vi.fn().mockResolvedValue({
			initializeSession: {
				secure_id: 'test-session',
				project_id: '1',
			},
		}),
	}),
}))

// Provide a minimal worker mock used by RecordSDK
vi.mock('../client/workers/highlight-client-worker?worker&inline', () => ({
	default: class MockWorker {
		onmessage: any
		postMessage() {}
	},
}))

describe('RecordSDK -> rrweb.record config wiring', () => {
	const originalWindow = global.window

	beforeEach(() => {
		vi.useFakeTimers()
		recordSpy.mockClear()
	})

	afterEach(() => {
		vi.useRealTimers()
		global.window = originalWindow
	})

	it('passes default ignore/block/mask options to rrweb.record', async () => {
		const sdk = new RecordSDK({
			organizationID: '1',
			sessionSecureID: 'seed',
		})

		await sdk.start()

		expect(recordSpy).toHaveBeenCalledTimes(1)
		const arg = (recordSpy.mock.calls as any[])[0][0]
		// Defaults
		expect(arg.ignoreClass).toBe('highlight-ignore')
		expect(arg.blockClass).toBe('highlight-block')
		expect(arg.ignoreSelector).toBeUndefined()
		expect(arg.blockSelector).toBeUndefined()
		expect(arg.maskTextClass).toBeUndefined()
		expect(arg.maskTextSelector).toBeUndefined()
	})

	it('respects overridden ignore/block/mask options', async () => {
		const sdk = new RecordSDK({
			organizationID: '1',
			sessionSecureID: 'seed',
			ignoreClass: 'custom-ignore',
			ignoreSelector: '.ignore-me',
			blockClass: 'custom-block',
			blockSelector: '.block-me',
			maskTextClass: 'mask-this',
			maskTextSelector: '.mask-me',
		})

		await sdk.start()

		expect(recordSpy).toHaveBeenCalledTimes(1)
		const arg = (recordSpy.mock.calls as any[])[0][0]
		expect(arg.ignoreClass).toBe('custom-ignore')
		expect(arg.ignoreSelector).toBe('.ignore-me')
		expect(arg.blockClass).toBe('custom-block')
		expect(arg.blockSelector).toBe('.block-me')
		expect(arg.maskTextClass).toBe('mask-this')
		expect(arg.maskTextSelector).toBe('.mask-me')
	})
})
