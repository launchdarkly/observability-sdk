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

/** These are known headers that are secrets. */
const SENSITIVE_HEADERS = [
	'authorization',
	'cookie',
	'proxy-authorization',
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
 * Sanitizes a URL according to OpenTelemetry semantic conventions.
 * - Redacts credentials (username:password) in the URL
 * - Redacts sensitive query parameter values while preserving keys
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
 */
export const sanitizeUrl = (url: string): string => {
	try {
		const urlObject = new URL(url)

		// Redact credentials if present
		if (urlObject.username || urlObject.password) {
			urlObject.username = 'REDACTED'
			urlObject.password = 'REDACTED'
		}

		// Redact sensitive query parameters
		const searchParams = urlObject.searchParams
		SENSITIVE_QUERY_PARAMS.forEach((sensitiveParam) => {
			// Check all query params case-insensitively
			for (const key of Array.from(searchParams.keys())) {
				if (key.toLowerCase() === sensitiveParam) {
					searchParams.set(key, 'REDACTED')
				}
			}
		})

		return urlObject.toString()
	} catch {
		// If URL parsing fails, return original URL
		// This handles relative URLs or malformed URLs
		return url
	}
}
