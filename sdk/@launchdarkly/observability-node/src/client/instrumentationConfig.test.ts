import type { Instrumentation } from '@opentelemetry/instrumentation'
import { describe, expect, it, vi } from 'vitest'
import { applyInstrumentationConfig } from './instrumentationConfig.js'

function fakeInstrumentation(
	instrumentationName: string,
	config: Record<string, unknown> = {},
) {
	let current: Record<string, unknown> = { enabled: true, ...config }
	return {
		instrumentationName,
		instrumentationVersion: '0.0.0',
		getConfig: () => current,
		setConfig: vi.fn((c: Record<string, unknown>) => {
			current = c
		}),
		enable: vi.fn(),
		disable: vi.fn(),
		setTracerProvider: vi.fn(),
		setMeterProvider: vi.fn(),
	} as unknown as Instrumentation & {
		setConfig: ReturnType<typeof vi.fn>
		disable: ReturnType<typeof vi.fn>
	}
}

describe('applyInstrumentationConfig', () => {
	it('returns instrumentations unchanged without config', () => {
		const http = fakeInstrumentation('@opentelemetry/instrumentation-http')
		expect(applyInstrumentationConfig([http], undefined)).toEqual([http])
		expect(http.setConfig).not.toHaveBeenCalled()
	})

	it('merges config over the existing defaults', () => {
		const http = fakeInstrumentation(
			'@opentelemetry/instrumentation-http',
			{
				disableOutgoingRequestInstrumentation: true,
			},
		)
		const hook = () => true
		const result = applyInstrumentationConfig([http], {
			'@opentelemetry/instrumentation-http': {
				ignoreIncomingRequestHook: hook,
			},
		})
		expect(result).toEqual([http])
		expect(http.getConfig()).toEqual({
			enabled: true,
			disableOutgoingRequestInstrumentation: true,
			ignoreIncomingRequestHook: hook,
		})
	})

	it('disables and drops instrumentations with enabled: false', () => {
		const http = fakeInstrumentation('@opentelemetry/instrumentation-http')
		const dns = fakeInstrumentation('@opentelemetry/instrumentation-dns')
		const result = applyInstrumentationConfig([http, dns], {
			'@opentelemetry/instrumentation-dns': { enabled: false },
		})
		expect(result).toEqual([http])
		expect(dns.disable).toHaveBeenCalledOnce()
		expect(http.disable).not.toHaveBeenCalled()
	})

	it('warns about config for an unregistered instrumentation', () => {
		const warn = vi.fn()
		const http = fakeInstrumentation('@opentelemetry/instrumentation-http')
		const result = applyInstrumentationConfig(
			[http],
			{ '@opentelemetry/instrumentation-fs': { enabled: true } },
			warn,
		)
		expect(result).toEqual([http])
		expect(warn).toHaveBeenCalledOnce()
		expect(warn.mock.calls[0][0]).toContain(
			'@opentelemetry/instrumentation-fs',
		)
	})

	it('does not warn when disabling an unregistered instrumentation', () => {
		const warn = vi.fn()
		applyInstrumentationConfig(
			[],
			{ '@opentelemetry/instrumentation-fs': { enabled: false } },
			warn,
		)
		expect(warn).not.toHaveBeenCalled()
	})
})
