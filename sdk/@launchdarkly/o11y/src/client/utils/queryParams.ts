/** The query parameters `captureQueryParams` captures by default. */
export const DEFAULT_CAPTURED_QUERY_PARAMS: readonly string[] = Object.freeze([
	'utm_source',
	'utm_medium',
	'utm_campaign',
	'utm_term',
	'utm_content',
])

/**
 * Picks the listed parameters from a query string. A name ending in `*`
 * matches every parameter with that prefix. The first value of a repeated
 * parameter wins; empty values are skipped.
 */
export function pickQueryParams(
	search: string,
	names: readonly string[],
): Record<string, string> {
	const picked: Record<string, string> = {}
	if (!search || !names.length) {
		return picked
	}
	const exact = new Set<string>()
	const prefixes: string[] = []
	for (const name of names) {
		if (typeof name !== 'string' || !name) continue
		if (name.endsWith('*')) {
			prefixes.push(name.slice(0, -1))
		} else {
			exact.add(name)
		}
	}
	new URLSearchParams(search).forEach((value, key) => {
		if (!value || key in picked) return
		if (exact.has(key) || prefixes.some((p) => key.startsWith(p))) {
			picked[key] = value
		}
	})
	return picked
}

/** The query parameters to capture for a `captureQueryParams` option value. */
export function capturedQueryParamNames(
	option: string[] | false | undefined,
): readonly string[] {
	if (option === undefined) return DEFAULT_CAPTURED_QUERY_PARAMS
	if (!Array.isArray(option)) return []
	return option
}

/** The attribute `captureReferrer` sets. */
export const LANDING_REFERRER_ATTR = 'landing.referrer'

/**
 * The landing referrer to record: a cross-origin `referrer` with its query
 * string, fragment and credentials stripped, or `undefined` when it is
 * empty, same-origin or unparseable.
 */
export function landingReferrer(
	referrer: string,
	currentOrigin: string,
): string | undefined {
	if (!referrer) return undefined
	let url: URL
	try {
		url = new URL(referrer)
	} catch {
		return undefined
	}
	if (url.origin !== 'null' && url.origin === currentOrigin) {
		return undefined
	}
	url.search = ''
	url.hash = ''
	url.username = ''
	url.password = ''
	return url.href
}
