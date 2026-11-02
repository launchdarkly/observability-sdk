import type { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ObservabilityClient } from './ObservabilityClient'
import { InstrumentationManager } from './InstrumentationManager'
import { CustomLogExporter } from '../otel/CustomLogExporter'
import { CustomTraceExporter } from '../otel/CustomTraceExporter'
import {
	afterAll,
	beforeAll,
	beforeEach,
	describe,
	expect,
	it,
	vi,
} from 'vitest'

// Local endpoints that fail fast (connection refused) so the sampling
// configuration fetch in initialize() never reaches the network.
const localOptions = {
	projectId: 'test-project-id',
	serviceName: 'test-service',
	backendUrl: 'http://127.0.0.1:1',
	otlpEndpoint: 'http://127.0.0.1:1',
	serviceVersion: '1.0.0',
	resourceAttributes: {},
	customHeaders: {},
	sessionTimeout: 15 * 60 * 1000,
	debug: false,
	disableErrorTracking: false,
	disableLogs: false,
	disableTraces: false,
	disableMetrics: true,
	tracingOrigins: false as const,
	urlBlocklist: [],
	networkRecording: {},
	contextFriendlyName: () => undefined,
	flagExposureDedupeWindowMillis: 0,
	flagExposureDedupeMaxSize: 2000,
	maxBufferSize: 1000,
	uploadIntervalMillis: 10 * 60 * 1000,
}

// Capture what the exporters receive instead of letting them hit the network.
const exportedLogs: ReadableLogRecord[] = []
const exportedSpans: ReadableSpan[] = []

beforeAll(() => {
	vi.spyOn(CustomLogExporter.prototype, 'export').mockImplementation(
		(items, resultCallback) => {
			exportedLogs.push(...items)
			resultCallback({ code: 0 })
		},
	)
	vi.spyOn(CustomTraceExporter.prototype, 'export').mockImplementation(
		(items, resultCallback) => {
			exportedSpans.push(...items)
			resultCallback({ code: 0 })
		},
	)
})

beforeEach(() => {
	exportedLogs.length = 0
	exportedSpans.length = 0
})

describe('InstrumentationManager recordError', () => {
	describe('unit (injected tracer/logger)', () => {
		const makeSpan = () => ({
			recordException: vi.fn(),
			setAttribute: vi.fn(),
			setAttributes: vi.fn(),
			setStatus: vi.fn(),
			end: vi.fn(),
		})

		const makeLogger = () => ({ emit: vi.fn() })

		it('records the exception once on a new span and emits no error log', () => {
			const manager = new InstrumentationManager(localOptions)
			const span = makeSpan()
			const logger = makeLogger()

			;(manager as any).getTracer = () => ({
				startSpan: vi.fn(() => span),
			})
			;(manager as any).getLogger = () => logger

			const error = new Error('boom')
			manager.recordError(error)

			expect(span.recordException).toHaveBeenCalledTimes(1)
			expect(span.recordException).toHaveBeenCalledWith(error)
			// The span is the only error record: no separate error log.
			expect(logger.emit).not.toHaveBeenCalled()
			// `log.source` was carried over from the removed error log.
			expect(span.setAttribute).toHaveBeenCalledWith(
				'log.source',
				'react-native-plugin',
			)
			expect(span.setAttribute).toHaveBeenCalledWith(
				'exception.message',
				'boom',
			)
			// No active span, so the new span is ended.
			expect(span.end).toHaveBeenCalledTimes(1)
		})

		it('records the exception once on the provided span and emits no error log', () => {
			const manager = new InstrumentationManager(localOptions)
			const activeSpan = makeSpan()
			const logger = makeLogger()

			;(manager as any).getTracer = () => ({
				startSpan: vi.fn(),
			})
			;(manager as any).getLogger = () => logger

			const error = new Error('boom')
			manager.recordError(
				error,
				{ 'error.context': 'test' },
				{
					span: activeSpan as any,
				},
			)

			expect(activeSpan.recordException).toHaveBeenCalledTimes(1)
			expect(activeSpan.recordException).toHaveBeenCalledWith(error)
			expect(activeSpan.setAttributes).toHaveBeenCalledWith({
				'error.context': 'test',
			})
			// The caller owns the provided span, so it is not ended.
			expect(activeSpan.end).not.toHaveBeenCalled()
			expect(logger.emit).not.toHaveBeenCalled()
		})
	})

	describe('integration through the client and console.error', () => {
		let client: ObservabilityClient

		beforeAll(async () => {
			client = new ObservabilityClient('test-project-id', localOptions)
			await client.whenInitialized()
			// Drain anything queued during startup and start each test clean.
			await client.flush()
			exportedLogs.length = 0
			exportedSpans.length = 0
		})

		afterAll(async () => {
			// Restore console.error before the manager providers go away.
			;(client as any).errorInstrumentation?.destroy?.()
			await client.stop()
		})

		it('recordError exports one error span and no log record', async () => {
			const clientError = new Error('boom-record-error')
			client.consumeCustomError(clientError)
			await client.flush()

			const errorSpans = exportedSpans.filter((span) =>
				Object.keys(span.attributes ?? {}).some((key) =>
					key.startsWith('exception.'),
				),
			)
			expect(errorSpans).toHaveLength(1)
			expect(errorSpans[0].name).toBe('error')
			const attributes = errorSpans[0].attributes as Record<
				string,
				string
			>
			expect(attributes['exception.message']).toBe('boom-record-error')
			expect(attributes['exception.type']).toBe('Error')
			expect(attributes['log.source']).toBe('react-native-plugin')

			// No separate log row for the error.
			expect(
				exportedLogs.filter((log) =>
					Object.keys(log.attributes ?? {}).some((key) =>
						key.startsWith('exception.'),
					),
				),
			).toHaveLength(0)
		})

		it('console.error exports one error span and no error log record', async () => {
			console.error('boom-console', { data: 'test' })
			await client.flush()

			const errorSpans = exportedSpans.filter((span) =>
				Object.keys(span.attributes ?? {}).some((key) =>
					key.startsWith('exception.'),
				),
			)
			expect(errorSpans).toHaveLength(1)
			const attributes = errorSpans[0].attributes as Record<
				string,
				string
			>
			expect(attributes['exception.message']).toContain('boom-console')
			expect(attributes['error.caught_by']).toBe('console.error')

			expect(
				exportedLogs.filter((log) =>
					Object.keys(log.attributes ?? {}).some((key) =>
						key.startsWith('exception.'),
					),
				),
			).toHaveLength(0)
		})
	})
})
