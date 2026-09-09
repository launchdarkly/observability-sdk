import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ClientError } from 'graphql-request'
import {
	getGraphQLRequestWrapper,
	MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS,
} from './graph'

function clientError(status: number): ClientError {
	return new ClientError({ status } as unknown as ClientError['response'], {
		query: 'mutation pushPayload { pushPayload }',
	})
}

/** Runs the wrapper to completion, letting every backoff timer fire immediately. */
async function withoutBackoff<T>(request: Promise<T>): Promise<T> {
	const settled = request.then(
		(value) => ({ value }),
		(error) => ({ error }),
	)
	await vi.runAllTimersAsync()
	const outcome = await settled
	if ('error' in outcome) {
		throw outcome.error
	}
	return outcome.value
}

describe('getGraphQLRequestWrapper', () => {
	beforeEach(() => {
		vi.useFakeTimers()
		vi.spyOn(console, 'error').mockImplementation(() => {})
	})

	afterEach(() => {
		vi.useRealTimers()
		vi.restoreAllMocks()
	})

	it('does not retry a request that succeeds', async () => {
		const requestFn = vi.fn().mockResolvedValue('ok')

		await expect(
			withoutBackoff(
				getGraphQLRequestWrapper()(requestFn, 'pushPayload'),
			),
		).resolves.toBe('ok')
		expect(requestFn).toHaveBeenCalledTimes(1)
	})

	it('retries a recoverable failure until it succeeds', async () => {
		const requestFn = vi
			.fn()
			.mockRejectedValueOnce(clientError(503))
			.mockRejectedValueOnce(new Error('Failed to fetch'))
			.mockResolvedValue('ok')

		await expect(
			withoutBackoff(
				getGraphQLRequestWrapper()(requestFn, 'pushPayload'),
			),
		).resolves.toBe('ok')
		expect(requestFn).toHaveBeenCalledTimes(3)
	})

	it('gives up on a recoverable failure after the retry budget', async () => {
		const error = clientError(503)
		const requestFn = vi.fn().mockRejectedValue(error)

		await expect(
			withoutBackoff(
				getGraphQLRequestWrapper()(requestFn, 'pushPayload'),
			),
		).rejects.toBe(error)
		expect(requestFn).toHaveBeenCalledTimes(
			MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS + 1,
		)
	})

	it('throws an unrecoverable failure without retrying', async () => {
		const error = clientError(403)
		const requestFn = vi.fn().mockRejectedValue(error)

		await expect(
			withoutBackoff(
				getGraphQLRequestWrapper()(requestFn, 'pushPayload'),
			),
		).rejects.toBe(error)
		expect(requestFn).toHaveBeenCalledTimes(1)
	})
})
