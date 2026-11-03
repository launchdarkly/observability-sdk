import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { EventType, type eventWithTime } from '@highlight-run/rrweb-types'
import { record } from '@highlight-run/rrweb'
import { RecordSDK } from './record'
import { SNAPSHOT_SETTINGS } from '../client/constants/sessions'
import { MessageType } from '../client/workers/types'

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

// Stands in for the upload worker: acknowledges each payload with a size the
// test picks, so a full snapshot alone can clear the canvas threshold.
let sizeOf: (events: eventWithTime[]) => number
vi.mock('../client/workers/highlight-client-worker?worker&inline', () => ({
	default: class MockWorker {
		onmessage: any
		postMessage({ message }: any) {
			if (message?.type !== MessageType.AsyncEvents) return
			queueMicrotask(() =>
				this.onmessage?.({
					data: {
						response: {
							type: MessageType.AsyncEvents,
							id: message.id,
							eventsSize: sizeOf(message.events),
							compressedSize: 1,
						},
					},
				}),
			)
		}
	},
}))

const oversized = SNAPSHOT_SETTINGS.canvas.bytes * 2
const hasFullSnapshot = (events: eventWithTime[]) =>
	events.some((e) => e.type === EventType.FullSnapshot)

describe('RecordSDK full snapshot threshold', () => {
	let sdk: RecordSDK | undefined

	beforeEach(() => {
		vi.useFakeTimers()
	})

	afterEach(() => {
		sdk?.stop(true)
		sdk = undefined
		vi.restoreAllMocks()
		vi.useRealTimers()
	})

	const startRecording = async () => {
		sdk = new RecordSDK({
			organizationID: '1',
			sessionSecureID: 'seed',
			enableCanvasRecording: true,
		})
		await sdk.start()
		return vi.spyOn(record, 'takeFullSnapshot')
	}

	it('does not re-snapshot an idle page whose snapshot alone clears the threshold', async () => {
		sizeOf = (events) => (hasFullSnapshot(events) ? oversized : 0)
		const takeFullSnapshot = await startRecording()

		await vi.advanceTimersByTimeAsync(SNAPSHOT_SETTINGS.canvas.time * 6)

		expect(takeFullSnapshot).not.toHaveBeenCalled()
	})

	it('still snapshots once enough non-snapshot bytes build up', async () => {
		sizeOf = (events) => (hasFullSnapshot(events) ? 0 : oversized)
		const takeFullSnapshot = await startRecording()

		// Keep rrweb emitting so every upload carries events.
		const tick = setInterval(() => record.addCustomEvent('tick', {}), 500)
		await vi.advanceTimersByTimeAsync(SNAPSHOT_SETTINGS.canvas.time * 2)
		clearInterval(tick)

		expect(takeFullSnapshot).toHaveBeenCalled()
	})
})
