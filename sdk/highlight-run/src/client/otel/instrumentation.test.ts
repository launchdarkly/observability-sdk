import { describe, it, expect } from 'vitest'
import {
	sanitizeHeaders,
	sanitizeUrl,
} from '../listeners/network-listener/utils/network-sanitizer'
import { parseXhrResponseHeaders } from './index'

/**
 * Helper to convert headers to OTel semantic convention format for testing.
 * This mirrors the logic in convertHeadersToOtelAttributes.
 */
const convertHeadersToOtelFormat = (
	headers: { [key: string]: string },
	prefix: 'http.request.header' | 'http.response.header',
): { [key: string]: string | string[] } => {
	const attributes: { [key: string]: string | string[] } = {}
	Object.entries(headers).forEach(([key, value]) => {
		const normalizedKey = key.toLowerCase().replace(/_/g, '-')
		const attributeName = `${prefix}.${normalizedKey}`

		// Only use arrays if there are multiple values for the same header
		if (attributes[attributeName]) {
			// Convert to array if not already, then add new value
			const existing = attributes[attributeName]
			attributes[attributeName] = Array.isArray(existing)
				? [...existing, value]
				: [existing, value]
		} else {
			// Single value - store as string
			attributes[attributeName] = value
		}
	})
	return attributes
}

describe('Network Instrumentation Custom Attributes', () => {
	describe('convertHeadersToOtelFormat', () => {
		it('should convert headers to OTel semantic convention format', () => {
			const headers = {
				'content-type': 'application/json',
				'x-request-id': 'abc-123',
				'Cache-Control': 'no-cache',
			}

			const result = convertHeadersToOtelFormat(
				headers,
				'http.request.header',
			)

			expect(result).toEqual({
				'http.request.header.content-type': 'application/json',
				'http.request.header.x-request-id': 'abc-123',
				'http.request.header.cache-control': 'no-cache',
			})
		})

		it('should normalize header names to lowercase', () => {
			const headers = {
				'Content-Type': 'text/html',
				'X-Custom-Header': 'value',
			}

			const result = convertHeadersToOtelFormat(
				headers,
				'http.response.header',
			)

			expect(result).toEqual({
				'http.response.header.content-type': 'text/html',
				'http.response.header.x-custom-header': 'value',
			})
		})

		it('should replace underscores with dashes in header names', () => {
			const headers = {
				x_custom_header: 'value',
				another_header: 'test',
			}

			const result = convertHeadersToOtelFormat(
				headers,
				'http.request.header',
			)

			expect(result).toEqual({
				'http.request.header.x-custom-header': 'value',
				'http.request.header.another-header': 'test',
			})
		})

		it('should store single values as strings, not arrays', () => {
			const headers = {
				'content-type': 'application/json',
			}

			const result = convertHeadersToOtelFormat(
				headers,
				'http.request.header',
			)

			expect(result['http.request.header.content-type']).toBe(
				'application/json',
			)
			expect(
				result['http.request.header.content-type'],
			).not.toBeInstanceOf(Array)
		})

		it('should use arrays only when multiple values exist for the same header', () => {
			// Simulate duplicate headers by calling with same header key twice
			const headers = {
				'set-cookie': 'session=abc123',
			}

			const result = convertHeadersToOtelFormat(
				headers,
				'http.response.header',
			)

			// First, it should be a string
			expect(result['http.response.header.set-cookie']).toBe(
				'session=abc123',
			)

			// Now simulate adding a second value
			const attributeName = 'http.response.header.set-cookie'
			const existing = result[attributeName]
			result[attributeName] = Array.isArray(existing)
				? [...existing, 'token=xyz789']
				: [existing, 'token=xyz789']

			// Now it should be an array
			expect(result['http.response.header.set-cookie']).toEqual([
				'session=abc123',
				'token=xyz789',
			])
		})

		it('should handle empty headers object', () => {
			const result = convertHeadersToOtelFormat({}, 'http.request.header')
			expect(result).toEqual({})
		})
	})

	describe('parseXhrResponseHeaders', () => {
		it('should parse XHR header string correctly', () => {
			const headerString =
				'content-type: application/json\r\nx-request-id: abc-123\r\ncache-control: no-cache'

			const parsed = parseXhrResponseHeaders(headerString)

			expect(parsed).toEqual({
				'content-type': 'application/json',
				'x-request-id': 'abc-123',
				'cache-control': 'no-cache',
			})
		})

		it('should handle header values with colons', () => {
			const headerString =
				'content-type: application/json; charset=utf-8\r\ndate: Mon, 01 Jan 2024 12:00:00 GMT'

			const parsed = parseXhrResponseHeaders(headerString)

			expect(parsed).toEqual({
				'content-type': 'application/json; charset=utf-8',
				date: 'Mon, 01 Jan 2024 12:00:00 GMT',
			})
		})

		it('should handle empty header string', () => {
			const parsed = parseXhrResponseHeaders('')
			expect(parsed).toEqual({})
		})

		it('should handle whitespace-only header string', () => {
			const parsed = parseXhrResponseHeaders('   \n\r\n   ')
			expect(parsed).toEqual({})
		})

		it('should handle newline-only separators', () => {
			const headerString =
				'content-type: application/json\nx-request-id: abc-123'

			const parsed = parseXhrResponseHeaders(headerString)

			expect(parsed).toEqual({
				'content-type': 'application/json',
				'x-request-id': 'abc-123',
			})
		})
	})

	describe('sanitizeHeaders', () => {
		describe('basic sanitization', () => {
			it('should return all headers when no redaction is configured', () => {
				const headers = {
					'content-type': 'application/json',
					'x-request-id': '12345',
					'cache-control': 'no-cache',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					'x-request-id': '12345',
					'cache-control': 'no-cache',
				})
			})

			it('should handle empty headers object', () => {
				const result = sanitizeHeaders([], {})
				expect(result).toEqual({})
			})

			it('should handle undefined headers', () => {
				const result = sanitizeHeaders([], undefined)
				expect(result).toEqual({})
			})
		})

		describe('networkHeadersToRedact', () => {
			it('should redact specified headers', () => {
				const headers = {
					'content-type': 'application/json',
					'x-secret': 'sensitive-value',
					'x-api-key': 'my-api-key',
				}

				const result = sanitizeHeaders(
					['x-secret', 'x-api-key'],
					headers,
				)

				expect(result).toEqual({
					'content-type': 'application/json',
					'x-secret': '[REDACTED]',
					'x-api-key': '[REDACTED]',
				})
			})

			it('should be case-insensitive for redaction', () => {
				const headers = {
					'X-Secret': 'sensitive-value',
					'X-API-KEY': 'my-api-key',
				}

				const result = sanitizeHeaders(
					['x-secret', 'x-api-key'],
					headers,
				)

				expect(result).toEqual({
					'X-Secret': '[REDACTED]',
					'X-API-KEY': '[REDACTED]',
				})
			})
		})

		describe('default sensitive headers', () => {
			it('should automatically redact authorization header', () => {
				const headers = {
					'content-type': 'application/json',
					authorization: 'Bearer token123',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					authorization: '[REDACTED]',
				})
			})

			it('should automatically redact cookie header', () => {
				const headers = {
					'content-type': 'application/json',
					cookie: 'session=abc123',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					cookie: '[REDACTED]',
				})
			})

			it('should automatically redact proxy-authorization header', () => {
				const headers = {
					'content-type': 'application/json',
					'proxy-authorization': 'Basic abc123',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					'proxy-authorization': '[REDACTED]',
				})
			})

			it('should automatically redact token header', () => {
				const headers = {
					'content-type': 'application/json',
					token: 'secret-token',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					token: '[REDACTED]',
				})
			})

			it('should redact multiple sensitive headers at once', () => {
				const headers = {
					'content-type': 'application/json',
					authorization: 'Bearer token123',
					cookie: 'session=abc123',
					'proxy-authorization': 'Basic abc123',
					token: 'secret-token',
				}

				const result = sanitizeHeaders([], headers)

				expect(result).toEqual({
					'content-type': 'application/json',
					authorization: '[REDACTED]',
					cookie: '[REDACTED]',
					'proxy-authorization': '[REDACTED]',
					token: '[REDACTED]',
				})
			})
		})

		describe('headerKeysToRecord (allowlist)', () => {
			it('should only keep specified headers when allowlist is set', () => {
				const headers = {
					'content-type': 'application/json',
					'x-request-id': '12345',
					'x-secret': 'sensitive',
					'cache-control': 'no-cache',
				}

				const result = sanitizeHeaders([], headers, ['x-request-id'])

				expect(result).toEqual({
					'content-type': '[REDACTED]',
					'x-request-id': '12345',
					'x-secret': '[REDACTED]',
					'cache-control': '[REDACTED]',
				})
			})

			it('should be case-insensitive for allowlist', () => {
				const headers = {
					'Content-Type': 'application/json',
					'X-Request-ID': '12345',
				}

				const result = sanitizeHeaders([], headers, [
					'content-type',
					'x-request-id',
				])

				expect(result).toEqual({
					'Content-Type': 'application/json',
					'X-Request-ID': '12345',
				})
			})

			it('should override headersToRedact when allowlist is set', () => {
				const headers = {
					'content-type': 'application/json',
					'x-request-id': '12345',
					authorization: 'Bearer token',
				}

				// Even though authorization is in sensitive headers,
				// allowlist takes precedence
				const result = sanitizeHeaders(['x-request-id'], headers, [
					'content-type',
					'authorization',
				])

				expect(result).toEqual({
					'content-type': 'application/json',
					'x-request-id': '[REDACTED]',
					authorization: 'Bearer token',
				})
			})
		})
	})

	describe('Response Header Capture Integration', () => {
		const captureResponseAttributes = (
			responseHeaders: { [key: string]: string },
			responseBody: string,
			networkRecordingOptions?: {
				recordHeadersAndBody?: boolean
				networkHeadersToRedact?: string[]
				headerKeysToRecord?: string[]
			},
		): { headers?: string; body?: string } | null => {
			if (!networkRecordingOptions?.recordHeadersAndBody) {
				return null
			}

			const sanitizedResponseHeaders = sanitizeHeaders(
				networkRecordingOptions?.networkHeadersToRedact ?? [],
				responseHeaders,
				networkRecordingOptions?.headerKeysToRecord,
			)

			// Always preserve content-type unless explicitly excluded via headerKeysToRecord
			const contentType =
				responseHeaders['content-type'] ??
				responseHeaders['Content-Type']
			if (contentType && !networkRecordingOptions?.headerKeysToRecord) {
				sanitizedResponseHeaders['content-type'] = contentType
			}

			return {
				headers: JSON.stringify(sanitizedResponseHeaders),
				body: responseBody,
			}
		}

		describe('recordHeadersAndBody conditional', () => {
			it('should NOT capture headers or body when recordHeadersAndBody is false', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
				}

				const result = captureResponseAttributes(
					responseHeaders,
					'{"data": "test"}',
					{ recordHeadersAndBody: false },
				)

				expect(result).toBeNull()
			})

			it('should NOT capture headers or body when recordHeadersAndBody is undefined', () => {
				const responseHeaders = {
					'content-type': 'application/json',
				}

				const result = captureResponseAttributes(
					responseHeaders,
					'body content',
					{}, // recordHeadersAndBody not set
				)

				expect(result).toBeNull()
			})

			it('should capture headers and body when recordHeadersAndBody is true', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
				}
				const responseBody = '{"data": "test"}'

				const result = captureResponseAttributes(
					responseHeaders,
					responseBody,
					{ recordHeadersAndBody: true },
				)

				expect(result).not.toBeNull()
				expect(result?.body).toBe(responseBody)
				const parsedHeaders = JSON.parse(result?.headers ?? '{}')
				expect(parsedHeaders['content-type']).toBe('application/json')
				expect(parsedHeaders['x-request-id']).toBe('abc-123')
			})

			it('should apply sanitization when recordHeadersAndBody is true', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					authorization: 'Bearer secret-token',
					'x-request-id': 'abc-123',
				}

				const result = captureResponseAttributes(
					responseHeaders,
					'body',
					{ recordHeadersAndBody: true },
				)

				expect(result).not.toBeNull()
				const parsedHeaders = JSON.parse(result?.headers ?? '{}')
				expect(parsedHeaders['authorization']).toBe('[REDACTED]')
				expect(parsedHeaders['x-request-id']).toBe('abc-123')
			})

			it('should apply networkHeadersToRedact when recordHeadersAndBody is true', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-internal-secret': 'secret-value',
					'x-public': 'public-value',
				}

				const result = captureResponseAttributes(
					responseHeaders,
					'body',
					{
						recordHeadersAndBody: true,
						networkHeadersToRedact: ['x-internal-secret'],
					},
				)

				expect(result).not.toBeNull()
				const parsedHeaders = JSON.parse(result?.headers ?? '{}')
				expect(parsedHeaders['x-internal-secret']).toBe('[REDACTED]')
				expect(parsedHeaders['x-public']).toBe('public-value')
			})

			it('should apply headerKeysToRecord when recordHeadersAndBody is true', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
					'x-other': 'other-value',
				}

				const result = captureResponseAttributes(
					responseHeaders,
					'body',
					{
						recordHeadersAndBody: true,
						headerKeysToRecord: ['x-request-id'],
					},
				)

				expect(result).not.toBeNull()
				const parsedHeaders = JSON.parse(result?.headers ?? '{}')
				expect(parsedHeaders['x-request-id']).toBe('abc-123')
				expect(parsedHeaders['content-type']).toBe('[REDACTED]')
				expect(parsedHeaders['x-other']).toBe('[REDACTED]')
			})
		})

		describe('Fetch Response Headers', () => {
			it('should capture all response headers', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
					'cache-control': 'max-age=3600',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed).toEqual({
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
					'cache-control': 'max-age=3600',
				})
			})

			it('should sanitize sensitive headers in response', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'set-cookie': 'session=secret',
					authorization: 'Bearer token',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('application/json')
				expect(parsed['authorization']).toBe('[REDACTED]')
			})

			it('should apply networkHeadersToRedact to response headers', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-internal-id': 'internal-123',
					'x-public-id': 'public-456',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
					networkHeadersToRedact: ['x-internal-id'],
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed).toEqual({
					'content-type': 'application/json',
					'x-internal-id': '[REDACTED]',
					'x-public-id': 'public-456',
				})
			})

			it('should preserve content-type even when trying to redact it', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
					networkHeadersToRedact: ['content-type'],
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				// content-type should be preserved despite being in redact list
				expect(parsed['content-type']).toBe('application/json')
			})

			it('should handle Content-Type with different casing', () => {
				const responseHeaders = {
					'Content-Type': 'text/html',
					'X-Request-ID': 'abc-123',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('text/html')
			})

			it('should NOT preserve content-type when headerKeysToRecord is set and excludes it', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
					'x-trace-id': 'trace-789',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
					headerKeysToRecord: ['x-request-id', 'x-trace-id'],
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				// When explicit allowlist is used, content-type is not auto-preserved
				expect(parsed['content-type']).toBe('[REDACTED]')
				expect(parsed['x-request-id']).toBe('abc-123')
				expect(parsed['x-trace-id']).toBe('trace-789')
			})

			it('should preserve content-type when headerKeysToRecord includes it', () => {
				const responseHeaders = {
					'content-type': 'application/json',
					'x-request-id': 'abc-123',
					'x-secret': 'secret-value',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
					headerKeysToRecord: ['content-type', 'x-request-id'],
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('application/json')
				expect(parsed['x-request-id']).toBe('abc-123')
				expect(parsed['x-secret']).toBe('[REDACTED]')
			})
		})

		describe('XHR Response Headers', () => {
			it('should capture and sanitize XHR response headers', () => {
				const headerString =
					'content-type: application/json\r\nauthorization: Bearer secret\r\nx-request-id: abc-123'

				const responseHeaders = parseXhrResponseHeaders(headerString)
				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('application/json')
				expect(parsed['authorization']).toBe('[REDACTED]')
				expect(parsed['x-request-id']).toBe('abc-123')
			})

			it('should preserve content-type in XHR responses', () => {
				const headerString =
					'content-type: text/html\r\nx-custom: value'

				const responseHeaders = parseXhrResponseHeaders(headerString)
				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
					networkHeadersToRedact: ['content-type'],
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('text/html')
			})

			it('should work with parseXhrResponseHeaders and recordHeadersAndBody', () => {
				const headerString =
					'content-type: application/json\r\nx-request-id: abc-123'

				const responseHeaders = parseXhrResponseHeaders(headerString)
				const result = captureResponseAttributes(
					responseHeaders,
					'{"response": "data"}',
					{ recordHeadersAndBody: true },
				)

				expect(result).not.toBeNull()
				expect(result?.body).toBe('{"response": "data"}')
				const parsedHeaders = JSON.parse(result?.headers ?? '{}')
				expect(parsedHeaders['content-type']).toBe('application/json')
			})
		})

		describe('Edge Cases', () => {
			it('should handle response with no headers', () => {
				const result = captureResponseAttributes({}, '', {
					recordHeadersAndBody: true,
				})
				expect(result?.headers).toBe('{}')
			})

			it('should handle response headers with empty values', () => {
				const responseHeaders = {
					'content-type': '',
					'x-empty': '',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe('')
				expect(parsed['x-empty']).toBe('')
			})

			it('should handle response headers with special characters', () => {
				const responseHeaders = {
					'content-type': 'application/json; charset=utf-8',
					'content-disposition':
						'attachment; filename="file with spaces.pdf"',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['content-type']).toBe(
					'application/json; charset=utf-8',
				)
				expect(parsed['content-disposition']).toBe(
					'attachment; filename="file with spaces.pdf"',
				)
			})

			it('should not fail when content-type is missing', () => {
				const responseHeaders = {
					'x-request-id': 'abc-123',
					'cache-control': 'no-cache',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed).toEqual({
					'x-request-id': 'abc-123',
					'cache-control': 'no-cache',
				})
				expect(parsed['content-type']).toBeUndefined()
			})

			it('should handle very long header values', () => {
				const longValue = 'x'.repeat(10000)
				const responseHeaders = {
					'content-type': 'application/json',
					'x-long-header': longValue,
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['x-long-header']).toBe(longValue)
			})

			it('should handle headers with unicode characters', () => {
				const responseHeaders = {
					'content-type': 'text/plain; charset=utf-8',
					'x-custom': 'value with émojis 🎉',
				}

				const result = captureResponseAttributes(responseHeaders, '', {
					recordHeadersAndBody: true,
				})
				const parsed = JSON.parse(result?.headers ?? '{}')

				expect(parsed['x-custom']).toBe('value with émojis 🎉')
			})
		})
	})

	describe('sanitizeUrl', () => {
		describe('credential redaction', () => {
			it('should redact username and password in URLs', () => {
				const url = 'https://username:password@www.example.com/'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@www.example.com/',
				)
			})

			it('should redact only username when no password is present', () => {
				const url = 'https://username@www.example.com/path'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@www.example.com/path',
				)
			})

			it('should handle URLs with credentials and query parameters', () => {
				const url =
					'https://user:pass@example.com/path?foo=bar&sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@example.com/path?foo=bar&sig=REDACTED',
				)
			})

			it('should not modify URLs without credentials', () => {
				const url = 'https://www.example.com/path'
				const result = sanitizeUrl(url)
				expect(result).toBe('https://www.example.com/path')
			})
		})

		describe('sensitive query parameter redaction', () => {
			it('should redact AWSAccessKeyId query parameter', () => {
				const url =
					'https://example.com/path?AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE&other=value'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?AWSAccessKeyId=REDACTED&other=value',
				)
			})

			it('should redact Signature query parameter', () => {
				const url =
					'https://example.com/path?Signature=somesignature&foo=bar'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?Signature=REDACTED&foo=bar',
				)
			})

			it('should redact sig query parameter', () => {
				const url =
					'https://www.example.com/path?color=blue&sig=secret123'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://www.example.com/path?color=blue&sig=REDACTED',
				)
			})

			it('should redact X-Goog-Signature query parameter', () => {
				const url =
					'https://storage.googleapis.com/bucket/file?X-Goog-Signature=abc123&other=value'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://storage.googleapis.com/bucket/file?X-Goog-Signature=REDACTED&other=value',
				)
			})

			it('should be case-insensitive when matching sensitive parameters', () => {
				const url =
					'https://example.com/path?signature=secret&SIG=secret2&awsaccesskeyid=key'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?signature=REDACTED&SIG=REDACTED&awsaccesskeyid=REDACTED',
				)
			})

			it('should preserve query parameter keys while redacting values', () => {
				const url =
					'https://example.com/path?foo=bar&sig=secret&baz=qux'
				const result = sanitizeUrl(url)
				expect(result).toContain('sig=REDACTED')
				expect(result).toContain('foo=bar')
				expect(result).toContain('baz=qux')
			})

			it('should handle multiple sensitive parameters', () => {
				const url =
					'https://example.com/path?AWSAccessKeyId=key&Signature=sig&sig=s&X-Goog-Signature=gs'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?AWSAccessKeyId=REDACTED&Signature=REDACTED&sig=REDACTED&X-Goog-Signature=REDACTED',
				)
			})

			it('should not modify URLs without sensitive query parameters', () => {
				const url = 'https://example.com/path?foo=bar&baz=qux'
				const result = sanitizeUrl(url)
				expect(result).toBe('https://example.com/path?foo=bar&baz=qux')
			})
		})

		describe('combined scenarios', () => {
			it('should handle URLs with both credentials and sensitive query params', () => {
				const url =
					'https://user:pass@example.com/api?key=value&sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@example.com/api?key=value&sig=REDACTED',
				)
			})

			it('should handle URLs with fragments', () => {
				const url =
					'https://user:pass@example.com/path?sig=secret#fragment'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@example.com/path?sig=REDACTED#fragment',
				)
			})

			it('should handle URLs with ports', () => {
				const url = 'https://user:pass@example.com:8080/path?sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://REDACTED:REDACTED@example.com:8080/path?sig=REDACTED',
				)
			})

			it('should handle complex URLs with all components', () => {
				const url =
					'https://admin:secret@api.example.com:443/v1/users?AWSAccessKeyId=KEY123&Signature=SIG456&filter=active#section'
				const result = sanitizeUrl(url)
				// Note: URL object automatically removes default HTTPS port (443)
				expect(result).toBe(
					'https://REDACTED:REDACTED@api.example.com/v1/users?AWSAccessKeyId=REDACTED&Signature=REDACTED&filter=active#section',
				)
			})
		})

		describe('edge cases', () => {
			it('should handle URLs with empty query parameter values', () => {
				const url = 'https://example.com/path?sig=&foo=bar'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?sig=REDACTED&foo=bar',
				)
			})

			it('should handle URLs with no query parameters', () => {
				const url = 'https://example.com/path'
				const result = sanitizeUrl(url)
				expect(result).toBe('https://example.com/path')
			})

			it('should handle URLs with only query parameters (no path)', () => {
				const url = 'https://example.com?sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe('https://example.com/?sig=REDACTED')
			})

			it('should handle http (non-https) URLs', () => {
				const url = 'http://user:pass@example.com/path?sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'http://REDACTED:REDACTED@example.com/path?sig=REDACTED',
				)
			})

			it('should return original URL if parsing fails', () => {
				const invalidUrl = 'not-a-valid-url'
				const result = sanitizeUrl(invalidUrl)
				expect(result).toBe(invalidUrl)
			})

			it('should handle localhost URLs', () => {
				const url = 'http://user:pass@localhost:3000/api?sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'http://REDACTED:REDACTED@localhost:3000/api?sig=REDACTED',
				)
			})

			it('should handle URLs with special characters in query values', () => {
				const url =
					'https://example.com/path?normal=value&sig=secret%20with%20spaces'
				const result = sanitizeUrl(url)
				expect(result).toBe(
					'https://example.com/path?normal=value&sig=REDACTED',
				)
			})

			it('should preserve URL encoding in non-sensitive parameters', () => {
				const url =
					'https://example.com/path?name=John%20Doe&sig=secret'
				const result = sanitizeUrl(url)
				expect(result).toContain('name=John+Doe')
				expect(result).toContain('sig=REDACTED')
			})
		})
	})
})
