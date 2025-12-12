import { describe, it, expect } from 'vitest'
import {
	sanitizeHeaders,
	sanitizeUrl,
} from '../listeners/network-listener/utils/network-sanitizer'
import {
	parseXhrResponseHeaders,
	splitHeaderValue,
	convertHeadersToOtelAttributes,
	safeParseUrl,
} from './index'

describe('Network Instrumentation Custom Attributes', () => {
	describe('splitHeaderValue', () => {
		it('should split comma-separated headers like accept', () => {
			const result = splitHeaderValue(
				'accept',
				'application/json, text/plain, */*',
			)
			expect(result).toEqual(['application/json', 'text/plain', '*/*'])
		})

		it('should split cache-control directives', () => {
			const result = splitHeaderValue(
				'cache-control',
				'no-cache, no-store, must-revalidate',
			)
			expect(result).toEqual(['no-cache', 'no-store', 'must-revalidate'])
		})

		it('should NOT split date headers (RFC 7231 date format)', () => {
			const result = splitHeaderValue(
				'date',
				'Mon, 01 Jan 2024 12:00:00 GMT',
			)
			expect(result).toEqual(['Mon, 01 Jan 2024 12:00:00 GMT'])
		})

		it('should NOT split last-modified headers', () => {
			const result = splitHeaderValue(
				'last-modified',
				'Sun, 31 Dec 2023 23:59:59 GMT',
			)
			expect(result).toEqual(['Sun, 31 Dec 2023 23:59:59 GMT'])
		})

		it('should NOT split expires headers', () => {
			const result = splitHeaderValue(
				'expires',
				'Tue, 02 Jan 2024 12:00:00 GMT',
			)
			expect(result).toEqual(['Tue, 02 Jan 2024 12:00:00 GMT'])
		})

		it('should NOT split content-type headers', () => {
			const result = splitHeaderValue(
				'content-type',
				'text/html; charset=utf-8',
			)
			expect(result).toEqual(['text/html; charset=utf-8'])
		})

		it('should NOT split custom headers with commas', () => {
			const result = splitHeaderValue(
				'x-custom-header',
				'value1, value2, value3',
			)
			expect(result).toEqual(['value1, value2, value3'])
		})

		it('should split vary header', () => {
			const result = splitHeaderValue(
				'vary',
				'Accept-Encoding, User-Agent',
			)
			expect(result).toEqual(['Accept-Encoding', 'User-Agent'])
		})

		it('should split accept-language with quality values', () => {
			const result = splitHeaderValue(
				'accept-language',
				'en-US, en;q=0.9, es;q=0.8',
			)
			expect(result).toEqual(['en-US', 'en;q=0.9', 'es;q=0.8'])
		})

		it('should trim whitespace from split values', () => {
			const result = splitHeaderValue(
				'accept',
				'  application/json  ,  text/plain  ',
			)
			expect(result).toEqual(['application/json', 'text/plain'])
		})
	})

	describe('convertHeadersToOtelAttributes', () => {
		it('should convert headers to OTel semantic convention format', () => {
			const headers = {
				'content-type': 'application/json',
				'x-request-id': 'abc-123',
				'Cache-Control': 'no-cache',
			}

			const result = convertHeadersToOtelAttributes(
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

			const result = convertHeadersToOtelAttributes(
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

			const result = convertHeadersToOtelAttributes(
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

			const result = convertHeadersToOtelAttributes(
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

		it('should split comma-separated header values into arrays', () => {
			const headers = {
				accept: 'application/json, text/plain, */*',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.request.header',
			)

			expect(result['http.request.header.accept']).toEqual([
				'application/json',
				'text/plain',
				'*/*',
			])
		})

		it('should split accept-language with quality values into arrays', () => {
			const headers = {
				'accept-language': 'en-US, en;q=0.9, es;q=0.8',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.request.header',
			)

			expect(result['http.request.header.accept-language']).toEqual([
				'en-US',
				'en;q=0.9',
				'es;q=0.8',
			])
		})

		it('should split cache-control directives into arrays', () => {
			const headers = {
				'cache-control': 'no-cache, no-store, must-revalidate',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.response.header',
			)

			expect(result['http.response.header.cache-control']).toEqual([
				'no-cache',
				'no-store',
				'must-revalidate',
			])
		})

		it('should handle mixed single and multi-value headers', () => {
			const headers = {
				'content-type': 'application/json',
				accept: 'application/json, application/xml, text/html',
				'x-custom-single': 'single-value',
				'cache-control': 'no-cache, no-store, max-age=0',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.request.header',
			)

			expect(result['http.request.header.content-type']).toBe(
				'application/json',
			)
			expect(result['http.request.header.accept']).toEqual([
				'application/json',
				'application/xml',
				'text/html',
			])
			expect(result['http.request.header.x-custom-single']).toBe(
				'single-value',
			)
			// cache-control is defined as a comma-separated header
			expect(result['http.request.header.cache-control']).toEqual([
				'no-cache',
				'no-store',
				'max-age=0',
			])
		})

		it('should NOT split date headers containing commas (RFC 7231 date format)', () => {
			const headers = {
				date: 'Mon, 01 Jan 2024 12:00:00 GMT',
				'last-modified': 'Sun, 31 Dec 2023 23:59:59 GMT',
				expires: 'Tue, 02 Jan 2024 12:00:00 GMT',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.response.header',
			)

			// Date headers should be preserved as strings, NOT split into arrays
			expect(result['http.response.header.date']).toBe(
				'Mon, 01 Jan 2024 12:00:00 GMT',
			)
			expect(result['http.response.header.last-modified']).toBe(
				'Sun, 31 Dec 2023 23:59:59 GMT',
			)
			expect(result['http.response.header.expires']).toBe(
				'Tue, 02 Jan 2024 12:00:00 GMT',
			)
		})

		it('should NOT split custom headers with commas', () => {
			const headers = {
				'x-custom-header': 'value1, value2, value3',
				'x-timestamp': 'Mon, 01 Jan 2024 12:00:00 GMT',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.request.header',
			)

			// Custom headers should NOT be split
			expect(result['http.request.header.x-custom-header']).toBe(
				'value1, value2, value3',
			)
			expect(result['http.request.header.x-timestamp']).toBe(
				'Mon, 01 Jan 2024 12:00:00 GMT',
			)
		})

		it('should split known comma-separated headers but not others', () => {
			const headers = {
				accept: 'text/html, application/json',
				'accept-language': 'en-US, en;q=0.9',
				vary: 'Accept-Encoding, User-Agent',
				date: 'Mon, 01 Jan 2024 12:00:00 GMT',
				'content-type': 'text/html; charset=utf-8',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.response.header',
			)

			// These should be split (comma-separated by spec)
			expect(result['http.response.header.accept']).toEqual([
				'text/html',
				'application/json',
			])
			expect(result['http.response.header.accept-language']).toEqual([
				'en-US',
				'en;q=0.9',
			])
			expect(result['http.response.header.vary']).toEqual([
				'Accept-Encoding',
				'User-Agent',
			])

			// These should NOT be split
			expect(result['http.response.header.date']).toBe(
				'Mon, 01 Jan 2024 12:00:00 GMT',
			)
			expect(result['http.response.header.content-type']).toBe(
				'text/html; charset=utf-8',
			)
		})

		it('should trim whitespace from split values', () => {
			const headers = {
				accept: 'application/json,  text/plain  ,   */*',
			}

			const result = convertHeadersToOtelAttributes(
				headers,
				'http.request.header',
			)

			expect(result['http.request.header.accept']).toEqual([
				'application/json',
				'text/plain',
				'*/*',
			])
		})

		it('should use arrays only when multiple values exist for the same header', () => {
			// Simulate duplicate headers by calling with same header key twice
			const headers = {
				'set-cookie': 'session=abc123',
			}

			const result = convertHeadersToOtelAttributes(
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
			const result = convertHeadersToOtelAttributes(
				{},
				'http.request.header',
			)
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

	describe('safeParseUrl', () => {
		describe('absolute URLs', () => {
			it('should parse absolute URL with path and query', () => {
				const result = safeParseUrl(
					'https://example.com/api/data?foo=bar',
				)
				expect(result.pathname).toBe('/api/data')
				expect(result.search).toBe('?foo=bar')
				expect(result.searchParams.get('foo')).toBe('bar')
				expect(result.origin).toBe('https://example.com')
			})

			it('should parse absolute URL with only path', () => {
				const result = safeParseUrl('https://example.com/api/data')
				expect(result.pathname).toBe('/api/data')
				expect(result.search).toBe('')
			})

			it('should parse absolute URL with port', () => {
				const result = safeParseUrl('http://localhost:3000/api')
				expect(result.pathname).toBe('/api')
				expect(result.port).toBe('3000')
				expect(result.origin).toBe('http://localhost:3000')
			})
		})

		describe('relative URLs', () => {
			it('should parse relative URL with leading slash', () => {
				const result = safeParseUrl('/api/data')
				expect(result.pathname).toBe('/api/data')
				expect(result.search).toBe('')
			})

			it('should parse relative URL with path and query', () => {
				const result = safeParseUrl('/api/data?foo=bar&baz=qux')
				expect(result.pathname).toBe('/api/data')
				expect(result.search).toBe('?foo=bar&baz=qux')
				expect(result.searchParams.get('foo')).toBe('bar')
				expect(result.searchParams.get('baz')).toBe('qux')
			})

			it('should parse relative URL with nested path', () => {
				const result = safeParseUrl('/api/v1/users/123?include=profile')
				expect(result.pathname).toBe('/api/v1/users/123')
				expect(result.searchParams.get('include')).toBe('profile')
			})

			it('should handle query-only relative URL', () => {
				const result = safeParseUrl('?foo=bar')
				expect(result.search).toBe('?foo=bar')
				expect(result.searchParams.get('foo')).toBe('bar')
			})
		})

		describe('edge cases', () => {
			it('should handle URL with encoded characters in query', () => {
				const result = safeParseUrl(
					'/search?q=hello%20world&filter=a%26b',
				)
				expect(result.pathname).toBe('/search')
				expect(result.searchParams.get('q')).toBe('hello world')
				expect(result.searchParams.get('filter')).toBe('a&b')
			})

			it('should handle URL with multiple query params of same key', () => {
				const result = safeParseUrl('/path?tag=a&tag=b&tag=c')
				expect(result.pathname).toBe('/path')
				expect(result.searchParams.getAll('tag')).toEqual([
					'a',
					'b',
					'c',
				])
			})

			it('should handle URL with fragment', () => {
				const result = safeParseUrl(
					'https://example.com/page?q=test#section',
				)
				expect(result.pathname).toBe('/page')
				expect(result.search).toBe('?q=test')
				expect(result.hash).toBe('#section')
			})
		})
	})
})
