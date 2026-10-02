import { describe, it, expect, beforeEach, vi } from 'vitest'
import { wrapTracer } from './LDTracer'
import { NOOP_SPAN_OPS } from '../sdk/withSpan'
import { _LDObserve } from '../sdk/LDObserve'
import { ObservabilityClient } from '../client/ObservabilityClient'

describe('wrapTracer', () => {
	it('routes startSpan through SpanOps', () => {
		const startSpan = vi.fn(() => ({ id: 'span' }))
		const tracer = wrapTracer({
			...NOOP_SPAN_OPS,
			startSpan,
		})

		tracer.startSpan('test-span', { attributes: { a: 1 } })

		expect(startSpan).toHaveBeenCalledWith(
			'test-span',
			{ attributes: { a: 1 } },
			undefined,
		)
	})

	it('routes startActiveSpan overloads through SpanOps', () => {
		const startActiveSpan = vi.fn((_name, fn) => fn({ id: 'span' }))
		const tracer = wrapTracer({
			...NOOP_SPAN_OPS,
			startActiveSpan,
		})

		tracer.startActiveSpan('fn-only', (span) => span)
		tracer.startActiveSpan('with-options', { root: true }, (span) => span)
		tracer.startActiveSpan(
			'with-context',
			{ root: true },
			{} as any,
			(span) => span,
		)

		expect(startActiveSpan).toHaveBeenCalledTimes(3)
		expect(startActiveSpan).toHaveBeenNthCalledWith(
			1,
			'fn-only',
			expect.any(Function),
			undefined,
			undefined,
		)
		expect(startActiveSpan).toHaveBeenNthCalledWith(
			2,
			'with-options',
			expect.any(Function),
			{ root: true },
			undefined,
		)
		expect(startActiveSpan).toHaveBeenNthCalledWith(
			3,
			'with-context',
			expect.any(Function),
			{ root: true },
			{},
		)
	})
})

describe('LDObserve getTracer span guards', () => {
	beforeEach(() => {
		_LDObserve._resetForTesting()
	})

	it('returns no-op spans from getTracer().startSpan before init', () => {
		const span = _LDObserve.getTracer().startSpan('pre-init')

		expect(span.isRecording()).toBe(false)
		expect(span.spanContext().traceId).toBe(
			'00000000000000000000000000000000',
		)
	})

	it('runs getTracer().startActiveSpan with no-op span before init', () => {
		let callbackSpan: any = null

		const result = _LDObserve
			.getTracer()
			.startActiveSpan('pre-init', (span) => {
				callbackSpan = span
				return 'ok'
			})

		expect(result).toBe('ok')
		expect(callbackSpan.isRecording()).toBe(false)
	})

	it('returns a no-op tracer from getTracer() when disableTraces is set', async () => {
		const client = new ObservabilityClient('sdkKey', {
			disableTraces: true,
		})

		_LDObserve._init(client)

		await vi.waitFor(
			() => {
				expect(_LDObserve.isInitialized()).toBe(true)
			},
			{ timeout: 2000 },
		)

		const span = _LDObserve.getTracer().startSpan('disabled')
		expect(span.isRecording()).toBe(false)

		let callbackSpan: any = null
		_LDObserve.getTracer().startActiveSpan('disabled', (s) => {
			callbackSpan = s
			return 'done'
		})
		expect(callbackSpan.isRecording()).toBe(false)
	})

	it('still initializes tracing when disableTraces is set', async () => {
		const client = new ObservabilityClient('sdkKey', {
			disableTraces: true,
		})

		_LDObserve._init(client)

		await vi.waitFor(
			() => {
				expect(_LDObserve.isInitialized()).toBe(true)
			},
			{ timeout: 2000 },
		)

		expect(client.getIsInitialized()).toBe(true)
	})
})
