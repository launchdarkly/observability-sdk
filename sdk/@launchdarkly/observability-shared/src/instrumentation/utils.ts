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
 * Returns true if the name is a Highlight network resource.
 * This is used to filter out Highlight requests/responses from showing up on end application's network resources.
 */
const isHighlightNetworkResourceFilter = (
	name: string,
	highlightEndpoints: string[],
) =>
	highlightEndpoints.some((backendUrl) =>
		name.toLocaleLowerCase().includes(backendUrl),
	)

// Determines whether we store the network request and show it in the session
// replay, including the body and headers.
export const shouldNetworkRequestBeRecorded = (
	url: string,
	highlightEndpoints: string[],
	_tracingOrigins?: boolean | (string | RegExp)[],
) => {
	return (
		!isHighlightNetworkResourceFilter(url, highlightEndpoints) &&
		!isLaunchDarklyEvalNetworkResource(url) &&
		!isLaunchDarklyEventsNetworkResource(url)
	)
}

// Determines whether we want to attach the x-highlight-request header to the
// request. We want to avoid adding this to external requests.
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
	let patterns: (string | RegExp)[] = []
	if (tracingOrigins === true) {
		patterns = ['localhost', /^\//]
		if (window?.location?.host) {
			patterns.push(window.location.host)
		}
	} else if (tracingOrigins instanceof Array) {
		patterns = tracingOrigins
	}

	let result = false
	patterns.forEach((pattern) => {
		if (url.match(pattern)) {
			result = true
		}
	})
	return result
}

export const shouldRecordRequest = (
	url: string,
	highlightEndpoints: string[],
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
		highlightEndpoints,
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
