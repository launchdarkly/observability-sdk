import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Attributes } from '@opentelemetry/api'
import {
	InMemorySpanExporter,
	ReadableSpan,
	WebTracerProvider,
} from '@opentelemetry/sdk-trace-web'
import { DocumentLoadInstrumentation } from '@opentelemetry/instrumentation-document-load'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { ObserveSDK } from './observe'
import { LDObserve } from './LDObserve'
import * as otel from '../client/otel'
import { CustomBatchSpanProcessor } from '../client/otel'
import { LocationChangeInstrumentation } from '../client/otel/location-change'
import { UserInteractionInstrumentation } from '../client/otel/user-interaction'
import { LD_PAGE_VIEW_SPAN_NAME } from '../integrations/launchdarkly'

const newObserveSDK = () =>
	new ObserveSDK({
		backendUrl: 'https://pub.example.com',
		otel: { otlpEndpoint: 'https://otel.example.com' },
		projectId: '1',
		sessionSecureId: 'test-session',
		environment: 'test',
	})

/**
 * A tracer provider wired to the SDK's span processor exactly as
 * `setupBrowserTracing` does, exporting to memory instead of the network.
 */
const setupTracing = (observe: ObserveSDK) => {
	const exporter = new InMemorySpanExporter()
	const processor = new CustomBatchSpanProcessor(exporter, {}, () => [
		observe.getLDContextKeyAttributes(),
		observe.getGlobalAttributes(),
	])
	const provider = new WebTracerProvider({ spanProcessors: [processor] })
	const tracer = provider.getTracer('test')
	vi.spyOn(otel, 'getTracer').mockReturnValue(tracer)
	const finished = async () => {
		await processor.forceFlush()
		return exporter.getFinishedSpans()
	}
	return { provider, tracer, finished }
}

const spanNamed = (spans: ReadableSpan[], name: string) => {
	const span = spans.find((s) => s.name === name)
	expect(span, `span ${name}`).toBeDefined()
	return span!
}

describe('ObserveSDK global attributes', () => {
	let observe: ObserveSDK

	beforeEach(() => {
		observe = newObserveSDK()
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	describe('setAttributes / removeAttributes', () => {
		it('merges calls and exposes a frozen snapshot', () => {
			observe.setAttributes({ utm_campaign: 'spring', step: 1 })
			observe.setAttributes({ utm_source: 'email', flags: ['a', 'b'] })

			const snapshot = observe.getGlobalAttributes()
			expect(snapshot).toEqual({
				utm_campaign: 'spring',
				step: 1,
				utm_source: 'email',
				flags: ['a', 'b'],
			})
			expect(Object.isFrozen(snapshot)).toBe(true)
			// Unchanged until the next call, so readers can hold on to it.
			expect(observe.getGlobalAttributes()).toBe(snapshot)
		})

		it('removes keys with removeAttributes or a null/undefined value', () => {
			observe.setAttributes({ a: '1', b: '2', c: '3', d: '4' })
			observe.removeAttributes(['a'])
			observe.setAttributes({ b: null, c: undefined })

			expect(observe.getGlobalAttributes()).toEqual({ d: '4' })
		})

		it('ignores reserved keys and unsupported values', () => {
			observe.setAttributes({
				'highlight.session_id': 'spoofed',
				'launchdarkly.anything': 'x',
				'session.id': 'x',
				'context.contextKeys.user': 'x',
				nested: { a: 1 } as any,
				ok: 'yes',
			})

			expect(observe.getGlobalAttributes()).toEqual({ ok: 'yes' })
		})
	})

	describe('spans', () => {
		it('adds attributes and context keys to spans from startSpan and startManualSpan', async () => {
			const { finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })

			observe.startSpan('auto-ended', () => {})
			observe.startManualSpan('manual', (span) => span.end())

			const spans = await finished()
			for (const name of ['auto-ended', 'manual']) {
				expect(spanNamed(spans, name).attributes).toMatchObject({
					utm_campaign: 'spring',
					'context.contextKeys.user': 'alice',
					'highlight.session_id': expect.any(String),
				})
			}
		})

		it('lets attributes the span is started with win', async () => {
			const { tracer, finished } = setupTracing(observe)
			observe.setAttributes({ shared: 'global', other: 'global' })
			observe.setLDContextKeyAttributes({ user: 'alice' })

			tracer
				.startSpan('with-attributes', {
					attributes: { shared: 'per-call' },
				})
				.end()

			const [span] = await finished()
			expect(span.attributes).toMatchObject({
				shared: 'per-call',
				other: 'global',
				'context.contextKeys.user': 'alice',
			})
		})

		it('stops adding removed attributes to later spans', async () => {
			const { tracer, finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			tracer.startSpan('before').end()
			observe.removeAttributes(['utm_campaign'])
			tracer.startSpan('after').end()

			const spans = await finished()
			expect(spanNamed(spans, 'before').attributes.utm_campaign).toBe(
				'spring',
			)
			expect(spanNamed(spans, 'after').attributes).not.toHaveProperty(
				'utm_campaign',
			)
		})

		it('adds attributes and context keys to page view spans', async () => {
			const { provider, finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })
			const instrumentation = new LocationChangeInstrumentation()
			instrumentation.setTracerProvider(provider)
			instrumentation.enable()
			try {
				window.history.pushState({}, '', '/next-page')
				await vi.waitFor(async () => {
					const spans = await finished()
					expect(
						spanNamed(spans, LD_PAGE_VIEW_SPAN_NAME).attributes,
					).toMatchObject({
						utm_campaign: 'spring',
						'context.contextKeys.user': 'alice',
					})
				})
			} finally {
				instrumentation.disable()
			}
		})

		it('adds attributes and context keys to click spans', async () => {
			const { provider, finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })
			const instrumentation = new UserInteractionInstrumentation()
			instrumentation.setTracerProvider(provider)
			instrumentation.enable()
			const button = document.createElement('button')
			document.body.appendChild(button)
			try {
				button.addEventListener('click', () => {})
				button.click()

				const spans = await finished()
				expect(spanNamed(spans, 'click').attributes).toMatchObject({
					utm_campaign: 'spring',
					'context.contextKeys.user': 'alice',
				})
			} finally {
				instrumentation.disable()
				button.remove()
			}
		})

		it('adds attributes and context keys to fetch spans', async () => {
			const { provider, finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })
			const instrumentation = new FetchInstrumentation({ enabled: false })
			instrumentation.setTracerProvider(provider)
			// The instrumentation refuses to patch the global fetch under Node,
			// so patch a stub fetch with it directly.
			const fetch = (instrumentation as any)._patchConstructor()(
				vi.fn(async () => new Response('ok')),
			)

			await fetch('https://api.example.com/data')

			await vi.waitFor(async () => {
				const spans = await finished()
				expect(spans).toHaveLength(1)
				expect(spans[0].attributes).toMatchObject({
					'http.url': 'https://api.example.com/data',
					utm_campaign: 'spring',
					'context.contextKeys.user': 'alice',
				})
			})
		})

		it('adds attributes and context keys to document load spans', async () => {
			const { provider, finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })
			// jsdom records no navigation timing; provide one.
			const getEntriesByType =
				performance.getEntriesByType.bind(performance)
			vi.spyOn(performance, 'getEntriesByType').mockImplementation(
				(type: string) =>
					type === 'navigation'
						? ([
								{
									fetchStart: 1,
									responseEnd: 5,
									domInteractive: 6,
									domContentLoadedEventEnd: 7,
									domComplete: 8,
									loadEventStart: 9,
									loadEventEnd: 10,
								},
							] as any)
						: getEntriesByType(type),
			)
			const instrumentation = new DocumentLoadInstrumentation({
				enabled: false,
			})
			instrumentation.setTracerProvider(provider)
			instrumentation.enable()
			try {
				await vi.waitFor(async () => {
					const spans = await finished()
					for (const name of ['documentLoad', 'documentFetch']) {
						expect(spanNamed(spans, name).attributes).toMatchObject(
							{
								utm_campaign: 'spring',
								'context.contextKeys.user': 'alice',
							},
						)
					}
				})
			} finally {
				instrumentation.disable()
			}
		})

		it('adds attributes and context keys to log and error spans', async () => {
			const { finished } = setupTracing(observe)
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.setLDContextKeyAttributes({ user: 'alice' })

			observe.recordLog('hello', 'info', { step: 'signup' })
			observe.recordError(new Error('boom'))

			const spans = await finished()
			const log = spanNamed(spans, otel.LOG_SPAN_NAME)
			expect(log.attributes).toMatchObject({ utm_campaign: 'spring' })
			expect(log.events[0].attributes).toMatchObject({
				utm_campaign: 'spring',
				'context.contextKeys.user': 'alice',
				step: 'signup',
			})
			expect(
				spanNamed(spans, 'highlight.exception').attributes,
			).toMatchObject({
				utm_campaign: 'spring',
				'context.contextKeys.user': 'alice',
			})
		})

		it('lets log metadata win over global attributes', async () => {
			const { finished } = setupTracing(observe)
			observe.setAttributes({ step: 'global', 'log.severity': 'x' })

			observe.recordLog('hello', 'warn', { step: 'per-call' })

			const log = spanNamed(await finished(), otel.LOG_SPAN_NAME)
			expect(log.events[0].attributes).toMatchObject({
				step: 'per-call',
				'log.severity': 'warn',
			})
		})
	})

	describe('metrics', () => {
		const mockMeter = () => {
			const record = vi.fn()
			const add = vi.fn()
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createGauge: () => ({ record }),
				createHistogram: () => ({ record }),
				createCounter: () => ({ add }),
				createUpDownCounter: () => ({ add }),
			} as any)
			return { record, add }
		}

		it('adds attributes and context keys to every metric type, per-call attributes winning', () => {
			const { record, add } = mockMeter()
			observe.setAttributes({ utm_campaign: 'spring', shared: 'global' })
			observe.setLDContextKeyAttributes({ user: 'alice' })
			const attributes: Attributes = { shared: 'per-call' }

			observe.recordGauge({ name: 'gauge', value: 1, attributes })
			observe.recordHistogram({ name: 'histogram', value: 1, attributes })
			observe.recordCount({ name: 'count', value: 1, attributes })
			observe.recordIncr({ name: 'incr', attributes })
			observe.recordUpDownCounter({
				name: 'updown',
				value: 1,
				attributes,
			})

			const expected = {
				utm_campaign: 'spring',
				shared: 'per-call',
				'context.contextKeys.user': 'alice',
				'highlight.session_id': expect.any(String),
			}
			expect(record).toHaveBeenCalledTimes(2)
			expect(add).toHaveBeenCalledTimes(3)
			for (const call of [...record.mock.calls, ...add.mock.calls]) {
				expect(call[1]).toEqual(expected)
			}
		})

		it('stops adding removed attributes to metrics', () => {
			const { add } = mockMeter()
			observe.setAttributes({ utm_campaign: 'spring' })
			observe.removeAttributes(['utm_campaign'])

			observe.recordIncr({ name: 'incr' })

			expect(add.mock.calls[0][1]).not.toHaveProperty('utm_campaign')
		})
	})
})

describe('LDObserve.setAttributes buffering', () => {
	it('applies calls made before the SDK attaches, before it starts', () => {
		// A fresh buffer, so the singleton other tests attach is untouched.
		const ldObserve = new (Object.getPrototypeOf(LDObserve).constructor)()
		ldObserve.setAttributes({ utm_campaign: 'spring', step: 1 })
		ldObserve.removeAttributes(['step'])
		ldObserve.recordLog('buffered', 'info')

		const observe = newObserveSDK()
		const recordLog = vi.spyOn(observe, 'recordLog')
		ldObserve.load(observe, { ready: false })

		expect(observe.getGlobalAttributes()).toEqual({
			utm_campaign: 'spring',
		})
		// Telemetry still waits for start().
		expect(recordLog).not.toHaveBeenCalled()
	})

	it('applies calls directly once the SDK is attached', () => {
		const ldObserve = new (Object.getPrototypeOf(LDObserve).constructor)()
		const observe = newObserveSDK()
		ldObserve.load(observe, { ready: false })

		ldObserve.setAttributes({ utm_campaign: 'spring' })

		expect(observe.getGlobalAttributes()).toEqual({
			utm_campaign: 'spring',
		})
	})
})
