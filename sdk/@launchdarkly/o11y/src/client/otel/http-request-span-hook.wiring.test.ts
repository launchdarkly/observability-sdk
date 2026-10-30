import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// FetchInstrumentation.enable() is a no-op under Node (which vitest reports),
// so capture the config the SDK hands each instrumentation and drive its
// applyCustomAttributesOnSpan callback directly.
const captured = vi.hoisted(() => ({
	fetch: undefined as any,
	xhr: undefined as any,
}))

vi.mock('@opentelemetry/instrumentation-fetch', () => ({
	FetchInstrumentation: class {
		constructor(config: unknown) {
			captured.fetch = config
		}
		setTracerProvider() {}
		setMeterProvider() {}
		setConfig() {}
		getConfig() {
			return { enabled: false }
		}
		enable() {}
		disable() {}
	},
}))
vi.mock('@opentelemetry/instrumentation-xml-http-request', () => ({
	XMLHttpRequestInstrumentation: class {
		constructor(config: unknown) {
			captured.xhr = config
		}
		setTracerProvider() {}
		setMeterProvider() {}
		setConfig() {}
		getConfig() {
			return { enabled: false }
		}
		enable() {}
		disable() {}
	},
}))

import { setupBrowserTracing, shutdown } from './index'
import type { ExportSampler } from './sampling/ExportSampler'
import type { HttpSpanRequest } from '../types/client'

const sampler = {
	shouldSample: () => ({ sample: true }),
	isSamplingEnabled: () => false,
} as unknown as ExportSampler

const ROUTE = '/api/v2/projects/{projectKey}'

const makeSpan = (url: string, method: string) => {
	const span: any = {
		name: `HTTP ${method}`,
		attributes: { 'http.url': url, 'http.method': method },
		updateName(name: string) {
			span.name = name
			return span
		},
		setAttributes(attrs: Record<string, unknown>) {
			Object.assign(span.attributes, attrs)
			return span
		},
	}
	return span
}

describe('onHttpRequestSpan wiring', () => {
	const calls: HttpSpanRequest[] = []

	beforeEach(() => {
		calls.length = 0
		captured.fetch = captured.xhr = undefined
		setupBrowserTracing(
			{
				projectId: 'p',
				sessionSecureId: 's',
				otlpEndpoint: 'https://otel.invalid',
				backendUrl: 'https://pub.invalid',
				networkRecordingOptions: { enabled: true },
				onHttpRequestSpan: (span, request) => {
					calls.push(request)
					span.updateName(`${request.method} ${ROUTE}`)
					span.setAttributes({
						'http.route': ROUTE,
						'url.template': ROUTE,
						'launchdarkly.operation.name': ROUTE,
					})
				},
			},
			sampler,
		)
	})

	afterEach(async () => {
		await shutdown()
	})

	it('templates the auto fetch span and keeps the concrete url', () => {
		const url = 'https://app.example.com/api/v2/projects/default'
		const span = makeSpan(url, 'GET')
		captured.fetch.applyCustomAttributesOnSpan(
			span,
			{ method: 'GET' },
			new Response('{}', { status: 200 }),
		)
		expect(calls).toEqual([{ type: 'fetch', method: 'GET', url }])
		expect(span.name).toBe(`GET ${ROUTE}`)
		expect(span.attributes).toMatchObject({
			'http.route': ROUTE,
			'url.template': ROUTE,
			'launchdarkly.operation.name': ROUTE,
			'http.url': url,
		})
	})

	it('still templates fetch spans that failed', () => {
		const span = makeSpan(
			'https://app.example.com/api/v2/projects/x',
			'GET',
		)
		captured.fetch.applyCustomAttributesOnSpan(
			span,
			{ method: 'GET' },
			{ status: 0, message: 'Failed to fetch' },
		)
		expect(span.name).toBe(`GET ${ROUTE}`)
	})

	it('templates the auto xhr span', () => {
		const url = 'https://app.example.com/api/v2/projects/default'
		const span = makeSpan(url, 'GET')
		captured.xhr.applyCustomAttributesOnSpan(span, {
			_method: 'GET',
			_url: url,
			responseType: '',
			getAllResponseHeaders: () => '',
		})
		expect(calls).toEqual([{ type: 'xhr', method: 'GET', url }])
		expect(span.name).toBe(`GET ${ROUTE}`)
	})
})
