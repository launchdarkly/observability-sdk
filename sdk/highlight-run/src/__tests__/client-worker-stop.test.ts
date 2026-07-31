import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Highlight } from '../client'
import { MessageType, StopReason } from '../client/workers/types'

const shutdown = vi.fn()
vi.mock('../client/otel', async (importOriginal) => ({
	...(await importOriginal<typeof import('../client/otel')>()),
	shutdown: () => shutdown(),
}))

describe('Highlight worker stop handling', () => {
	let highlight: Highlight

	beforeEach(() => {
		vi.useFakeTimers()
		shutdown.mockClear()
		highlight = new Highlight({
			organizationID: '1',
			sessionSecureID: 'seed',
			backendUrl: 'https://pub.observability.app.launchdarkly.com',
		})
	})

	afterEach(() => {
		vi.useRealTimers()
	})

	it('keeps telemetry running when the worker stops replay', () => {
		highlight._worker.onmessage({
			data: {
				response: {
					type: MessageType.Stop,
					reason: StopReason.UnrecoverableError,
				},
			},
		} as MessageEvent<any>)

		expect(shutdown).not.toHaveBeenCalled()
		expect(highlight.state).toBe('NotRecording')
		// Keeps the page visibility listener, which outlives a stop, from restarting us.
		expect(highlight.manualStopped).toBe(true)
	})

	it('shuts telemetry down when the SDK itself stops', () => {
		highlight.stopRecording(true)

		expect(shutdown).toHaveBeenCalledTimes(1)
		expect(highlight.state).toBe('NotRecording')
	})
})
