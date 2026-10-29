import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
	InMemorySpanExporter,
	WebTracerProvider,
} from '@opentelemetry/sdk-trace-web'
import * as otel from '../client/otel'
import { CustomBatchSpanProcessor } from '../client/otel'
import {
	clearGlobalAttributes,
	getGlobalAttributes,
	removeGlobalAttributes,
	setGlobalAttributes,
} from './globalAttributes'
import { LDObserve } from './LDObserve'
import { ObserveSDK } from './observe'
import { CONTEXT_SCOPE_CONTEXT_KEYS_ATTR } from '../integrations/launchdarkly'

const baseOptions = {
	backendUrl: 'https://pub.highlight.io',
	otel: { otlpEndpoint: 'https://otel.highlight.io' },
	projectId: '1',
	sessionSecureId: 'test-session',
	environment: 'test',
}

const newFacade = () =>
	new (
		LDObserve as unknown as { constructor: new () => typeof LDObserve }
	).constructor()

describe('global attributes store', () => {
	beforeEach(() => clearGlobalAttributes())

	it('merges by key and removes keys', () => {
		setGlobalAttributes({ a: 'one', b: 2 })
		setGlobalAttributes({ b: 3, c: true })
		expect(getGlobalAttributes()).toEqual({ a: 'one', b: 3, c: true })

		removeGlobalAttributes(['a', 'missing'])
		expect(getGlobalAttributes()).toEqual({ b: 3, c: true })
	})

	it('ignores reserved session keys', () => {
		setGlobalAttributes({
			'highlight.session_id': 'spoofed',
			'session.id': 'spoofed',
			'highlight.record': false,
			kept: 'yes',
		})
		expect(getGlobalAttributes()).toEqual({ kept: 'yes' })
	})

	it('ignores values that are not OpenTelemetry attribute values', () => {
		setGlobalAttributes({
			undef: undefined,
			nul: null as unknown as string,
			obj: { nested: 1 } as unknown as string,
			mixed: [1, 'two'] as unknown as string[],
			list: ['a', 'b'],
			'': 'empty key',
		})
		expect(getGlobalAttributes()).toEqual({ list: ['a', 'b'] })
	})

	it('keeps an existing value when the new one is invalid', () => {
		setGlobalAttributes({ a: 'one' })
		setGlobalAttributes({ a: undefined })
		expect(getGlobalAttributes()).toEqual({ a: 'one' })
	})

	it('publishes immutable snapshots', () => {
		const list = ['a']
		setGlobalAttributes({ list })
		const before = getGlobalAttributes()
		list.push('b')
		setGlobalAttributes({ other: 1 })

		expect(Object.isFrozen(before)).toBe(true)
		expect(before).toEqual({ list: ['a'] })
		expect(getGlobalAttributes()).toEqual({ list: ['a'], other: 1 })
	})
})

describe('LDObserve global attributes', () => {
	beforeEach(() => clearGlobalAttributes())

	it('applies immediately before the sdk is initialized', () => {
		const facade = newFacade()
		facade.setAttributes({ tenant: 'acme' })
		expect(getGlobalAttributes()).toEqual({ tenant: 'acme' })

		facade.removeAttributes(['tenant'])
		expect(getGlobalAttributes()).toEqual({})
	})

	it('is never buffered with manualStart before start()', () => {
		const facade = newFacade()
		facade.load(new ObserveSDK(baseOptions), { ready: false })

		facade.setAttributes({ tenant: 'acme' })

		expect(getGlobalAttributes()).toEqual({ tenant: 'acme' })
		const buffered = (
			facade as unknown as { _callBuffer: Array<{ method: string }> }
		)._callBuffer
		expect(buffered).toHaveLength(0)
	})
})

describe('global attributes on telemetry', () => {
	let exporter: InMemorySpanExporter
	let provider: WebTracerProvider

	beforeEach(() => {
		clearGlobalAttributes()
		exporter = new InMemorySpanExporter()
		provider = new WebTracerProvider({
			spanProcessors: [new CustomBatchSpanProcessor(exporter)],
		})
	})

	afterEach(async () => {
		vi.restoreAllMocks()
		await provider.shutdown()
		clearGlobalAttributes()
	})

	const finishedSpans = async () => {
		await provider.forceFlush()
		return exporter.getFinishedSpans()
	}

	describe('span processor (automatic spans)', () => {
		it('fills missing keys; start and later attributes win', async () => {
			LDObserve.setAttributes({
				tenant: 'acme',
				'http.method': 'global',
				'http.url': 'global',
				'highlight.session_id': 'spoofed',
			})

			// Mirrors the fetch/XHR instrumentations: attributes passed at
			// start, then more set on the span after it has started.
			const span = provider
				.getTracer('test')
				.startSpan('HTTP GET', { attributes: { 'http.method': 'GET' } })
			span.setAttribute('http.url', 'https://example.com')
			span.end()

			const [exported] = await finishedSpans()
			expect(exported.attributes).toMatchObject({
				tenant: 'acme',
				'http.method': 'GET',
				'http.url': 'https://example.com',
			})
			expect(exported.attributes['highlight.session_id']).not.toBe(
				'spoofed',
			)
		})

		it('captures the values current when the span starts', async () => {
			LDObserve.setAttributes({ tenant: 'acme', plan: 'free' })
			const span = provider.getTracer('test').startSpan('long')
			LDObserve.setAttributes({ tenant: 'other' })
			LDObserve.removeAttributes(['plan'])
			span.end()
			provider.getTracer('test').startSpan('next').end()

			const [long, next] = await finishedSpans()
			expect(long.attributes).toMatchObject({
				tenant: 'acme',
				plan: 'free',
			})
			expect(next.attributes.tenant).toBe('other')
			expect(next.attributes.plan).toBeUndefined()
		})
	})

	describe('ObserveSDK', () => {
		let observe: ObserveSDK

		beforeEach(() => {
			observe = new ObserveSDK(baseOptions)
			vi.spyOn(otel, 'getTracer').mockReturnValue(
				provider.getTracer('test'),
			)
		})

		it('adds globals to manual spans; per-call attributes win', async () => {
			observe.setAttributes({ tenant: 'acme', step: 'global' })
			observe.startSpan(
				'checkout',
				{ attributes: { step: 'payment' } },
				() => {},
			)
			observe.startManualSpan('manual', (span) => {
				span.setAttribute('tenant', 'override')
				span.end()
			})

			const [checkout, manual] = await finishedSpans()
			expect(checkout.attributes).toMatchObject({
				tenant: 'acme',
				step: 'payment',
			})
			expect(manual.attributes.tenant).toBe('override')
		})

		it('keeps LD context keys over globals', async () => {
			observe.setLDContextKeyAttributes({ user: 'u1' })
			const key = `${CONTEXT_SCOPE_CONTEXT_KEYS_ATTR}.user`
			observe.setAttributes({ [key]: 'global' })
			observe.startSpan('span', () => {})

			const [span] = await finishedSpans()
			expect(span.attributes[key]).toBe('u1')
		})

		it('adds globals to errors; the error payload wins', async () => {
			observe.setAttributes({ tenant: 'acme', source: 'global' })
			observe.recordError(new Error('boom'), undefined, {
				orderId: 'o-1',
			})

			const [span] = await finishedSpans()
			expect(span.name).toBe('highlight.exception')
			expect(span.attributes).toMatchObject({
				tenant: 'acme',
				source: 'frontend',
				orderId: 'o-1',
			})
		})

		it('adds globals to logs; log fields and metadata win', async () => {
			observe.setAttributes({
				tenant: 'acme',
				orderId: 'global',
				[otel.ATTR_LOG_MESSAGE]: 'global',
			})
			observe.recordLog('checkout complete', 'info', { orderId: 'o-1' })

			const [span] = await finishedSpans()
			expect(span.name).toBe(otel.LOG_SPAN_NAME)
			expect(span.attributes.tenant).toBe('acme')
			const [event] = span.events
			expect(event.name).toBe('log')
			expect(event.attributes).toMatchObject({
				tenant: 'acme',
				orderId: 'o-1',
				[otel.ATTR_LOG_MESSAGE]: 'checkout complete',
				[otel.ATTR_LOG_SEVERITY]: 'info',
			})
		})

		it('adds globals to metrics; metric attributes win', () => {
			const add = vi.fn()
			const record = vi.fn()
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createCounter: vi.fn().mockReturnValue({ add }),
				createGauge: vi.fn().mockReturnValue({ record }),
				createHistogram: vi.fn().mockReturnValue({ record }),
				createUpDownCounter: vi.fn().mockReturnValue({ add }),
			} as any)

			observe.setAttributes({ tenant: 'acme', route: 'global' })
			observe.recordCount({
				name: 'clicks',
				value: 1,
				attributes: { route: '/cart' },
			})
			observe.recordGauge({ name: 'gauge', value: 1 })
			observe.recordHistogram({ name: 'histogram', value: 1 })
			observe.recordUpDownCounter({ name: 'updown', value: 1 })

			expect(add.mock.calls[0][1]).toMatchObject({
				tenant: 'acme',
				route: '/cart',
			})
			expect(add.mock.calls[0][1]['highlight.session_id']).toBeDefined()
			for (const [, attributes] of [
				...record.mock.calls,
				add.mock.calls[1],
			]) {
				expect(attributes).toMatchObject({
					tenant: 'acme',
					route: 'global',
				})
			}
		})
	})
})
