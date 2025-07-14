// based on https://github.com/launchdarkly/js-core/blob/main/packages/telemetry/browser-telemetry/src/filters/defaultUrlFilter.ts
const pollingRegex = /sdk\/evalx\/[^/]+\/contexts\/(?<context>[^/?]*)\??.*?/
const streamingRegex = /\/eval\/[^/]+\/(?<context>[^/?]*)\??.*?/
/**
 * Returns true for LD evaluation URLs that should not be recorded.
 *
 * @param url URL to filter.
 * @returns true when the url is a sensitive url that should not be recorded.
 */
export function isLDContextUrl(url) {
	if (url.includes('/sdk/evalx')) {
		const regexMatch = url.match(pollingRegex)
		if (regexMatch) {
			return true
		}
	}
	if (url.includes('/eval/')) {
		const regexMatch = url.match(streamingRegex)
		if (regexMatch) {
			return true
		}
	}
	return false
}
