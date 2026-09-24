import { ClientError } from 'graphql-request'
import { PublicGraphError } from '../graph/generated/schemas'

type GraphQLErrors = NonNullable<ClientError['response']['errors']>

// Public graph errors that are permanent whatever the transport reports.
const UNRECOVERABLE_ERRORS: string[] = [
	PublicGraphError.BillingQuotaExceeded.toString(),
]

/**
 * Tests whether an HTTP error status represents a condition that might resolve on its own if we
 * retry. 4xx statuses are permanent apart from the three that describe a transient condition;
 * anything else, including 5xx, is worth another attempt.
 */
export const isHttpErrorRecoverable = (statusCode: number): boolean => {
	if (statusCode < 400 || statusCode >= 500) {
		return true
	}

	switch (statusCode) {
		case 400: // bad request
		case 408: // request timeout
		case 429: // too many requests
			return true
		default:
			return false // all other 4xx errors are unrecoverable
	}
}

/**
 * Classifies a failed public graph request as recoverable (retrying may succeed) or unrecoverable
 * (permanent for this page load). Errors of unknown origin count as recoverable: retrying costs a
 * backed-off request, while a wrong permanent verdict silently disables recording.
 */
export const isErrorRecoverable = (error: unknown): boolean => {
	if (!(error instanceof ClientError)) {
		// Offline, DNS and aborted-fetch failures reject with the raw error and carry no
		// permanent signal.
		return true
	}

	const errors = error.response.errors
	if (errors?.some((e) => UNRECOVERABLE_ERRORS.includes(e.message))) {
		return false
	}

	// An explicit `retryable` is more specific than the status code, so it wins.
	const retryable = retryableFlag(errors)
	if (retryable !== undefined) {
		return retryable
	}

	if (error.response.status >= 400) {
		return isHttpErrorRecoverable(error.response.status)
	}

	// The public graph also reports a rejected request as `200` + `errors`. Classify those like a
	// generic 4xx: permanent unless the server marked an error retryable.
	return !errors?.length
}

/** The server's retry verdict for a set of GraphQL errors, or `undefined` when none states one. */
const retryableFlag = (
	errors: GraphQLErrors | undefined,
): boolean | undefined => {
	if (errors?.some((e) => e.extensions?.retryable === false)) {
		return false
	}
	if (errors?.some((e) => e.extensions?.retryable === true)) {
		return true
	}
	return undefined
}
