import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RecordSDK } from '../sdk/record'
import { MessageType, StopReason } from '../client/workers/types'

const recordStop = vi.fn()
vi.mock('@highlight-run/rrweb', () => ({
	addCustomEvent: vi.fn(),
	record: () => recordStop,
}))

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

vi.mock('../client/workers/highlight-client-worker?worker&inline', () => ({
	default: class MockWorker {
		onmessage: any
		postMessage() {}
	},
}))

async function startedSDK(): Promise<RecordSDK> {
	const sdk = new RecordSDK({
		organizationID: '1',
		sessionSecureID: 'seed',
	})
	await sdk.start()
	return sdk
}

/** Delivers a worker response to the SDK the way the real worker would. */
function postToSDK(sdk: RecordSDK, response: unknown) {
	sdk._worker.onmessage({ data: { response } } as MessageEvent<any>)
}

describe('RecordSDK worker stop handling', () => {
	beforeEach(() => {
		vi.useFakeTimers()
		recordStop.mockClear()
	})

	afterEach(() => {
		vi.useRealTimers()
	})

	it.each([
		StopReason.PushPayloadTimeout,
		StopReason.UnrecoverableError,
		StopReason.RetriesExhausted,
	])(
		'releases the recorder when the worker stops us (%s)',
		async (reason) => {
			const sdk = await startedSDK()
			sdk.events.push({ type: 3, data: {}, timestamp: 1 } as any)

			postToSDK(sdk, { type: MessageType.Stop, reason })

			expect(recordStop).toHaveBeenCalledTimes(1)
			expect(sdk.events).toEqual([])
			expect(sdk.getRecordingState()).toBe('NotRecording')
		},
	)

	it('keeps recording while the worker only reports uploads', async () => {
		const sdk = await startedSDK()
		sdk.events.push({ type: 3, data: {}, timestamp: 1 } as any)

		postToSDK(sdk, {
			type: MessageType.AsyncEvents,
			id: 1,
			eventsSize: 100,
			compressedSize: 50,
		})

		expect(recordStop).not.toHaveBeenCalled()
		expect(sdk.events).toHaveLength(1)
		expect(sdk.getRecordingState()).toBe('Recording')
	})
})
