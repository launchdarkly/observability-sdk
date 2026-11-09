import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ObservabilityClient } from './ObservabilityClient'
import { Platform } from 'react-native'
import {
	SESSION_RESUME_THRESHOLD_MS,
	SESSION_STORAGE_KEY,
} from '../constants/sessions'

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

describe('ObservabilityClient — platform attributes on the Resource', () => {
	// React Native is aliased to src/__mocks__/react-native.ts, whose Platform
	// is a plain mutable object; set it per test and restore afterwards.
	const platform = Platform as unknown as {
		OS: string
		Version?: number | string
		constants?: Record<string, unknown>
	}
	const original = {
		OS: platform.OS,
		Version: platform.Version,
		constants: platform.constants,
	}

	beforeEach(() => {
		platform.OS = 'ios'
		platform.Version = '17.0'
		platform.constants = {
			systemName: 'iOS',
			osVersion: '17.0',
			interfaceIdiom: 'phone',
		}
	})

	afterEach(() => {
		platform.OS = original.OS
		platform.Version = original.Version
		platform.constants = original.constants
	})

	// Builds a fully-initialized client with the telemetry pipeline stubbed out
	// and captures the OTel Resource that would back every exporter.
	async function buildInitializedResource(
		options: ConstructorParameters<typeof ObservabilityClient>[1] = {},
	): Promise<Record<string, unknown>> {
		const client = new ObservabilityClient('projectKey', {
			disableErrorTracking: true,
			...options,
		})

		let resource: { attributes: Record<string, unknown> } | undefined
		;(
			client as unknown as {
				instrumentationManager: {
					initialize: (...args: unknown[]) => Promise<void>
				}
			}
		).instrumentationManager.initialize = vi.fn(async (r: unknown) => {
			resource = r as { attributes: Record<string, unknown> }
		})

		const ready = await client.whenInitialized()
		expect(ready).toBe(true)
		expect(resource).toBeDefined()
		return resource!.attributes
	}

	it('carries the platform os/device attributes on the Resource', async () => {
		const attributes = await buildInitializedResource()

		expect(attributes).toMatchObject({
			'os.type': 'darwin',
			'os.name': 'iOS',
			'os.version': '17.0',
		})
	})

	it('lets user resourceAttributes override the platform attributes', async () => {
		const attributes = await buildInitializedResource({
			resourceAttributes: {
				'os.name': 'CustomOS',
				'device.model.identifier': 'user-model',
			},
		})

		expect(attributes).toMatchObject({
			// Platform-derived values are still present...
			'os.type': 'darwin',
			'os.version': '17.0',
			// ...but the user values win.
			'os.name': 'CustomOS',
			'device.model.identifier': 'user-model',
		})
	})

	it('carries session.previous_id when a new session replaces a stale one', async () => {
		const now = Date.now()
		localStorage.setItem(
			SESSION_STORAGE_KEY,
			JSON.stringify({
				sessionId: 'stale-session-id',
				startTime: now - SESSION_RESUME_THRESHOLD_MS * 2,
				lastActivityTime: now - SESSION_RESUME_THRESHOLD_MS - 1_000,
				reloadCount: 0,
			}),
		)
		try {
			const attributes = await buildInitializedResource()

			expect(attributes['session.previous_id']).toBe('stale-session-id')
			expect(attributes['session.id']).not.toBe('stale-session-id')
		} finally {
			localStorage.clear()
		}
	})

	it('omits session.previous_id when there was no previous session', async () => {
		localStorage.clear()
		const attributes = await buildInitializedResource()

		expect(attributes).not.toHaveProperty('session.previous_id')
	})
})
