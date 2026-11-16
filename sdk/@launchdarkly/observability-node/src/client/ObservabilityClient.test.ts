import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions'
import { ObservabilityClient } from './ObservabilityClient.js'
import { ShouldExportSpanProcessor } from '../otel/ShouldExportSpanProcessor.js'
import { describe, expect, it, vi } from 'vitest'

describe('client', () => {
	it('includes process information in the resource', () => {
		const client = new ObservabilityClient('abc123')

		const sdk = client.otel
		const resource = sdk['_resource']

		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_NAME
			],
		).toEqual('nodejs')
		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_DESCRIPTION
			],
		).toEqual('Node.js')
		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_VERSION
			],
		).toBeDefined()
	})

	it('includes service config if provided', () => {
		const client = new ObservabilityClient('abc123', {
			serviceName: 'my-service',
			serviceVersion: 'abc123',
		})

		const sdk = client.otel
		const resource = sdk['_resource']

		expect(
			resource.attributes[SemanticResourceAttributes.SERVICE_NAME],
		).toEqual('my-service')
		expect(
			resource.attributes[SemanticResourceAttributes.SERVICE_VERSION],
		).toEqual('abc123')
	})

	describe('instrumentationConfig', () => {
		const instrumentation = (client: ObservabilityClient, name: string) =>
			(client.otel['_instrumentations'] as any[]).find(
				(i) => i.instrumentationName === name,
			)

		it('applies user config to the registered http instrumentation', () => {
			const ignoreIncomingRequestHook = vi.fn(() => true)
			const client = new ObservabilityClient('abc123', {
				instrumentationConfig: {
					'@opentelemetry/instrumentation-http': {
						ignoreIncomingRequestHook,
					},
				},
			})

			const config = instrumentation(
				client,
				'@opentelemetry/instrumentation-http',
			).getConfig()
			expect(config.ignoreIncomingRequestHook).toBe(
				ignoreIncomingRequestHook,
			)
		})

		it('keeps the SDK pino logHook and runs it before the user hook', () => {
			const userLogHook = vi.fn((_span: any, record: any) => {
				expect(record.fromSpan).toBe('yes')
			})
			const client = new ObservabilityClient('abc123', {
				instrumentationConfig: {
					'@opentelemetry/instrumentation-pino': {
						logHook: userLogHook,
					},
				},
			})

			const record: Record<string, unknown> = {}
			instrumentation(client, '@opentelemetry/instrumentation-pino')
				.getConfig()
				.logHook({ attributes: { fromSpan: 'yes' } }, record, 30)
			expect(record.fromSpan).toBe('yes')
			expect(userLogHook).toHaveBeenCalledTimes(1)
		})

		it('runs the pg redaction hook before the user responseHook', () => {
			const seen: Record<string, unknown> = {}
			const span = {
				setAttribute: (k: string, v: unknown) => {
					seen[k] = v
				},
			}
			const userResponseHook = vi.fn(() => {
				expect(seen['db.statement']).toBe('[REDACTED]')
			})
			const client = new ObservabilityClient('abc123', {
				disablePgInstrumentationAttributes: true,
				instrumentationConfig: {
					'@opentelemetry/instrumentation-pg': {
						responseHook: userResponseHook,
					},
				},
			})

			const config = instrumentation(
				client,
				'@opentelemetry/instrumentation-pg',
			).getConfig()
			expect(config.enhancedDatabaseReporting).toBe(false)
			config.responseHook(span, { data: {} })
			expect(userResponseHook).toHaveBeenCalledTimes(1)
		})

		it('enables enhanced pg reporting by default', () => {
			const client = new ObservabilityClient('abc123')
			const config = instrumentation(
				client,
				'@opentelemetry/instrumentation-pg',
			).getConfig()
			expect(config.enhancedDatabaseReporting).toBe(true)
			expect(config.responseHook).toBeUndefined()
		})

		it("does not carry one client's config into the next", () => {
			new ObservabilityClient('abc123', {
				instrumentationConfig: {
					'@opentelemetry/instrumentation-http': {
						ignoreIncomingRequestHook: () => true,
					},
				},
			})
			const client = new ObservabilityClient('abc123')
			expect(
				instrumentation(
					client,
					'@opentelemetry/instrumentation-http',
				).getConfig().ignoreIncomingRequestHook,
			).toBeUndefined()
		})

		it('keeps an instrumentation set to enabled: false disabled after start', () => {
			const registered = (
				new ObservabilityClient('abc123').otel[
					'_instrumentations'
				] as any[]
			).find(
				(i) =>
					i.instrumentationName ===
					'@opentelemetry/instrumentation-dns',
			)
			expect(registered['_enabled']).toBe(true)

			const client = new ObservabilityClient('abc123', {
				instrumentationConfig: {
					'@opentelemetry/instrumentation-dns': { enabled: false },
				},
			})

			expect(registered['_enabled']).toBe(false)
			expect(
				instrumentation(client, '@opentelemetry/instrumentation-dns'),
			).toBeUndefined()

			// A later client without the option turns it back on.
			new ObservabilityClient('abc123')
			expect(registered['_enabled']).toBe(true)
		})

		it('warns about names that match no registered instrumentation', () => {
			const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
			new ObservabilityClient('abc123', {
				instrumentationConfig: { 'not-an-instrumentation': {} },
			})
			expect(warn).toHaveBeenCalledWith(
				expect.stringContaining('not-an-instrumentation'),
			)
			warn.mockRestore()
		})
	})

	describe('shouldExportSpan', () => {
		it('wraps the span processor when set', () => {
			const client = new ObservabilityClient('abc123', {
				shouldExportSpan: () => true,
			})
			const processors = client.otel['_tracerProviderConfig']
				?.spanProcessors as unknown[]
			expect(processors?.[0]).toBeInstanceOf(ShouldExportSpanProcessor)
		})
	})
})
