import { beforeEach, describe, expect, it } from 'vitest'
import {
	InMemorySpanExporter,
	SimpleSpanProcessor,
} from '@opentelemetry/sdk-trace-base'
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import {
	InMemoryLogRecordExporter,
	LoggerProvider,
	SimpleLogRecordProcessor,
} from '@opentelemetry/sdk-logs'
import { GlobalAttributes } from './GlobalAttributes'
import { GlobalAttributesSpanProcessor } from './GlobalAttributesSpanProcessor'
import { GlobalAttributesLogRecordProcessor } from './GlobalAttributesLogRecordProcessor'

describe('GlobalAttributesSpanProcessor', () => {
	let store: GlobalAttributes
	let exporter: InMemorySpanExporter
	let provider: WebTracerProvider

	beforeEach(() => {
		store = new GlobalAttributes()
		exporter = new InMemorySpanExporter()
		provider = new WebTracerProvider({
			spanProcessors: [
				new GlobalAttributesSpanProcessor(store),
				new SimpleSpanProcessor(exporter),
			],
		})
	})

	it('fills global attributes without overriding span attributes', () => {
		store.setAttributes({ 'app.tier': 'gold', shared: 'global' })

		provider
			.getTracer('test')
			.startSpan('span', { attributes: { shared: 'call' } })
			.end()

		const [span] = exporter.getFinishedSpans()
		expect(span.attributes['app.tier']).toBe('gold')
		expect(span.attributes.shared).toBe('call')
	})

	it('uses the values current at span start', () => {
		store.setAttributes({ phase: 'start' })
		const span = provider.getTracer('test').startSpan('span')
		store.setAttributes({ phase: 'later', added: true })
		span.end()

		const [finished] = exporter.getFinishedSpans()
		expect(finished.attributes.phase).toBe('start')
		expect(finished.attributes.added).toBeUndefined()
	})

	it('stops applying removed attributes', () => {
		store.setAttributes({ a: 1, b: 2 })
		store.removeAttributes(['a'])

		provider.getTracer('test').startSpan('span').end()

		const [span] = exporter.getFinishedSpans()
		expect(span.attributes.a).toBeUndefined()
		expect(span.attributes.b).toBe(2)
	})
})

describe('GlobalAttributesLogRecordProcessor', () => {
	let store: GlobalAttributes
	let exporter: InMemoryLogRecordExporter
	let provider: LoggerProvider

	beforeEach(() => {
		store = new GlobalAttributes()
		exporter = new InMemoryLogRecordExporter()
		provider = new LoggerProvider({
			processors: [
				new GlobalAttributesLogRecordProcessor(store),
				new SimpleLogRecordProcessor(exporter),
			],
		})
	})

	it('fills global attributes without overriding record attributes', () => {
		store.setAttributes({ 'app.tier': 'gold', shared: 'global' })

		provider.getLogger('test').emit({
			body: 'hello',
			attributes: { shared: 'call' },
		})

		const [record] = exporter.getFinishedLogRecords()
		expect(record.attributes['app.tier']).toBe('gold')
		expect(record.attributes.shared).toBe('call')
	})
})
