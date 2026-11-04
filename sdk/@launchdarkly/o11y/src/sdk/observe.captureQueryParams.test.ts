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
import { LD_PAGE_VIEW_SPAN_NAME } from '../integrations/launchdarkly'
import type { ObserveOptions } from '../client/types/observe'
import {
	DEFAULT_CAPTURED_QUERY_PARAMS,
	pickQueryParams,
} from '../client/utils/queryParams'

const LANDING_URL =
	'/?utm_campaign=spring&utm_source=email&utm_medium=newsletter&utm_term=shoes&utm_content=hero&other=x&email=a%40b.com'
const ALL_UTMS = {
	utm_campaign: 'spring',
	utm_source: 'email',
	utm_medium: 'newsletter',
	utm_term: 'shoes',
	utm_content: 'hero',
}

const newObserveSDK = (options: Partial<ObserveOptions> = {}) =>
	new ObserveSDK({
		backendUrl: 'https://pub.example.com',
		otel: { otlpEndpoint: 'https://otel.example.com' },
		projectId: '1',
		sessionSecureId: 'test-session',
		environment: 'test',
		...options,
	})

/** Loads the page at `url`, as the browser would before the SDK initializes. */
const landOn = (url: string) => window.history.replaceState({}, '', url)

/** A tracer provider wired to the SDK's span processor, exporting to memory. */
const setupTracing = (observe: ObserveSDK) => {
	const exporter = new InMemorySpanExporter()
	const processor = new CustomBatchSpanProcessor(exporter, {}, () => [
		observe.getLDContextKeyAttributes(),
		observe.getGlobalAttributes(),
	])
	const provider = new WebTracerProvider({ spanProcessors: [processor] })
	vi.spyOn(otel, 'getTracer').mockReturnValue(provider.getTracer('test'))
	const finished = async () => {
		await processor.forceFlush()
		return exporter.getFinishedSpans()
	}
	return { provider, finished }
}

const spansNamed = (spans: ReadableSpan[], name: string) =>
	spans.filter((s) => s.name === name)

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

const mockNavigationTiming = () => {
	const getEntriesByType = performance.getEntriesByType.bind(performance)
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
}

describe('pickQueryParams', () => {
	it('picks listed names, the first of repeated values, and skips empty values', () => {
		expect(
			pickQueryParams('?a=1&a=2&b=&c=3&d=4', ['a', 'b', 'c', 'missing']),
		).toEqual({ a: '1', c: '3' })
	})

	it('decodes values', () => {
		expect(pickQueryParams('?a=spring%20sale&b=x+y', ['a', 'b'])).toEqual({
			a: 'spring sale',
			b: 'x y',
		})
	})

	it('treats a trailing * as a prefix match', () => {
		expect(
			pickQueryParams('?utm_source=e&utm_x=1&utmz=2&gclid=g', ['utm_*']),
		).toEqual({ utm_source: 'e', utm_x: '1' })
	})

	it('returns nothing for an empty search or list', () => {
		expect(pickQueryParams('', ['a'])).toEqual({})
		expect(pickQueryParams('?a=1', [])).toEqual({})
	})
})

describe('ObserveSDK captureQueryParams', () => {
	beforeEach(() => {
		landOn(LANDING_URL)
	})

	afterEach(() => {
		vi.restoreAllMocks()
		landOn('/')
	})

	it('captures the default UTM list without configuration', () => {
		expect(DEFAULT_CAPTURED_QUERY_PARAMS).toEqual([
			'utm_source',
			'utm_medium',
			'utm_campaign',
			'utm_term',
			'utm_content',
		])
		expect(newObserveSDK().getGlobalAttributes()).toEqual(ALL_UTMS)
	})

	it('ignores parameters that are not listed', () => {
		const attributes = newObserveSDK().getGlobalAttributes()
		expect(attributes).not.toHaveProperty('other')
		expect(attributes).not.toHaveProperty('email')
	})

	it('is disabled by false or an empty list', () => {
		expect(
			newObserveSDK({ captureQueryParams: false }).getGlobalAttributes(),
		).toEqual({})
		expect(
			newObserveSDK({ captureQueryParams: [] }).getGlobalAttributes(),
		).toEqual({})
	})

	it('replaces the default list with a custom one', () => {
		expect(
			newObserveSDK({
				captureQueryParams: ['utm_campaign', 'other'],
			}).getGlobalAttributes(),
		).toEqual({ utm_campaign: 'spring', other: 'x' })
	})

	it('supports a utm_* prefix pattern', () => {
		landOn('/?utm_campaign=spring&utm_id=42&other=x')
		expect(
			newObserveSDK({
				captureQueryParams: ['utm_*'],
			}).getGlobalAttributes(),
		).toEqual({ utm_campaign: 'spring', utm_id: '42' })
	})

	it('captures nothing on a page load without the parameters', () => {
		landOn('/pricing?other=x')
		expect(newObserveSDK().getGlobalAttributes()).toEqual({})
	})

	it('ignores listed keys in reserved namespaces', () => {
		landOn('/?session.id=spoofed&utm_source=email')
		expect(
			newObserveSDK({
				captureQueryParams: ['session.id', 'utm_source'],
			}).getGlobalAttributes(),
		).toEqual({ utm_source: 'email' })
	})

	it('lets removeAttributes remove a captured value', () => {
		const observe = newObserveSDK()
		observe.removeAttributes(['utm_campaign'])
		expect(observe.getGlobalAttributes()).not.toHaveProperty('utm_campaign')
		expect(observe.getGlobalAttributes()).toHaveProperty(
			'utm_source',
			'email',
		)
	})

	it('lets setAttributes win over captured values', () => {
		const observe = newObserveSDK()
		observe.setAttributes({ utm_campaign: 'explicit' })
		expect(observe.getGlobalAttributes()).toMatchObject({
			utm_campaign: 'explicit',
			utm_source: 'email',
		})
	})

	it('lets setAttributes calls made before init win over captured values', () => {
		const ldObserve = new (Object.getPrototypeOf(LDObserve).constructor)()
		ldObserve.setAttributes({ utm_campaign: 'explicit' })

		const observe = newObserveSDK()
		ldObserve.applyBufferedAttributes(observe)

		expect(observe.getGlobalAttributes()).toMatchObject({
			utm_campaign: 'explicit',
			utm_source: 'email',
		})
	})

	it('writes nothing to localStorage, sessionStorage or cookies', () => {
		localStorage.clear()
		sessionStorage.clear()
		const cookies = document.cookie
		const setItem = vi.spyOn(Storage.prototype, 'setItem')

		newObserveSDK().recordLog('hello', 'info')

		expect(setItem).not.toHaveBeenCalled()
		expect(localStorage.length).toBe(0)
		expect(sessionStorage.length).toBe(0)
		expect(document.cookie).toBe(cookies)
	})

	describe('telemetry', () => {
		it('adds captured values to document load spans', async () => {
			const observe = newObserveSDK()
			const { provider, finished } = setupTracing(observe)
			mockNavigationTiming()
			const instrumentation = new DocumentLoadInstrumentation({
				enabled: false,
			})
			instrumentation.setTracerProvider(provider)
			instrumentation.enable()
			try {
				await vi.waitFor(async () => {
					const [span] = spansNamed(await finished(), 'documentLoad')
					expect(span?.attributes).toMatchObject(ALL_UTMS)
					expect(span.attributes).not.toHaveProperty('other')
				})
			} finally {
				instrumentation.disable()
			}
		})

		it('keeps captured values on page views, fetches, logs, errors and metrics after SPA navigation', async () => {
			const observe = newObserveSDK()
			const { provider, finished } = setupTracing(observe)
			const { add } = mockMeter()
			const locationChange = new LocationChangeInstrumentation()
			locationChange.setTracerProvider(provider)
			locationChange.enable()
			const fetchInstrumentation = new FetchInstrumentation({
				enabled: false,
			})
			fetchInstrumentation.setTracerProvider(provider)
			const fetch = (fetchInstrumentation as any)._patchConstructor()(
				vi.fn(async () => new Response('ok')),
			)
			try {
				// Two SPA navigations drop the query string from the URL.
				window.history.pushState({}, '', '/products')
				window.history.pushState({}, '', '/checkout')
				expect(window.location.search).toBe('')
				await fetch('https://api.example.com/cart')
				observe.recordLog('checkout opened', 'info')
				observe.recordError(new Error('boom'))
				observe.recordIncr({ name: 'checkout.views' })

				await vi.waitFor(async () => {
					const spans = await finished()
					const pageViews = spansNamed(spans, LD_PAGE_VIEW_SPAN_NAME)
					expect(pageViews).toHaveLength(2)
					const fetchSpan = spans.find(
						(s) =>
							s.attributes['http.url'] ===
							'https://api.example.com/cart',
					)
					const [log] = spansNamed(spans, otel.LOG_SPAN_NAME)
					const [error] = spansNamed(spans, 'highlight.exception')
					for (const span of [...pageViews, fetchSpan, log, error]) {
						expect(span?.attributes).toMatchObject(ALL_UTMS)
						expect(span!.attributes).not.toHaveProperty('other')
					}
					expect(log.events[0].attributes).toMatchObject(ALL_UTMS)
				})
				expect(add.mock.calls[0][1]).toMatchObject(ALL_UTMS)
				expect(add.mock.calls[0][1]).not.toHaveProperty('other')
			} finally {
				locationChange.disable()
			}
		})

		it('adds nothing when disabled', async () => {
			const observe = newObserveSDK({ captureQueryParams: false })
			const { finished } = setupTracing(observe)
			const { add } = mockMeter()

			observe.recordLog('hello', 'info')
			observe.recordIncr({ name: 'incr' })

			const [log] = spansNamed(await finished(), otel.LOG_SPAN_NAME)
			expect(log.attributes).not.toHaveProperty('utm_campaign')
			expect(add.mock.calls[0][1]).not.toHaveProperty('utm_campaign')
		})
	})

	it('with manualStart, adds captured values to telemetry buffered before start() and to spans created during start', async () => {
		// A fresh buffer, so the singleton other tests attach is untouched.
		const ldObserve = new (Object.getPrototypeOf(LDObserve).constructor)()
		ldObserve.recordLog('before start', 'info')

		let attributesAtSetup: Attributes | undefined
		vi.spyOn(otel, 'setupBrowserTracing').mockImplementation(((
			config: any,
		) => {
			// When tracing is set up, the document load span is created.
			attributesAtSetup = config.getGlobalAttributes?.()
		}) as any)
		const observe = newObserveSDK({ manualStart: true })
		vi.spyOn(observe as any, 'configureSampling').mockResolvedValue(
			undefined,
		)
		vi.spyOn(observe as any, 'setupListeners').mockImplementation(
			() => undefined,
		)
		const { finished } = setupTracing(observe)

		// The plugin's manualStart order: attach without starting.
		ldObserve.applyBufferedAttributes(observe)
		ldObserve.load(observe, { ready: false })
		// The user navigates before calling start().
		window.history.pushState({}, '', '/later')
		expect(await finished()).toHaveLength(0)

		await ldObserve.start()

		expect(attributesAtSetup).toEqual(ALL_UTMS)
		await vi.waitFor(async () => {
			const [log] = spansNamed(await finished(), otel.LOG_SPAN_NAME)
			expect(log?.attributes).toMatchObject(ALL_UTMS)
		})
	})
})
