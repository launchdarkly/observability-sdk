import { describe, expect, it, vi } from 'vitest'
import {
	InMemorySpanExporter,
	SimpleSpanProcessor,
} from '@opentelemetry/sdk-trace-base'
import { SpanProcessor, WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import {
	InMemoryLogRecordExporter,
	LoggerProvider,
	LogRecordProcessor,
	SimpleLogRecordProcessor,
} from '@opentelemetry/sdk-logs'
import { SafeLogRecordProcessor, SafeSpanProcessor } from './SafeProcessors'

const throwingSpanProcessor = (): SpanProcessor => ({
	onStart: vi.fn(() => {
		throw new Error('onStart')
	}),
	onEnding: vi.fn(() => {
		throw new Error('onEnding')
	}),
	onEnd: vi.fn(() => {
		throw new Error('onEnd')
	}),
	forceFlush: vi.fn(() => Promise.reject(new Error('forceFlush'))),
	shutdown: vi.fn(() => {
		throw new Error('shutdown')
	}),
})

const throwingLogProcessor = (): LogRecordProcessor => ({
	onEmit: vi.fn(() => {
		throw new Error('onEmit')
	}),
	forceFlush: vi.fn(() => {
		throw new Error('forceFlush')
	}),
	shutdown: vi.fn(() => Promise.reject(new Error('shutdown'))),
})

describe('SafeSpanProcessor', () => {
	it('keeps exporting when the wrapped processor throws', async () => {
		const log = vi.fn()
		const delegate = throwingSpanProcessor()
		const exporter = new InMemorySpanExporter()
		const provider = new WebTracerProvider({
			spanProcessors: [
				new SafeSpanProcessor(delegate, log),
				new SimpleSpanProcessor(exporter),
			],
		})

		provider.getTracer('test').startSpan('span').end()
		await provider.forceFlush()

		expect(exporter.getFinishedSpans().map((s) => s.name)).toEqual(['span'])

		await provider.shutdown()

		expect(delegate.onStart).toHaveBeenCalledTimes(1)
		expect(delegate.onEnding).toHaveBeenCalledTimes(1)
		expect(delegate.onEnd).toHaveBeenCalledTimes(1)
		expect(delegate.forceFlush).toHaveBeenCalled()
		expect(delegate.shutdown).toHaveBeenCalledTimes(1)
		expect(log.mock.calls.map(([message]) => message)).toEqual(
			expect.arrayContaining([
				'custom processor threw in onStart:',
				'custom processor threw in onEnding:',
				'custom processor threw in onEnd:',
				'custom processor threw in forceFlush:',
				'custom processor threw in shutdown:',
			]),
		)
	})
})

describe('SafeLogRecordProcessor', () => {
	it('keeps exporting when the wrapped processor throws', async () => {
		const log = vi.fn()
		const delegate = throwingLogProcessor()
		const exporter = new InMemoryLogRecordExporter()
		const provider = new LoggerProvider({
			processors: [
				new SafeLogRecordProcessor(delegate, log),
				new SimpleLogRecordProcessor(exporter),
			],
		})

		provider.getLogger('test').emit({ body: 'hello' })
		await provider.forceFlush()

		expect(exporter.getFinishedLogRecords().map((r) => r.body)).toEqual([
			'hello',
		])

		await provider.shutdown()

		expect(delegate.onEmit).toHaveBeenCalledTimes(1)
		expect(delegate.forceFlush).toHaveBeenCalled()
		expect(delegate.shutdown).toHaveBeenCalledTimes(1)
		expect(log.mock.calls.map(([message]) => message)).toEqual(
			expect.arrayContaining([
				'custom processor threw in onEmit:',
				'custom processor threw in forceFlush:',
				'custom processor threw in shutdown:',
			]),
		)
	})
})
