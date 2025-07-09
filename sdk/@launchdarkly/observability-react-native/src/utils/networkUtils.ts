export type PropagateTraceHeaderCorsUrls = (string | RegExp)[]

export const getCorsUrlsPattern = (
	tracingOrigins?: boolean | (string | RegExp)[],
): PropagateTraceHeaderCorsUrls => {
	if (tracingOrigins === true) {
		return [
			/localhost/,
			/^\//,
			/^http:\/\/localhost/,
			/^https:\/\/localhost/,
		]
	} else if (Array.isArray(tracingOrigins)) {
		return tracingOrigins.map((pattern) =>
			typeof pattern === 'string' ? new RegExp(pattern) : pattern,
		)
	}

	return [/^$/]
}

export const getIgnoreUrlsPattern = (
	tracingOrigins?: boolean | (string | RegExp)[],
): Array<string | RegExp> => {
	if (tracingOrigins === false || tracingOrigins === undefined) {
		return [/.*/] // Ignore all URLs if tracingOrigins is disabled
	}
	
	if (tracingOrigins === true) {
		return [/^(?!.*localhost)(?!\/)(?!http:\/\/localhost)(?!https:\/\/localhost).*$/]
	}
	
	if (Array.isArray(tracingOrigins)) {
		if (tracingOrigins.length === 0) {
			return [/.*/]
		}
		
		// Create a negative lookahead pattern that ignores URLs that don't match any of the tracingOrigins
		const patterns = tracingOrigins.map((pattern) =>
			typeof pattern === 'string' ? pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') : pattern.source
		)
		const combinedPattern = `^(?!.*(${patterns.join('|')})).*$`
		return [new RegExp(combinedPattern)]
	}
	
	return [/.*/] // Default to ignoring all URLs
}
