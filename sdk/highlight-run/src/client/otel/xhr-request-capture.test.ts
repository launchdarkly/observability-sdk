import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Span } from '@opentelemetry/api'
import type { BrowserXHR } from '../listeners/network-listener/utils/xhr-listener'
import { enhanceSpanWithHttpRequestAttributes } from './index'
import { installXhrRequestCapture } from './xhr-request-capture'

const createMockSpan = (url: string) => {
	const attributes: Record<string, unknown> = { 'http.url': url }
	const span = {
		attributes,
		setAttribute: vi.fn((key: string, value: unknown) => {
			attributes[key] = value
			return span
		}),
		setAttributes: vi.fn((attrs: Record<string, unknown>) => {
			Object.assign(attributes, attrs)
			return span
		}),
		spanContext: () => ({ traceId: 'trace', spanId: 'span' }),
	}
	return span as unknown as Span & { attributes: Record<string, unknown> }
}

describe('installXhrRequestCapture', () => {
	const originalOpen = XMLHttpRequest.prototype.open
	const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader
	const originalSend = XMLHttpRequest.prototype.send

	let sendSpy: ReturnType<typeof vi.fn>
	let uninstall: () => void

	beforeEach(() => {
		// Stub the network layer; everything above it stays real jsdom.
		sendSpy = vi.fn()
		XMLHttpRequest.prototype.send =
			sendSpy as unknown as typeof originalSend
		uninstall = installXhrRequestCapture([
			'https://securetoken.googleapis.com',
		])
	})

	afterEach(() => {
		uninstall()
		XMLHttpRequest.prototype.open = originalOpen
		XMLHttpRequest.prototype.setRequestHeader = originalSetRequestHeader
		XMLHttpRequest.prototype.send = originalSend
	})

	it('stashes method, url, headers and a string body on the instance', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr.setRequestHeader('Content-Type', 'application/json')
		xhr.setRequestHeader('X-Request-Id', 'abc')
		xhr.send('{"name":"widget"}')

		expect(xhr._method).toBe('POST')
		expect(xhr._url).toBe('https://api.example.com/items')
		expect(xhr._requestHeaders).toEqual({
			'Content-Type': 'application/json',
			'X-Request-Id': 'abc',
		})
		expect(xhr._body).toBe('{"name":"widget"}')
		expect(sendSpy).toHaveBeenCalledWith('{"name":"widget"}')
	})

	it('stringifies object bodies', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr.send({ name: 'widget' } as unknown as string)

		expect(xhr._body).toBe('{"name":"widget"}')
	})

	it('accepts URL objects', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('PUT', new URL('https://api.example.com/items/1'))
		xhr.send('{}')

		expect(xhr._url).toBe('https://api.example.com/items/1')
	})

	it('does not stash a body when none is sent', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('GET', 'https://api.example.com/items')
		xhr.send()

		expect(xhr._body).toBeUndefined()
		expect(xhr._requestHeaders).toEqual({})
	})

	it('does not stash bodies for blocklisted urls', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://securetoken.googleapis.com/v1/token')
		xhr.send('{"refresh_token":"secret"}')

		expect(xhr._body).toBeUndefined()
	})

	it('leaves a body already stashed by the session replay listener alone', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr._body = 'from-xhr-listener'
		xhr.send('{"name":"widget"}')

		expect(xhr._body).toBe('from-xhr-listener')
	})

	it('resets headers when the request is re-opened', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr.setRequestHeader('X-First', '1')
		xhr.open('POST', 'https://api.example.com/other')

		expect(xhr._requestHeaders).toEqual({})
		expect(xhr._url).toBe('https://api.example.com/other')
	})

	it('does not carry a stale body onto a reused request', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr.send('{"name":"first"}')
		expect(xhr._body).toBe('{"name":"first"}')

		xhr.open('POST', 'https://api.example.com/items')
		xhr.send('{"name":"second"}')
		expect(xhr._body).toBe('{"name":"second"}')

		xhr.open('GET', 'https://api.example.com/items')
		xhr.send()
		expect(xhr._body).toBeUndefined()
	})

	it('restores the original prototype methods on uninstall', () => {
		uninstall()

		expect(XMLHttpRequest.prototype.open).toBe(originalOpen)
		expect(XMLHttpRequest.prototype.setRequestHeader).toBe(
			originalSetRequestHeader,
		)
		expect(XMLHttpRequest.prototype.send).toBe(sendSpy)
	})

	it('does not unwind a patch applied on top of it', () => {
		const outerSend = vi.fn()
		XMLHttpRequest.prototype.send =
			outerSend as unknown as typeof originalSend
		uninstall()

		expect(XMLHttpRequest.prototype.send).toBe(outerSend)
	})

	it('feeds http.request.body and request headers to the span without the session replay listener', () => {
		const xhr = new XMLHttpRequest() as BrowserXHR
		xhr.open('POST', 'https://api.example.com/items')
		xhr.setRequestHeader('Content-Type', 'application/json')
		xhr.setRequestHeader('Authorization', 'Bearer secret')
		xhr.send('{"name":"widget","password":"hunter2"}')

		const span = createMockSpan('https://api.example.com/items')
		enhanceSpanWithHttpRequestAttributes(
			span,
			xhr._body,
			xhr._requestHeaders as unknown as Headers,
			{
				enabled: true,
				recordHeadersAndBody: true,
				networkBodyKeysToRedact: ['password'],
			},
		)

		expect(span.attributes['http.request.body']).toBe(
			'{"name":"widget","password":"[REDACTED]"}',
		)
		expect(span.attributes['http.request.header.content-type']).toBe(
			'application/json',
		)
		expect(span.attributes['http.request.header.authorization']).toBe(
			'[REDACTED]',
		)
	})
})
