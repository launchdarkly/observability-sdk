import { describe, it, expect, vi } from 'vitest'
import { CustomBatchSpanProcessor } from './CustomBatchSpanProcessor'
import { RECORD_ATTRIBUTE } from '@launchdarkly/observability-shared'
import { InMemorySpanExporter } from '@opentelemetry/sdk-trace-base'
import { Tracer, WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'

describe('CustomBatchSpanProcessor', () => {
	let exporter: InMemorySpanExporter
	let processor: CustomBatchSpanProcessor
	let provider: WebTracerProvider
	let tracer: Tracer

	beforeEach(() => {
		exporter = new InMemorySpanExporter()
		processor = new CustomBatchSpanProcessor(exporter)
		provider = new WebTracerProvider({
			spanProcessors: [processor],
		})
		tracer = provider.getTracer('test')
	})

	it('does not export spans with RECORD_ATTRIBUTE=false', async () => {
		const span = tracer.startSpan('span')
		span.setAttribute(RECORD_ATTRIBUTE, false)
		span.end()

		await processor.forceFlush()
		const finished = exporter.getFinishedSpans()
		expect(finished.length).toBe(0)
	})

	it('exports spans with RECORD_ATTRIBUTE true or undefined', async () => {
		const span1 = tracer.startSpan('span1')
		span1.setAttribute(RECORD_ATTRIBUTE, true)
		span1.end()

		const span2 = tracer.startSpan('span2')
		span2.end()

		await processor.forceFlush()
		const finished = exporter.getFinishedSpans()
		expect(finished.length).toEqual(2)
		expect(finished.map((s) => s.name)).toContain('span1')
		expect(finished.map((s) => s.name)).toContain('span2')
	})

	it('logs when debug is enabled and span is not recorded', async () => {
		const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
		processor = new CustomBatchSpanProcessor(exporter, {
			debug: true,
		})
		const span = tracer.startSpan('span')
		span.setAttribute(RECORD_ATTRIBUTE, false)
		span.end()
		processor.onEnd(span as unknown as ReadableSpan)

		expect(logSpy).toHaveBeenCalledWith(
			'[CustomBatchSpanProcessor]',
			'span set to not record - skipping',
			'span',
		)
		logSpy.mockRestore()
	})
})
