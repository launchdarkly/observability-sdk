import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
	MessageType,
	HighlightClientWorkerParams,
	HighlightClientWorkerResponse,
	InitializeMessage,
	IdentifyMessage,
	PropertiesMessage,
	ResetMessage,
	CustomEventResponse,
	StatusResponse,
} from './types'

interface TestWorker {
	postMessage: (params: HighlightClientWorkerParams) => void
	onmessage:
		| ((event: MessageEvent<HighlightClientWorkerResponse>) => void)
		| null
	terminate: () => void
}

function createResponseCollector(worker: TestWorker) {
	const responses: HighlightClientWorkerResponse[] = []
	worker.onmessage = (event) => {
		responses.push(event.data)
	}
	return responses
}

function getCustomEventResponses(
	responses: HighlightClientWorkerResponse[],
): CustomEventResponse[] {
	return responses
		.filter((r) => r.response?.type === MessageType.CustomEvent)
		.map((r) => r.response as CustomEventResponse)
}

function createInitializeMessage(
	overrides: Partial<InitializeMessage> = {},
): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Initialize,
			backend: 'https://test.highlight.io/graphql',
			sessionSecureID: 'test-session-123',
			debug: false,
			recordingStartTime: Date.now(),
			...overrides,
		},
	}
}

function createIdentifyMessage(
	overrides: Partial<IdentifyMessage> = {},
): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Identify,
			userIdentifier: 'user-123',
			userObject: { name: 'Test User', email: 'test@example.com' },
			...overrides,
		},
	}
}

function createPropertiesMessage(
	overrides: Partial<PropertiesMessage> = {},
): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Properties,
			propertiesObject: { page: '/home', action: 'click' },
			propertyType: { type: 'track' },
			...overrides,
		},
	}
}

function createResetMessage(): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Reset,
		} as ResetMessage,
	}
}

function createGetStatusMessage(): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.GetStatus,
		},
	}
}

/**
 * Sends a GetStatus message and waits for the response.
 * This is the preferred way to check worker state because GetStatus
 * is processed synchronously, so when we receive the response we know
 * all messages sent before GetStatus have been handled.
 */
async function waitForStatus(
	worker: TestWorker,
	responses: HighlightClientWorkerResponse[],
): Promise<StatusResponse> {
	const countBefore = responses.filter(
		(r) => r.response?.type === MessageType.GetStatus,
	).length

	worker.postMessage(createGetStatusMessage())

	await vi.waitFor(() => {
		const statusResponses = responses.filter(
			(r) => r.response?.type === MessageType.GetStatus,
		)
		expect(statusResponses.length).toBeGreaterThan(countBefore)
	})

	// Return the latest status response
	const statusResponses = responses.filter(
		(r) => r.response?.type === MessageType.GetStatus,
	)
	return statusResponses[statusResponses.length - 1]
		.response as StatusResponse
}

describe('highlight-client-worker', () => {
	let worker: TestWorker

	beforeEach(async () => {
		vi.resetModules()

		const { default: HighlightClientWorker } =
			await import('./highlight-client-worker?worker&inline')
		worker = new HighlightClientWorker() as unknown as TestWorker
	})

	afterEach(() => {
		worker.terminate()
	})

	describe('message queueing before initialization', () => {
		it('does not post CustomEvent responses before Initialize', async () => {
			const responses = createResponseCollector(worker)

			worker.postMessage(createIdentifyMessage())
			worker.postMessage(createPropertiesMessage())

			// Use GetStatus to verify messages are queued (not processed)
			const status = await waitForStatus(worker, responses)
			expect(status.pendingCount).toBe(2)
			expect(status.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)
		})

		it('queues multiple message types before initialization without responses', async () => {
			const responses = createResponseCollector(worker)

			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'user-1' }),
			)
			worker.postMessage(
				createPropertiesMessage({
					propertiesObject: { key: 'value1' },
				}),
			)
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'user-2' }),
			)

			// Use GetStatus to verify all 3 messages are queued
			const status = await waitForStatus(worker, responses)
			expect(status.pendingCount).toBe(3)
			expect(status.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)
		})
	})

	describe('Initialize drains pending messages', () => {
		it('posts CustomEvent responses for queued messages after Initialize', async () => {
			const responses = createResponseCollector(worker)

			// Queue an Identify message before initialization
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'queued-user' }),
			)

			// Verify message is queued (not processed)
			const statusBefore = await waitForStatus(worker, responses)
			expect(statusBefore.pendingCount).toBe(1)
			expect(statusBefore.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)

			// Now initialize the worker
			worker.postMessage(createInitializeMessage())

			// Wait for the queued message to be processed
			await vi.waitFor(() => {
				const event = getCustomEventResponses(responses).find(
					(e) => e.tag === 'Identify',
				)
				expect(event).toBeDefined()
				expect(event?.payload).toContain('queued-user')
			})
		})

		it('processes multiple queued messages in order after Initialize', async () => {
			const responses = createResponseCollector(worker)

			// Queue multiple Identify messages
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'first-user' }),
			)
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'second-user' }),
			)

			// Verify messages are queued (not processed)
			const statusBefore = await waitForStatus(worker, responses)
			expect(statusBefore.pendingCount).toBe(2)
			expect(statusBefore.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)

			// Initialize
			worker.postMessage(createInitializeMessage())

			// Wait for first user to be processed
			await vi.waitFor(() => {
				const event = getCustomEventResponses(responses).find(
					(e) =>
						e.tag === 'Identify' &&
						e.payload?.includes('first-user'),
				)
				expect(event).toBeDefined()
			})
		})

		it('processes new messages immediately after initialization', async () => {
			const responses = createResponseCollector(worker)

			// Initialize first
			worker.postMessage(createInitializeMessage())

			// Send an Identify message - should be processed immediately
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'new-user' }),
			)

			// Wait for the message to be processed
			await vi.waitFor(() => {
				const event = getCustomEventResponses(responses).find((e) =>
					e.payload?.includes('new-user'),
				)
				expect(event).toBeDefined()
			})
		})
	})

	describe('Reset clears pending messages', () => {
		it('clears pending messages when Reset is received', async () => {
			const responses = createResponseCollector(worker)

			// Queue messages
			worker.postMessage(
				createIdentifyMessage({
					userIdentifier: 'should-be-cleared',
				}),
			)

			// Send Reset - this should clear the pending messages
			worker.postMessage(createResetMessage())

			// Now initialize
			worker.postMessage(createInitializeMessage())

			// Verify the cleared message was not processed
			await vi.waitFor(() => {
				const clearedEvent = getCustomEventResponses(responses).find(
					(e) => e.payload?.includes('should-be-cleared'),
				)
				expect(clearedEvent).toBeUndefined()
			})
		})

		it('processes messages sent after Reset', async () => {
			const responses = createResponseCollector(worker)

			// Queue a message that will be cleared
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'before-reset' }),
			)

			// Reset
			worker.postMessage(createResetMessage())

			// Queue another message after reset
			worker.postMessage(
				createIdentifyMessage({ userIdentifier: 'after-reset' }),
			)

			// Initialize
			worker.postMessage(createInitializeMessage())

			// Wait for after-reset message to be processed
			await vi.waitFor(() => {
				const afterResetEvent = getCustomEventResponses(responses).find(
					(e) => e.payload?.includes('after-reset'),
				)
				expect(afterResetEvent).toBeDefined()
			})

			// Verify before-reset message was NOT processed
			const beforeResetEvent = getCustomEventResponses(responses).find(
				(e) => e.payload?.includes('before-reset'),
			)
			expect(beforeResetEvent).toBeUndefined()
		})

		it('queues messages sent after Reset but before new Initialize', async () => {
			const responses = createResponseCollector(worker)

			// Initialize with first session
			worker.postMessage(
				createInitializeMessage({
					sessionSecureID: 'old-session-id',
				}),
			)

			// Verify worker is initialized
			const statusInitial = await waitForStatus(worker, responses)
			expect(statusInitial.initialized).toBe(true)

			// Reset the worker (simulates forceNew/sessionKey change)
			worker.postMessage(createResetMessage())

			// Send a message BEFORE the new Initialize arrives
			// This message should be QUEUED, not processed with old session ID
			worker.postMessage(
				createIdentifyMessage({
					userIdentifier: 'message-between-reset-and-init',
				}),
			)

			// Verify the message is queued (worker should NOT be initialized after Reset)
			const statusAfterReset = await waitForStatus(worker, responses)
			expect(statusAfterReset.initialized).toBe(false)
			expect(statusAfterReset.pendingCount).toBe(1)

			// Verify no CustomEvent was generated for this message yet
			const eventBeforeInit = getCustomEventResponses(responses).find(
				(e) => e.payload?.includes('message-between-reset-and-init'),
			)
			expect(eventBeforeInit).toBeUndefined()

			// Now send the new Initialize
			worker.postMessage(
				createInitializeMessage({
					sessionSecureID: 'new-session-id',
				}),
			)

			// Wait for the queued message to be processed
			await vi.waitFor(() => {
				const event = getCustomEventResponses(responses).find((e) =>
					e.payload?.includes('message-between-reset-and-init'),
				)
				expect(event).toBeDefined()
			})
		})
	})

	describe('Track properties behavior', () => {
		it('creates Track CustomEvent for track-type properties', async () => {
			const responses = createResponseCollector(worker)

			// Initialize
			worker.postMessage(createInitializeMessage())

			// Send a track properties message
			worker.postMessage(
				createPropertiesMessage({
					propertiesObject: { action: 'button_click', page: '/home' },
					propertyType: { type: 'track' },
				}),
			)

			// Wait for Track event
			await vi.waitFor(() => {
				const trackEvent = getCustomEventResponses(responses).find(
					(e) => e.tag === 'Track',
				)
				expect(trackEvent).toBeDefined()
				expect(trackEvent?.payload).toContain('button_click')
			})
		})
	})

	describe('worker response for Segment source', () => {
		it('creates Segment Identify CustomEvent for segment source', async () => {
			const responses = createResponseCollector(worker)

			// Initialize
			worker.postMessage(createInitializeMessage())

			// Send Identify with segment source
			worker.postMessage({
				message: {
					type: MessageType.Identify,
					userIdentifier: 'segment-user',
					userObject: { plan: 'premium' },
					source: 'segment',
				},
			})

			// Wait for Segment Identify event
			await vi.waitFor(() => {
				const segmentEvent = getCustomEventResponses(responses).find(
					(e) => e.tag === 'Segment Identify',
				)
				expect(segmentEvent).toBeDefined()
				expect(segmentEvent?.payload).toContain('segment-user')
			})
		})
	})

	describe('Initialize message handling', () => {
		it('allows processing after Initialize with valid sessionSecureID', async () => {
			const responses = createResponseCollector(worker)

			// Initialize with valid session
			worker.postMessage(
				createInitializeMessage({
					sessionSecureID: 'valid-session-id',
					recordingStartTime: Date.now(),
				}),
			)

			// Send a message - should be processed
			worker.postMessage(createIdentifyMessage())

			// Wait for response
			await vi.waitFor(() => {
				expect(
					getCustomEventResponses(responses).length,
				).toBeGreaterThan(0)
			})
		})

		it('does not process messages if sessionSecureID is empty', async () => {
			const responses = createResponseCollector(worker)

			// Initialize with empty session ID - shouldSendRequest() will return false
			worker.postMessage(
				createInitializeMessage({
					sessionSecureID: '',
				}),
			)

			// Send a message - should be queued, not processed
			worker.postMessage(createIdentifyMessage())

			// Verify message is queued and worker is not initialized
			const status = await waitForStatus(worker, responses)
			expect(status.pendingCount).toBe(1)
			expect(status.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)
		})

		it('does not process messages if recordingStartTime is 0', async () => {
			const responses = createResponseCollector(worker)

			// Initialize with recordingStartTime = 0 - shouldSendRequest() returns false
			worker.postMessage(
				createInitializeMessage({
					recordingStartTime: 0,
				}),
			)

			// Send a message - should be queued, not processed
			worker.postMessage(createIdentifyMessage())

			// Verify message is queued and worker is not initialized
			const status = await waitForStatus(worker, responses)
			expect(status.pendingCount).toBe(1)
			expect(status.initialized).toBe(false)
			expect(getCustomEventResponses(responses)).toHaveLength(0)
		})
	})
})
