import { describe, expect, it } from 'vitest'
import { getResponseBody } from './fetch-listener'

const streamOf = (...chunks: Uint8Array[]) =>
	new ReadableStream<Uint8Array>({
		start(controller) {
			for (const chunk of chunks) {
				controller.enqueue(chunk)
			}
			controller.close()
		},
	})

const responseOf = (stream: ReadableStream<Uint8Array>) =>
	new Response(stream, { headers: { 'content-type': 'text/plain' } })

describe('getResponseBody', () => {
	it('decodes a multi-byte UTF-8 character split across chunks', async () => {
		// "é" is 0xC3 0xA9 in UTF-8; split the two bytes across two chunks.
		const response = responseOf(
			streamOf(
				new Uint8Array([0x63, 0x61, 0x66, 0xc3]), // "caf" + first byte of é
				new Uint8Array([0xa9]), // second byte of é
			),
		)

		const body = await getResponseBody(response, undefined, undefined)

		expect(body).toBe('café')
	})

	it('decodes a 3-byte UTF-8 character split across three chunks', async () => {
		// "€" is 0xE2 0x82 0xAC in UTF-8.
		const response = responseOf(
			streamOf(
				new Uint8Array([0xe2]),
				new Uint8Array([0x82]),
				new Uint8Array([0xac]),
			),
		)

		const body = await getResponseBody(response, undefined, undefined)

		expect(body).toBe('€')
	})
})
