import { ROOT_CONTEXT } from '@opentelemetry/api'
import type {
	ReadableSpan,
	Span,
	SpanProcessor,
} from '@opentelemetry/sdk-trace-base'
import { describe, expect, it, vi } from 'vitest'
import { ShouldExportSpanProcessor } from './ShouldExportSpanProcessor'

function fakeProcessor() {
	return {
		onStart: vi.fn(),
		onEnd: vi.fn(),
		forceFlush: vi.fn(() => Promise.resolve()),
		shutdown: vi.fn(() => Promise.resolve()),
	} satisfies SpanProcessor
}

const span = (target: string) =>
	({ attributes: { 'http.target': target } }) as unknown as ReadableSpan

describe('ShouldExportSpanProcessor', () => {
	const predicate = (s: ReadableSpan) =>
		s.attributes['http.target'] !== '/_vercel/ping'

	it('forwards spans the predicate keeps', () => {
		const delegate = fakeProcessor()
		new ShouldExportSpanProcessor(delegate, predicate).onEnd(
			span('/api/real'),
		)
		expect(delegate.onEnd).toHaveBeenCalledTimes(1)
	})

	it('drops spans the predicate rejects', () => {
		const delegate = fakeProcessor()
		new ShouldExportSpanProcessor(delegate, predicate).onEnd(
			span('/_vercel/ping'),
		)
		expect(delegate.onEnd).not.toHaveBeenCalled()
	})

	it('keeps the span when the predicate throws', () => {
		const delegate = fakeProcessor()
		new ShouldExportSpanProcessor(delegate, () => {
			throw new Error('boom')
		}).onEnd(span('/api/real'))
		expect(delegate.onEnd).toHaveBeenCalledTimes(1)
	})

	it('passes onStart, forceFlush and shutdown through', async () => {
		const delegate = fakeProcessor()
		const processor = new ShouldExportSpanProcessor(delegate, predicate)
		processor.onStart({} as Span, ROOT_CONTEXT)
		await processor.forceFlush()
		await processor.shutdown()
		expect(delegate.onStart).toHaveBeenCalledTimes(1)
		expect(delegate.forceFlush).toHaveBeenCalledTimes(1)
		expect(delegate.shutdown).toHaveBeenCalledTimes(1)
	})
})
