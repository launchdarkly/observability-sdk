import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Span } from '@opentelemetry/api'
import { enhanceSpanWithHttpRequestAttributes } from './index'
import {
	getCapturedRequestBody,
	installFetchRequestBodyCapture,
	normalizeHeaders,
	normalizeRequestBody,
} from './request-body'

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

describe('normalizeHeaders', () => {
	it('returns an empty object for missing headers', () => {
		expect(normalizeHeaders(undefined)).toEqual({})
		expect(normalizeHeaders(null)).toEqual({})
		expect(normalizeHeaders('')).toEqual({})
	})

	it('copies plain objects', () => {
		expect(
			normalizeHeaders({ 'Content-Type': 'application/json' }),
		).toEqual({ 'Content-Type': 'application/json' })
	})

	it('reads Headers instances, which spread to {}', () => {
		const headers = new Headers({
			'Content-Type': 'application/json',
			'X-Request-Id': 'abc',
		})
		expect({ ...headers }).toEqual({})
		expect(normalizeHeaders(headers)).toEqual({
			'content-type': 'application/json',
			'x-request-id': 'abc',
		})
	})

	it('reads [name, value] arrays and combines repeated names', () => {
		expect(
			normalizeHeaders([
				['Accept', 'application/json'],
				['Accept', 'text/plain'],
				['X-Request-Id', 'abc'],
			]),
		).toEqual({
			accept: 'application/json, text/plain',
			'x-request-id': 'abc',
		})
	})
})

describe('normalizeRequestBody', () => {
	it('returns undefined for no body', () => {
		expect(normalizeRequestBody(undefined)).toBeUndefined()
		expect(normalizeRequestBody(null)).toBeUndefined()
	})

	it('passes strings through untouched', () => {
		expect(normalizeRequestBody('{"a":1}')).toBe('{"a":1}')
	})

	it('serializes URLSearchParams as form encoding', () => {
		const params = new URLSearchParams({ a: '1', b: 'two words' })
		expect(normalizeRequestBody(params)).toBe('a=1&b=two+words')
	})

	it('serializes FormData as JSON and describes file parts', () => {
		const form = new FormData()
		form.append('name', 'widget')
		form.append('tag', 'a')
		form.append('tag', 'b')
		form.append(
			'upload',
			new File(['hello'], 'hello.txt', { type: 'text/plain' }),
		)
		expect(JSON.parse(normalizeRequestBody(form)!)).toEqual({
			name: 'widget',
			tag: ['a', 'b'],
			upload: '[File name="hello.txt" type="text/plain" size=5]',
		})
	})

	it('describes Blobs instead of reading them', () => {
		const blob = new Blob(['{"a":1}'], { type: 'application/json' })
		expect(normalizeRequestBody(blob)).toBe(
			'[Blob type="application/json" size=7]',
		)
	})

	it('decodes UTF-8 ArrayBuffers and typed arrays as text', () => {
		const bytes = new TextEncoder().encode('{"a":"é"}')
		expect(normalizeRequestBody(bytes)).toBe('{"a":"é"}')
		expect(normalizeRequestBody(bytes.buffer)).toBe('{"a":"é"}')
	})

	it('describes binary buffers that are not valid UTF-8', () => {
		const bytes = new Uint8Array([0xff, 0xfe, 0x00, 0x01])
		expect(normalizeRequestBody(bytes)).toBe('[binary size=4]')
	})

	it('leaves streams to the fetch capture', () => {
		const stream = new ReadableStream()
		expect(normalizeRequestBody(stream)).toBeUndefined()
	})

	it('serializes plain objects as JSON', () => {
		expect(normalizeRequestBody({ a: 1 })).toBe('{"a":1}')
	})
})

describe('installFetchRequestBodyCapture', () => {
	const originalFetch = window.fetch
	let fetchSpy: ReturnType<typeof vi.fn>
	let uninstall: () => void

	beforeEach(() => {
		fetchSpy = vi.fn(async () => new Response('ok'))
		window.fetch = fetchSpy as unknown as typeof fetch
		uninstall = installFetchRequestBodyCapture()
	})

	afterEach(() => {
		uninstall()
		window.fetch = originalFetch
	})

	it('captures the body of a Request object and still calls through', async () => {
		const request = new Request('https://api.example.com/items', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: '{"name":"widget"}',
		})

		const response = await window.fetch(request)

		expect(response.status).toBe(200)
		expect(fetchSpy).toHaveBeenCalledWith(request, undefined)
		expect(await getCapturedRequestBody(request)).toBe('{"name":"widget"}')
	})

	it('does not consume the body the network layer will read', async () => {
		const request = new Request('https://api.example.com/items', {
			method: 'POST',
			body: '{"name":"widget"}',
		})

		await window.fetch(request)
		await getCapturedRequestBody(request)

		expect(request.bodyUsed).toBe(false)
		expect(await request.text()).toBe('{"name":"widget"}')
	})

	it('prefers an explicit init.body over the Request body', async () => {
		const request = new Request('https://api.example.com/items', {
			method: 'POST',
			body: 'from-request',
		})

		await window.fetch(request, { body: 'from-init' })

		expect(await getCapturedRequestBody(request)).toBe('from-init')
	})

	it('captures nothing for a Request without a body', async () => {
		const request = new Request('https://api.example.com/items')

		await window.fetch(request)

		expect(await getCapturedRequestBody(request)).toBeUndefined()
	})

	it('ignores string and URL inputs, whose init.body is available to the hook', async () => {
		await window.fetch('https://api.example.com/items', {
			method: 'POST',
			body: '{"a":1}',
		})

		expect(fetchSpy).toHaveBeenCalledWith('https://api.example.com/items', {
			method: 'POST',
			body: '{"a":1}',
		})
	})

	it('stops reading at the body size limit for the content type', async () => {
		const request = new Request('https://api.example.com/upload', {
			method: 'POST',
			headers: { 'Content-Type': 'application/octet-stream' },
			body: 'x'.repeat(200 * 1024),
		})

		await window.fetch(request)

		expect((await getCapturedRequestBody(request))?.length).toBe(64 * 1024)
	})

	it('restores the original fetch on uninstall', () => {
		uninstall()
		expect(window.fetch).toBe(fetchSpy)
	})

	it('does not unwind a patch applied on top of it', () => {
		const outer = vi.fn()
		window.fetch = outer as unknown as typeof fetch
		uninstall()
		expect(window.fetch).toBe(outer)
	})
})

describe('enhanceSpanWithHttpRequestAttributes with non-string inputs', () => {
	const options = { enabled: true, recordHeadersAndBody: true }

	it('records a URLSearchParams body and Headers-instance headers', () => {
		const span = createMockSpan('https://api.example.com/login')
		enhanceSpanWithHttpRequestAttributes(
			span,
			new URLSearchParams({ user: 'ann', remember: '1' }),
			new Headers({
				'Content-Type': 'application/x-www-form-urlencoded',
				Authorization: 'Bearer secret',
			}),
			options,
		)

		expect(span.attributes['http.request.body']).toBe('user=ann&remember=1')
		expect(span.attributes['http.request.header.content-type']).toBe(
			'application/x-www-form-urlencoded',
		)
		expect(span.attributes['http.request.header.authorization']).toBe(
			'[REDACTED]',
		)
	})

	it('redacts keys inside a FormData body', () => {
		const form = new FormData()
		form.append('user', 'ann')
		form.append('password', 'hunter2')
		const span = createMockSpan('https://api.example.com/login')
		enhanceSpanWithHttpRequestAttributes(span, form, undefined, {
			...options,
			networkBodyKeysToRedact: ['password'],
		})

		expect(span.attributes['http.request.body']).toBe(
			'{"user":"ann","password":"[REDACTED]"}',
		)
	})

	it('does not set http.request.body when there is no body', () => {
		const span = createMockSpan('https://api.example.com/items')
		enhanceSpanWithHttpRequestAttributes(
			span,
			undefined,
			undefined,
			options,
		)

		expect('http.request.body' in span.attributes).toBe(false)
	})
})
