import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ClientError } from 'graphql-request'
import { MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS } from './constants'
import {
	CustomEventResponse,
	HighlightClientWorkerParams,
	HighlightClientWorkerResponse,
	MessageType,
	StatusResponse,
	StopEventResponse,
	StopReason,
} from './types'

// Every operation the worker performs rejects with `error`, so a single knob decides whether the
// worker sees a recoverable or an unrecoverable failure. Setting `hang` holds the next operation
// open instead, and `answerInFlight` is how the test settles it later.
const graph = vi.hoisted(() => ({
	error: undefined as unknown,
	hang: false,
	answerInFlight: undefined as
		| { succeed: () => void; fail: (e: unknown) => void }
		| undefined,
}))

vi.mock('../graph/generated/operations', async (importOriginal) => {
	const actual =
		await importOriginal<typeof import('../graph/generated/operations')>()
	const reject = (): Promise<any> => {
		if (graph.hang) {
			graph.hang = false
			return new Promise<any>((resolve, rejectInFlight) => {
				graph.answerInFlight = {
					succeed: () => resolve({}),
					fail: rejectInFlight,
				}
			})
		}
		return Promise.reject(graph.error)
	}
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

function asyncEventsMessage(id: number): HighlightClientWorkerParams {
	return {
		message: {
			type: MessageType.AsyncEvents,
			id,
			hasSessionUnloaded: false,
			highlightLogs: '',
			events: [],
			messages: [],
			errors: [],
			resourcesString: '[]',
			webSocketEventsString: '[]',
		},
	}
}

/**
 * A worker that ignores a failure says nothing about it, so telling that apart from one still on
 * its way means giving the worker time to answer.
 */
function settle(): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, 300))
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
		graph.hang = false
		graph.answerInFlight = undefined
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

	it('does not report the stop as a timeline event', async () => {
		graph.error = clientError(403)

		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('refused-user'))

		await vi.waitFor(() => {
			expect(
				responsesOfType<StopEventResponse>(MessageType.Stop),
			).toHaveLength(1)
		})

		// The client is NotRecording by the time it handles this, so a Track event could never be
		// captured; it would only leave `addCustomEvent` polling every 500ms for the rest of the
		// page load.
		expect(
			responsesOfType<CustomEventResponse>(MessageType.CustomEvent).map(
				(e) => e.tag,
			),
		).not.toContain('Track')
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

	it('stops recording once the retry budget is spent', async () => {
		graph.error = clientError(503)

		worker.postMessage(initializeMessage())
		for (let i = 0; i < MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS; i++) {
			worker.postMessage(identifyMessage(`retried-user-${i}`))
		}

		await vi.waitFor(() => {
			const stops = responsesOfType<StopEventResponse>(MessageType.Stop)
			expect(stops).toHaveLength(1)
			expect(stops[0].reason).toBe(StopReason.RetriesExhausted)
		})

		worker.postMessage(identifyMessage('dropped-user'))

		const { pendingCount, initialized } = await status()
		expect(pendingCount).toBe(0)
		expect(initialized).toBe(false)
	})

	it.each([
		['initializes again', [initializeMessage()]],
		[
			'is reset for a new session',
			[
				{ message: { type: MessageType.Reset } },
				initializeMessage(),
			] as HighlightClientWorkerParams[],
		],
	] as [string, HighlightClientWorkerParams[]][])(
		'ignores a refusal owed to a session that ended before the client %s',
		async (_name, revival) => {
			graph.error = clientError(403)

			// A request the backend never answers, so it is still open when the stop below lands.
			graph.hang = true
			worker.postMessage(initializeMessage())
			worker.postMessage(identifyMessage('slow-user'))
			await vi.waitFor(() => {
				expect(graph.answerInFlight).toBeDefined()
			})

			worker.postMessage(identifyMessage('refused-user'))
			await vi.waitFor(() => {
				expect(
					responsesOfType<StopEventResponse>(MessageType.Stop),
				).toHaveLength(1)
			})

			revival.forEach((message) => worker.postMessage(message))
			expect((await status()).initialized).toBe(true)

			graph.answerInFlight!.fail(clientError(403))
			await settle()

			expect((await status()).initialized).toBe(true)
			expect(
				responsesOfType<StopEventResponse>(MessageType.Stop),
			).toHaveLength(1)
		},
	)

	it('does not restore the retry budget of a new session with a stale success', async () => {
		// A request the backend answers only after the session that sent it has been replaced.
		graph.hang = true
		worker.postMessage(initializeMessage())
		worker.postMessage(identifyMessage('slow-user'))
		await vi.waitFor(() => {
			expect(graph.answerInFlight).toBeDefined()
		})

		worker.postMessage(initializeMessage())

		// One short of the budget, so what the stale answer does to the count is what decides
		// whether the next failure stops recording.
		graph.error = clientError(503)
		for (let i = 0; i < MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS - 1; i++) {
			worker.postMessage(identifyMessage(`retried-user-${i}`))
		}
		await settle()

		graph.answerInFlight!.succeed()
		await settle()

		worker.postMessage(identifyMessage('last-straw'))
		await vi.waitFor(() => {
			const stops = responsesOfType<StopEventResponse>(MessageType.Stop)
			expect(stops).toHaveLength(1)
			expect(stops[0].reason).toBe(StopReason.RetriesExhausted)
		})
	})

	it('does not report a payload uploaded for a session that has ended', async () => {
		// The client counts the bytes of every payload the worker reports towards its next full
		// snapshot, and this one was recorded by a session it is no longer replaying.
		graph.hang = true
		worker.postMessage(initializeMessage())
		worker.postMessage(asyncEventsMessage(1))
		await vi.waitFor(() => {
			expect(graph.answerInFlight).toBeDefined()
		})

		worker.postMessage(initializeMessage())
		graph.answerInFlight!.succeed()
		await settle()

		expect(responsesOfType(MessageType.AsyncEvents)).toHaveLength(0)
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
