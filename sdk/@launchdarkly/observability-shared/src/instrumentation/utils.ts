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

export const getHttpSpanName = (
	url: string,
	method: string,
	body: Request['body'] | RequestInit['body'],
) => {
	const urlObject = new URL(url)
	const pathname = urlObject.pathname

	// Extract meaningful operation name from GraphQL requests
	try {
		const parsedBody = typeof body === 'string' ? JSON.parse(body) : body

		if (parsedBody && parsedBody.query) {
			let operationName = 'unknown'
			// If operation name is provided in the body, use it
			if (parsedBody.operationName) {
				operationName = parsedBody.operationName
			} else {
				// Try to parse the query to extract operation name and type
				const query = parse(parsedBody.query)
				const operation = query.definitions[0]

				if (
					operation?.kind === 'OperationDefinition' &&
					operation.name?.value
				) {
					operationName = operation.name?.value
				}
			}

			// Fallback for cases where parsing fails
			return `${method.toUpperCase()} ${pathname} (${operationName})`
		}
	} catch {
		// Ignore errors from JSON parsing
	}

	// Improve REST API naming
	const pathSegments = pathname.split('/').filter(Boolean)

	if (pathSegments.length > 0) {
		// Process each segment and replace IDs with parameter placeholders
		const processedSegments = pathSegments.map((segment, index) => {
			// Check if the segment looks like an ID (number, UUID, etc.)
			if (
				/^\d+$/.test(segment) || // Pure numbers
				/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
					segment,
				) || // UUIDs
				/^[0-9a-f]{8,}$/i.test(segment) // Long hex strings
			) {
				// Get the previous segment to create a meaningful parameter name
				const previousSegment =
					index > 0 ? pathSegments[index - 1] : 'resource'
				return `:${previousSegment}Id`
			}
			return segment
		})

		return `${method.toUpperCase()} /${processedSegments.join('/')}`
	} else {
		// Root path
		return `${method.toUpperCase()} /`
	}
}
