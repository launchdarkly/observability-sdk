import { afterEach, describe, expect, it } from 'vitest'
import { getBodyData } from '../listeners/network-listener/utils/xhr-listener'
import { installXhrRequestCapture } from './xhr-request-capture'
import { normalizeRequestBody } from './request-body'

describe("XHR body capture and the SDK's own exports", () => {
	let cleanup: (() => void) | undefined
	afterEach(() => {
		cleanup?.()
		cleanup = undefined
	})

	it('describes binary bodies instead of JSON.stringify-ing them', () => {
		// The OTLP exporter sends its batches as a Uint8Array; stringifying one
		// produced {"0":123,"1":34,...}, ~15x the payload, on every export.
		const payload = new TextEncoder().encode(
			'{"resourceSpans":[' + 'x'.repeat(200000) + ']}',
		)
		const recorded = getBodyData(
			payload,
			'https://otel.observability.app.launchdarkly.com/v1/traces',
		)
		expect(recorded).toBe(`[binary size=${payload.byteLength}]`)
		expect(
			getBodyData(payload.buffer, 'https://api.example.com/upload'),
		).toMatch(/^\[binary size=\d+\]$/)
		expect(
			getBodyData(
				new Blob(['abc'], { type: 'text/plain' }),
				'https://api.example.com/upload',
			),
		).toBe('[Blob type="text/plain" size=3]')
		expect(getBodyData('{"a":1}', 'https://api.example.com/x')).toBe(
			'{"a":1}',
		)
	})

	it('does not stash bodies for blocklisted (own) endpoints', () => {
		cleanup = installXhrRequestCapture([
			'https://otel.observability.app.launchdarkly.com',
			'https://pub.observability.app.launchdarkly.com',
		])
		const own = new XMLHttpRequest() as XMLHttpRequest & {
			_body?: unknown
			_url?: string
		}
		own.open(
			'POST',
			'https://otel.observability.app.launchdarkly.com/v1/traces',
		)
		try {
			own.send(new Uint8Array([1, 2, 3]))
		} catch {}
		expect(own._url).toContain('otel.observability')
		expect(own._body).toBeUndefined()

		const app = new XMLHttpRequest() as XMLHttpRequest & { _body?: unknown }
		app.open('POST', 'https://api.example.com/cart/load')
		try {
			app.send('{"ShipmentId":1}')
		} catch {}
		expect(app._body).toBe('{"ShipmentId":1}')
	})

	it('caps binary fetch bodies at the recordable size instead of decoding 64 MiB', () => {
		const big = new Uint8Array(300 * 1024).fill(65) // 'A' * 300 KiB, valid UTF-8
		expect(normalizeRequestBody(big)).toBe(
			`[binary size=${big.byteLength}]`,
		)
		const small = new TextEncoder().encode('{"ok":true}')
		expect(normalizeRequestBody(small)).toBe('{"ok":true}')
	})
})
