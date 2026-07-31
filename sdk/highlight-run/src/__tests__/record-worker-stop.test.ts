import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RecordSDK } from '../sdk/record'
import { MessageType, StopReason } from '../client/workers/types'

const recordStop = vi.fn()
const recordSpy = vi.fn(() => recordStop)
vi.mock('@highlight-run/rrweb', () => ({
	addCustomEvent: vi.fn(),
	record: () => recordSpy(),
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

async function startedSDK(
	options: Partial<ConstructorParameters<typeof RecordSDK>[0]> = {},
): Promise<RecordSDK> {
	const sdk = new RecordSDK({
		organizationID: '1',
		sessionSecureID: 'seed',
		...options,
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
		recordSpy.mockClear()
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

	// The page visibility listener stays attached once recording has ever started, so it is the
	// one thing that can restart us after the worker has given up.
	it('stays stopped when the tab becomes visible again', async () => {
		const sdk = await startedSDK({ disableBackgroundRecording: true })

		postToSDK(sdk, {
			type: MessageType.Stop,
			reason: StopReason.UnrecoverableError,
		})
		recordSpy.mockClear()
		sdk._lastVisibilityChangeTime = 0 // clear the debounce frozen fake timers impose
		await sdk._visibilityHandler(false)

		expect(recordSpy).not.toHaveBeenCalled()
		expect(sdk.getRecordingState()).toBe('NotRecording')
	})

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
