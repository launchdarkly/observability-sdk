import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ClientError } from 'graphql-request'
import {
	CustomEventResponse,
	HighlightClientWorkerParams,
	HighlightClientWorkerResponse,
	MessageType,
	StatusResponse,
	StopEventResponse,
	StopReason,
} from './types'

// Every operation the worker performs rejects with this, so a single knob decides whether the
// worker sees a recoverable or an unrecoverable failure.
const graph = vi.hoisted(() => ({ error: undefined as unknown }))

vi.mock('../graph/generated/operations', async (importOriginal) => {
	const actual =
		await importOriginal<typeof import('../graph/generated/operations')>()
	const reject = () => Promise.reject(graph.error)
	return {
		...actual,
		getSdk: () => ({
			identifySession: reject,
			addSessionProperties: reject,
			PushSessionEvents: reject,
			pushMetrics: reject,
		}),
	}
})

interface TestWorker {
	postMessage: (params: HighlightClientWorkerParams) => void
	onmessage:
		| ((event: MessageEvent<HighlightClientWorkerResponse>) => void)
		| null
	terminate: () => void
}

function clientError(status: number): ClientError {
	return new ClientError({ status } as unknown as ClientError['response'], {
		query: 'mutation identifySession { identifySession }',
	})
}

function initializeMessage(): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Initialize,
			backend: 'https://test.highlight.io/graphql',
			sessionSecureID: 'test-session-123',
			debug: false,
			recordingStartTime: Date.now(),
		},
	}
}

function identifyMessage(userIdentifier: string): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.Identify,
			userIdentifier,
			userObject: { name: 'Test User' },
		},
	}
}

describe('highlight-client-worker error handling', () => {
	let worker: TestWorker
	let responses: HighlightClientWorkerResponse[]

	const responsesOfType = <T>(type: MessageType): T[] =>
		responses
			.filter((r) => r.response?.type === type)
			.map((r) => r.response as T)

	const status = async (): Promise<StatusResponse> => {
		const before = responsesOfType<StatusResponse>(
			MessageType.GetStatus,
		).length
		worker.postMessage({ message: { type: MessageType.GetStatus } })
		await vi.waitFor(() => {
			expect(
				responsesOfType<StatusResponse>(MessageType.GetStatus).length,
			).toBeGreaterThan(before)
		})
		const all = responsesOfType<StatusResponse>(MessageType.GetStatus)
		return all[all.length - 1]
	}

	beforeEach(() => {
		vi.resetModules()
		vi.spyOn(console, 'warn').mockImplementation(() => {})
		// See highlight-client-worker.test.ts: the source module (rather than the `?worker&inline`
		// build output) is what @vitest/web-worker can run for real.
		worker = new Worker(
			new URL('./highlight-client-worker.ts', import.meta.url),
			{ type: 'module' },
		) as unknown as TestWorker
		responses = []
		worker.onmessage = (event) => {
			responses.push(event.data)
		}
	})

	afterEach(() => {
		worker.terminate()
		vi.restoreAllMocks()
	})

	it('stops recording when the backend rejects a request permanently', async () => {
		graph.error = clientError(403)

		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('refused-user'))

		await vi.waitFor(() => {
			const stops = responsesOfType<StopEventResponse>(MessageType.Stop)
			expect(stops).toHaveLength(1)
			expect(stops[0].reason).toBe(StopReason.UnrecoverableError)
		})

		expect((await status()).initialized).toBe(false)
	})

	it('drops later messages after a permanent rejection', async () => {
		graph.error = clientError(403)

		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('refused-user'))

		await vi.waitFor(() => {
			expect(
				responsesOfType<StopEventResponse>(MessageType.Stop),
			).toHaveLength(1)
		})

		worker.postMessage(identifyMessage('dropped-user'))

		const { pendingCount } = await status()
		expect(pendingCount).toBe(0)
		expect(
			responsesOfType<CustomEventResponse>(MessageType.CustomEvent).some(
				(e) => e.payload?.includes('dropped-user'),
			),
		).toBe(false)
	})

	it('keeps recording when the failure may resolve on its own', async () => {
		graph.error = clientError(503)

		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('retried-user'))

		await vi.waitFor(() => {
			expect(
				responsesOfType<CustomEventResponse>(
					MessageType.CustomEvent,
				).some((e) => e.payload?.includes('retried-user')),
			).toBe(true)
		})

		expect(responsesOfType<StopEventResponse>(MessageType.Stop)).toEqual([])
		expect((await status()).initialized).toBe(true)
	})

	it('resumes after a new session initializes', async () => {
		graph.error = clientError(403)

		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('refused-user'))

		await vi.waitFor(() => {
			expect(
				responsesOfType<StopEventResponse>(MessageType.Stop),
			).toHaveLength(1)
		})

		worker.postMessage(initializeMessage())

		expect((await status()).initialized).toBe(true)
	})
})
