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

export const shouldNetworkRequestBeTraced = (
	url: string,
	tracingOrigins: boolean | (string | RegExp)[],
	urlBlocklist: string[],
): boolean => {
	if (
		urlBlocklist.some((blockedUrl) =>
			url.toLowerCase().includes(blockedUrl),
		)
	) {
		return false
	}

	let patterns: (string | RegExp)[] = []
	if (tracingOrigins === true) {
		patterns = [
			'localhost',
			/^\//,
			/^http:\/\/localhost/,
			/^https:\/\/localhost/,
		]
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
