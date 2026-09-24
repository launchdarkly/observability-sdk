import { isErrorRecoverable } from './error-recoverability'

export const MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS = 3

// Initial backoff for retrying graphql requests.
export const BASE_DELAY_MS = 1000
export const BACKOFF_DELAY_MS = 500

export const getGraphQLRequestWrapper = () => {
	const graphQLRequestWrapper = async <T>(
		requestFn: () => Promise<T>,
		operationName: string,
		operationType?: string,
		variables?: any,
		retries: number = 0,
	): Promise<T> => {
		try {
			return await requestFn()
		} catch (error: any) {
			// Retrying an unrecoverable failure only delays the caller and adds load the backend
			// already rejected.
			if (!isErrorRecoverable(error)) {
				throw error
			}

			if (retries < MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS) {
				await new Promise((resolve) =>
					setTimeout(
						resolve,
						BASE_DELAY_MS + BACKOFF_DELAY_MS * Math.pow(2, retries),
					),
				)
				return await graphQLRequestWrapper(
					requestFn,
					operationName,
					operationType,
					variables,
					retries + 1,
				)
			}
			console.error(
				`highlight.io: data request failed after ${retries} retries`,
			)
			throw error
		}
	}
	return graphQLRequestWrapper
}
