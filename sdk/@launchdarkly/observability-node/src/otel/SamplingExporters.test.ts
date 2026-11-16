import { SpanKind } from '@opentelemetry/api'
import { ExportResult, ExportResultCode } from '@opentelemetry/core'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import {
	BasicTracerProvider,
	BatchSpanProcessor,
	ReadableSpan,
} from '@opentelemetry/sdk-trace-base'
import { describe, expect, it, vi } from 'vitest'
import { CustomSampler } from './sampling/CustomSampler'
import { SamplingLogExporter } from './SamplingLogExporter'
import { SamplingTraceExporter } from './SamplingTraceExporter'

// A sampler that drops every span and log.
const dropAll = {
	isSamplingEnabled: () => true,
	sampleSpan: () => ({ sample: false }),
	sampleLog: () => ({ sample: false }),
	setConfig: () => {},
} as unknown as CustomSampler

const span = {
	name: 'dropped',
	kind: SpanKind.INTERNAL,
	spanContext: () => ({ traceId: 't', spanId: 's', traceFlags: 1 }),
	attributes: {},
} as unknown as ReadableSpan

const logRecord = { attributes: {} } as unknown as ReadableLogRecord

// Nothing listens here: the tests must never reach the network.
const config = { url: 'http://127.0.0.1:1/v1/traces' }

describe('SamplingTraceExporter', () => {
	it('reports success when every span is sampled out', () => {
		const exporter = new SamplingTraceExporter(config, dropAll)
		const send = vi.spyOn(
			Object.getPrototypeOf(SamplingTraceExporter.prototype),
			'export',
		)
		const resultCallback = vi.fn<(result: ExportResult) => void>()

		exporter.export([span], resultCallback)

		expect(resultCallback).toHaveBeenCalledWith({
			code: ExportResultCode.SUCCESS,
		})
		expect(send).not.toHaveBeenCalled()
		send.mockRestore()
	})

	it('reports success for an empty batch', () => {
		const exporter = new SamplingTraceExporter(config, dropAll)
		const resultCallback = vi.fn<(result: ExportResult) => void>()

		exporter.export([], resultCallback)

		expect(resultCallback).toHaveBeenCalledWith({
			code: ExportResultCode.SUCCESS,
		})
	})

	it('does not stall BatchSpanProcessor.forceFlush', async () => {
		const exporter = new SamplingTraceExporter(config, dropAll)
		const processor = new BatchSpanProcessor(exporter, {
			// Long enough that a missing callback makes the test time out.
			exportTimeoutMillis: 30_000,
		})
		const provider = new BasicTracerProvider({
			spanProcessors: [processor],
		})
		provider.getTracer('test').startSpan('dropped').end()

		const started = Date.now()
		await processor.forceFlush()
		expect(Date.now() - started).toBeLessThan(1_000)
		await provider.shutdown()
	}, 5_000)
})

describe('SamplingLogExporter', () => {
	it('reports success when every log is sampled out', () => {
		const exporter = new SamplingLogExporter(
			{ url: 'http://127.0.0.1:1/v1/logs' },
			dropAll,
		)
		const resultCallback = vi.fn<(result: ExportResult) => void>()

		exporter.export([logRecord], resultCallback)

		expect(resultCallback).toHaveBeenCalledWith({
			code: ExportResultCode.SUCCESS,
		})
	})
})
