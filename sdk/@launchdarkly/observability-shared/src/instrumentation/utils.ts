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

	// Match against the URL's origin rather than the full URL. Otherwise the
	// configured host can appear as a query-parameter value in a third-party
	// URL and match the substring regex, leaking trace headers to origins the
	// user never intended to trace.
	const matchTarget = getUrlMatchTarget(url)

	const patterns = getCorsUrlsPattern(tracingOrigins)
	if (Array.isArray(patterns)) {
		return patterns.some((pattern) => pattern.test(matchTarget))
	}

	return patterns.test(matchTarget)
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

const escapeRegExp = (s: string): string =>
	s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// Helper to convert tracingOrigins to a RegExp or array of RegExp
export function getCorsUrlsPattern(
	tracingOrigins: TracingOrigins,
): RegExp | RegExp[] {
	if (tracingOrigins === true) {
		const patterns = [/localhost/, /^\//]
		if (typeof window !== 'undefined' && window.location?.host) {
			patterns.push(buildOriginHostPattern(window.location.host))
		}
		return patterns
	} else if (Array.isArray(tracingOrigins)) {
		// Per the documented contract, string entries are hosts while RegExp
		// entries are used as-is. Anchor string entries to the origin position
		// (host + optional subdomains) rather than matching them as a bare
		// substring. This matters because these patterns are also handed to the
		// instrumentations' `propagateTraceHeaderCorsUrls`, which tests them
		// against the *full* request URL — an unanchored `api.example.com` would
		// otherwise match a third-party URL that merely carries the host as a
		// query-parameter value (e.g. `https://evil.test/?x=api.example.com`),
		// leaking trace and baggage headers to unintended origins.
		return tracingOrigins.map((pattern) =>
			typeof pattern === 'string'
				? buildOriginHostPattern(pattern)
				: pattern,
		)
	}

	return /^$/ // Match nothing if tracingOrigins is false or undefined
}

// Build a regex that matches a host (and its subdomains) at the origin position
// of a URL, not anywhere inside it. Anchoring matters: an unanchored
// /example.com/ also matches https://third-party.io/?store=example.com.
const buildOriginHostPattern = (host: string): RegExp =>
	new RegExp('^https?://([^/]+\\.)?' + escapeRegExp(host) + '([:/?#]|$)')

// Returns the URL's origin for absolute URLs, or the original string for
// relative paths or inputs that fail to parse.
const getUrlMatchTarget = (url: string): string => {
	// Keep relative paths verbatim so patterns like /^\// still match them.
	if (url.startsWith('/') && !url.startsWith('//')) {
		return url
	}
	try {
		return new URL(url).origin
	} catch {
		return url
	}
}
