import { describe, expect, it } from 'vitest'
import { ClientError } from 'graphql-request'
import { PublicGraphError } from '../graph/generated/schemas'
import {
	isErrorRecoverable,
	isHttpErrorRecoverable,
} from './error-recoverability'

type TestGraphQLError = {
	message: string
	extensions?: { retryable?: boolean }
}

function clientError(status: number, errors?: TestGraphQLError[]): ClientError {
	return new ClientError(
		{ status, errors } as unknown as ClientError['response'],
		{ query: 'mutation pushPayload { pushPayload }' },
	)
}

describe('isHttpErrorRecoverable', () => {
	it('treats transient 4xx statuses as recoverable', () => {
		expect(isHttpErrorRecoverable(400)).toBe(true)
		expect(isHttpErrorRecoverable(408)).toBe(true)
		expect(isHttpErrorRecoverable(429)).toBe(true)
	})

	it('treats every other 4xx status as unrecoverable', () => {
		expect(isHttpErrorRecoverable(401)).toBe(false)
		expect(isHttpErrorRecoverable(402)).toBe(false)
		expect(isHttpErrorRecoverable(403)).toBe(false)
		expect(isHttpErrorRecoverable(404)).toBe(false)
		expect(isHttpErrorRecoverable(422)).toBe(false)
	})

	it('treats server errors and non-error statuses as recoverable', () => {
		expect(isHttpErrorRecoverable(500)).toBe(true)
		expect(isHttpErrorRecoverable(502)).toBe(true)
		expect(isHttpErrorRecoverable(503)).toBe(true)
		expect(isHttpErrorRecoverable(504)).toBe(true)
		expect(isHttpErrorRecoverable(200)).toBe(true)
		expect(isHttpErrorRecoverable(0)).toBe(true)
	})
})

describe('isErrorRecoverable', () => {
	it('treats errors of unknown origin as recoverable', () => {
		expect(isErrorRecoverable(new Error('Failed to fetch'))).toBe(true)
		expect(isErrorRecoverable(new TypeError('NetworkError'))).toBe(true)
		expect(isErrorRecoverable(undefined)).toBe(true)
	})

	it('classifies a rejected request by its status', () => {
		expect(isErrorRecoverable(clientError(403))).toBe(false)
		expect(isErrorRecoverable(clientError(404))).toBe(false)
		expect(isErrorRecoverable(clientError(429))).toBe(true)
		expect(isErrorRecoverable(clientError(503))).toBe(true)
	})

	it('treats a 200 carrying GraphQL errors as unrecoverable', () => {
		expect(
			isErrorRecoverable(clientError(200, [{ message: 'not allowed' }])),
		).toBe(false)
	})

	it('honors an explicit retryable flag over the status', () => {
		expect(
			isErrorRecoverable(
				clientError(403, [
					{ message: 'try again', extensions: { retryable: true } },
				]),
			),
		).toBe(true)
		expect(
			isErrorRecoverable(
				clientError(429, [
					{ message: 'give up', extensions: { retryable: false } },
				]),
			),
		).toBe(false)
		expect(
			isErrorRecoverable(
				clientError(200, [
					{ message: 'try again', extensions: { retryable: true } },
				]),
			),
		).toBe(true)
	})

	it('takes the most pessimistic retryable flag when errors disagree', () => {
		expect(
			isErrorRecoverable(
				clientError(200, [
					{ message: 'try again', extensions: { retryable: true } },
					{ message: 'give up', extensions: { retryable: false } },
				]),
			),
		).toBe(false)
	})

	it('ignores a retryable flag on a permanent public graph error', () => {
		expect(
			isErrorRecoverable(
				clientError(200, [
					{
						message: PublicGraphError.BillingQuotaExceeded,
						extensions: { retryable: true },
					},
				]),
			),
		).toBe(false)
	})
})
