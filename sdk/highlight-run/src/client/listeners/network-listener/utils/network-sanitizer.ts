import { Headers, Request, Response } from './models'

export const sanitizeResource = <T extends Request | Response>(
	resource: T,
	headersToRedact: string[],
	headersToRecord?: string[],
): T => {
	const newHeaders = sanitizeHeaders(
		headersToRedact,
		resource.headers,
		headersToRecord,
	)

	return {
		...resource,
		headers: newHeaders,
	}
}

export const sanitizeHeaders = (
	headersToRedact: string[],
	headers?: Headers,
	headersToRecord?: string[],
) => {
	const newHeaders = { ...headers }

	// `headersToRecord` overrides `headersToRedact`.
	if (headersToRecord) {
		Object.keys(newHeaders)?.forEach((header: string) => {
			// Only keep the keys that are specified in `headersToRecord`.
			if (![...headersToRecord].includes(header?.toLowerCase())) {
				newHeaders[header] = '[REDACTED]'
			}
		})

		return newHeaders
	}

	Object.keys(newHeaders)?.forEach((header: string) => {
		// Redact all the keys in `headersToRedact`.
		if (
			[...SENSITIVE_HEADERS, ...headersToRedact].includes(
				header?.toLowerCase(),
			)
		) {
			newHeaders[header] = '[REDACTED]'
		}
	})

	return newHeaders
}

/** Known headers that contain secrets. Applies to both request and response headers. */
const SENSITIVE_HEADERS = [
	'authorization',
	'cookie',
	'proxy-authorization',
	'set-cookie',
	'token',
]

/** Known URLs that contains secrets. */
export const DEFAULT_URL_BLOCKLIST = [
	'https://www.googleapis.com/identitytoolkit',
	'https://securetoken.googleapis.com',
]

/**
 * Sensitive query parameter keys that should be redacted according to
 * OpenTelemetry semantic conventions for HTTP spans.
 * @see https://opentelemetry.io/docs/specs/semconv/http/http-spans/
 */
const SENSITIVE_QUERY_PARAMS = [
	'awsaccesskeyid',
	'signature',
	'sig',
	'x-goog-signature',
]

/**
 * Safely parses a URL, handling both absolute and relative URLs.
 * For relative URLs, resolves against globalThis.location.origin (browser/worker)
 * or a placeholder base (non-browser environments).
 */
export const safeParseUrl = (url: string): URL => {
	try {
		return new URL(url)
	} catch {
		// For relative URLs, we need a base to parse. The base doesn't affect
		// the output since sanitizeUrl strips it for relative URLs.
		// Use globalThis for broader environment support (window, workers, etc.)
		return new URL(url, globalThis.location?.origin ?? 'http://example.com')
	}
}

/**
 * Sanitizes a URL according to OpenTelemetry semantic conventions.
 * - Redacts credentials (username:password) in the URL
 * - Redacts sensitive query parameter values while preserving keys
 * - Handles absolute, relative, and protocol-relative URLs
 *
 * @param url - The URL string to sanitize
 * @returns Sanitized URL string
 *
 * @example
 * sanitizeUrl('https://user:pass@example.com/path')
 * // Returns: 'https://REDACTED:REDACTED@example.com/path'
 *
 * @example
 * sanitizeUrl('https://example.com/path?color=blue&sig=secret123')
 * // Returns: 'https://example.com/path?color=blue&sig=REDACTED'
 *
 * @example
 * sanitizeUrl('/api?sig=secret123')
 * // Returns: '/api?sig=REDACTED'
 *
 * @example
 * sanitizeUrl('//example.com/path?sig=secret123')
 * // Returns: '//example.com/path?sig=REDACTED'
 */
export const sanitizeUrl = (url: string): string => {
	try {
		const urlObject = safeParseUrl(url)

		if (urlObject.username || urlObject.password) {
			urlObject.username = 'REDACTED'
			urlObject.password = 'REDACTED'
		}

		const searchParams = urlObject.searchParams
		SENSITIVE_QUERY_PARAMS.forEach((sensitiveParam) => {
			for (const key of Array.from(searchParams.keys())) {
				if (key.toLowerCase() === sensitiveParam) {
					searchParams.set(key, 'REDACTED')
				}
			}
		})

		// If the URL is relative (but not protocol-relative), return only the pathname + search + hash
		if (!url.includes('://') && !url.startsWith('//')) {
			return urlObject.pathname + urlObject.search + urlObject.hash
		}

		// For protocol-relative URLs, preserve the //host format
		if (url.startsWith('//')) {
			let result = '//'
			// Include credentials if present (they will be redacted)
			if (urlObject.username || urlObject.password) {
				result += urlObject.username + ':' + urlObject.password + '@'
			}
			result +=
				urlObject.host +
				urlObject.pathname +
				urlObject.search +
				urlObject.hash
			return result
		}

		return urlObject.toString()
	} catch {
		return url
	}
}
