import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ObservabilityClient } from './ObservabilityClient'

describe('ObservabilityClient — stop during async init', () => {
	beforeEach(() => {
		// Ensure a clean cold-start so init() takes the normal path.
		try {
			;(globalThis as { localStorage?: Storage }).localStorage?.clear()
		} catch {
			// no localStorage in this environment; nothing to clear
		}
	})

	it('does not revive the client if stop() runs while init() is in flight', async () => {
		// The constructor kicks off init() asynchronously; it is still awaiting
		// sessionManager.initialize() when we synchronously call stop() below.
		const client = new ObservabilityClient('sdkKey', {})

		// stop() lands mid-init. Its first action flips the `stopped` guard, so the
		// in-flight init() must abort rather than mark the client initialized.
		await client.stop()

		// Give any lingering init() microtasks a chance to (incorrectly) complete.
		await new Promise((resolve) => setTimeout(resolve, 50))

		expect(client.getIsInitialized()).toBe(false)
	})

	it('whenInitialized() resolves true once init settles successfully', async () => {
		const client = new ObservabilityClient('sdkKey', {})

		const ready = await client.whenInitialized()

		expect(ready).toBe(true)
		expect(client.getIsInitialized()).toBe(true)
	})

	it('whenInitialized() resolves false when stop() aborts init (no infinite wait)', async () => {
		const client = new ObservabilityClient('sdkKey', {})
		await client.stop()

		// Must settle (not hang) so awaiters/pollers terminate on a torn-down client.
		const ready = await client.whenInitialized()

		expect(ready).toBe(false)
		expect(client.getIsInitialized()).toBe(false)
	})

	it('whenInitialized() resolves false when init() throws (does not hang forever)', async () => {
		const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
		const client = new ObservabilityClient('sdkKey', {})

		// Force init() to fail after its first await, before it can mark the client
		// ready. Installed synchronously (before the first await resolves) so the
		// rejecting mock is in place when init() reaches instrumentation setup.
		const internals = client as unknown as {
			instrumentationManager: {
				initialize: (...args: unknown[]) => Promise<void>
			}
		}
		internals.instrumentationManager.initialize = vi
			.fn()
			.mockRejectedValue(new Error('init boom'))

		const ready = await client.whenInitialized()

		expect(ready).toBe(false)
		expect(client.getIsInitialized()).toBe(false)
		errorSpy.mockRestore()
	})
})
