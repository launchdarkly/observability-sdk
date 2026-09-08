import { describe, expect, it } from 'vitest'
import {
	bodyOmittedPlaceholder,
	getBodySizeLimit,
	getBodyThatShouldBeRecorded,
} from './xhr-listener'
import { getResponseBody } from './fetch-listener'

const json = { 'content-type': 'application/json' }

describe('body size limits', () => {
	it('caps JSON and text bodies at 256 KiB and everything else at 64 KiB', () => {
		expect(getBodySizeLimit(json)).toBe(256 * 1024)
		expect(
			getBodySizeLimit({ 'Content-Type': 'text/plain; charset=utf-8' }),
		).toBe(256 * 1024)
		expect(
			getBodySizeLimit({ 'content-type': 'application/octet-stream' }),
		).toBe(64 * 1024)
		expect(getBodySizeLimit(undefined)).toBe(64 * 1024)
	})

	it('describes an oversized body instead of parsing or recording it', () => {
		const big = JSON.stringify({
			items: Array.from({ length: 20000 }, (_, i) => ({
				i,
				name: `product ${i}`,
			})),
		})
		expect(big.length).toBeGreaterThan(256 * 1024)
		const recorded = getBodyThatShouldBeRecorded(
			big,
			['password'],
			undefined,
			json,
		)
		expect(recorded).toBe(bodyOmittedPlaceholder(big.length, 256 * 1024))
		expect(recorded.length).toBeLessThan(200)
	})

	it('still redacts bodies under the limit', () => {
		const body = JSON.stringify({ user: 'chad', password: 'hunter2' })
		const recorded = getBodyThatShouldBeRecorded(
			body,
			['password'],
			undefined,
			json,
		)
		expect(JSON.parse(recorded)).toEqual({
			user: 'chad',
			password: '[REDACTED]',
		})
	})

	it('stops reading a cloned response stream once it is over the limit', async () => {
		const chunk = new TextEncoder().encode('x'.repeat(64 * 1024))
		let pulls = 0
		const stream = new ReadableStream<Uint8Array>({
			pull(controller) {
				pulls++
				controller.enqueue(chunk) // never ends on its own, like a long streaming response
			},
		})
		const response = new Response(stream, {
			headers: { 'content-type': 'application/json' },
		})
		const text = await getResponseBody(response, undefined, undefined)
		expect(text).toMatch(
			/^\[body omitted: \d+ bytes exceeds the 262144 byte limit\]$/,
		)
		// 256 KiB limit = 4 chunks of 64 KiB; the stream may prefetch a couple more before cancel lands
		expect(pulls).toBeLessThanOrEqual(8)
		// Response.clone() is a tee; cancelling our branch must stop our reads
		// (the source is only cancelled once the app's branch is too).
		const pullsAfter = pulls
		await new Promise((r) => setTimeout(r, 50))
		expect(pulls).toBe(pullsAfter)
	})

	it('records a small streamed response in full', async () => {
		const response = new Response('{"ok":true}', {
			headers: { 'content-type': 'application/json' },
		})
		expect(await getResponseBody(response, undefined, undefined)).toBe(
			'{"ok":true}',
		)
	})
})
