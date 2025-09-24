import { parse } from 'graphql'
import { isLDContextUrl } from '../launchdarkly/urlFilters'
import { TracingOrigins } from './types'

const isLaunchDarklyEventsNetworkResource = (name: string) => {
	return ['events.ld.catamorphic.com', 'events.launchdarkly.com'].some(
		(backendUrl) => name.toLocaleLowerCase().includes(backendUrl),
	)
}

const isLaunchDarklyEvalNetworkResource = (name: string) => {
	return isLDContextUrl(name)
}

/**
 * Returns true if the name is a internal network resource.
 * This is used to filter out internal requests/responses from showing up on end
 * application's network resources.
 */
const isInternalNetworkResourceFilter = (
	name: string,
	internalEndpoints: string[],
) =>
	internalEndpoints.some((backendUrl) =>
		name.toLocaleLowerCase().includes(backendUrl),
	)

// Determines whether we store the network request and show it in the session
// replay, including the body and headers.
export const shouldNetworkRequestBeRecorded = (
	url: string,
	internalEndpoints: string[],
	_tracingOrigins?: boolean | (string | RegExp)[],
) => {
	return (
		!isInternalNetworkResourceFilter(url, internalEndpoints) &&
		!isLaunchDarklyEvalNetworkResource(url) &&
		!isLaunchDarklyEventsNetworkResource(url)
	)
}

// Determines whether we want to assign trace propagation headers to a request.
export const shouldNetworkRequestBeTraced = (
	url: string,
	tracingOrigins: boolean | (string | RegExp)[],
	urlBlocklist: string[],
) => {
	if (
		urlBlocklist.some((blockedUrl) =>
			url.toLowerCase().includes(blockedUrl),
		)
	) {
		return false
	}

	const patterns = getCorsUrlsPattern(tracingOrigins)
	if (Array.isArray(patterns)) {
		return patterns.some((pattern) => pattern.test(url))
	}

	return patterns.test(url)
}

export const shouldRecordRequest = (
	url: string,
	internalEndpoints: string[],
	tracingOrigins: TracingOrigins,
	urlBlocklist: string[],
) => {
	const shouldRecordHeaderAndBody = !urlBlocklist?.some((blockedUrl) =>
		url.toLowerCase().includes(blockedUrl),
	)
	if (!shouldRecordHeaderAndBody) {
		return false
	}

	return shouldNetworkRequestBeRecorded(
		url,
		internalEndpoints,
		tracingOrigins,
	)
}

// Helper to convert tracingOrigins to a RegExp or array of RegExp
export function getCorsUrlsPattern(
	tracingOrigins: TracingOrigins,
): RegExp | RegExp[] {
	if (tracingOrigins === true) {
		const patterns = [/localhost/, /^\//]
		if (typeof window !== 'undefined' && window.location?.host) {
			patterns.push(new RegExp(window.location.host))
		}
		return patterns
	} else if (Array.isArray(tracingOrigins)) {
		return tracingOrigins.map((pattern) =>
			typeof pattern === 'string' ? new RegExp(pattern) : pattern,
		)
	}

	return /^$/ // Match nothing if tracingOrigins is false or undefined
}

export const getSpanName = (
	url: string,
	method: string,
	body?:
		| Request['body']
		| RequestInit['body']
		| XMLHttpRequest['responseText'],
) => {
	const urlObject = new URL(url)
	const pathname = urlObject.pathname
	const host = urlObject.host

	// Extract meaningful operation name from GraphQL requests
	if (body) {
		try {
			const parsedBody =
				typeof body === 'string' ? JSON.parse(body) : body

			if (parsedBody && parsedBody.query) {
				// If operation name is provided in the body, use it
				if (parsedBody.operationName) {
					return `${parsedBody.operationName} (GraphQL: ${host}${pathname})`
				}

				// Try to parse the query to extract operation name and type
				const query = parse(parsedBody.query)
				const operation = query.definitions[0]

				if (operation?.kind === 'OperationDefinition') {
					const operationType = operation.operation // 'query', 'mutation', 'subscription'
					const operationName = operation.name?.value

					if (operationName) {
						return `${operationName} (GraphQL ${operationType}: ${host}${pathname})`
					} else {
						// Anonymous operation but we know the type
						return `anonymous (GraphQL ${operationType}: ${host}${pathname})`
					}
				}

				// Fallback for cases where parsing fails
				return `GraphQL (${host}${pathname})`
			}
		} catch {
			// Ignore errors from JSON parsing
		}
	}

	// Improve REST API naming
	const pathSegments = pathname.split('/').filter(Boolean)
	let spanName = `${method.toUpperCase()} ${pathname}`

	if (pathSegments.length > 0) {
		// Try to create a more meaningful name for REST APIs
		const lastSegment = pathSegments[pathSegments.length - 1]

		// Check if the last segment looks like an ID (number, UUID, etc.)
		if (
			/^\d+$/.test(lastSegment) || // Pure numbers
			/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
				lastSegment,
			) || // UUIDs
			/^[0-9a-f]{8,}$/i.test(lastSegment) // Long hex strings
		) {
			// Replace ID with a placeholder but keep it for context
			const resourcePath = pathSegments.slice(0, -1).join('/')
			const resourceName =
				pathSegments[pathSegments.length - 2] || 'resource'
			spanName = `${method.toUpperCase()} /${resourcePath}/:${resourceName}Id (${host})`
		} else {
			// Use the actual path with host
			spanName = `${method.toUpperCase()} ${pathname} (${host})`
		}

		// Add query params if they might be meaningful
		if (urlObject.search && urlObject.search.length > 1) {
			const params = urlObject.searchParams
			const paramCount = Array.from(params.keys()).length
			if (paramCount > 0) {
				spanName += ` [${paramCount} param${paramCount > 1 ? 's' : ''}]`
			}
		}
	} else {
		// Root path
		spanName = `${method.toUpperCase()} / (${host})`
	}

	return spanName
}
