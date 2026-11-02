import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ExportResultCode } from '@opentelemetry/core'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import { resourceFromAttributes } from '@opentelemetry/resources'
import {
	AggregationTemporality,
	DataPointType,
	InstrumentType,
	type ResourceMetrics,
} from '@opentelemetry/sdk-metrics'
import { InstrumentationManager } from '../client/InstrumentationManager'
import { createMetricExporter } from './createMetricExporter'

const METRICS_URL = 'http://127.0.0.1:1/v1/metrics'

// Local endpoints that fail fast so nothing reaches the network.
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
	disableErrorTracking: true,
	disableLogs: true,
	disableTraces: true,
	disableMetrics: false,
	tracingOrigins: false as const,
	urlBlocklist: [],
	networkRecording: {},
	contextFriendlyName: () => undefined,
	flagExposureDedupeWindowMillis: 0,
	flagExposureDedupeMaxSize: 2000,
	maxBufferSize: 1000,
	uploadIntervalMillis: 10 * 60 * 1000,
}

describe('createMetricExporter', () => {
	let exported: ResourceMetrics[]

	beforeEach(() => {
		exported = []
		vi.spyOn(OTLPMetricExporter.prototype, 'export').mockImplementation(
			(items, resultCallback) => {
				exported.push(items)
				resultCallback({ code: ExportResultCode.SUCCESS })
			},
		)
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('prefers delta temporality for counters and histograms', () => {
		const exporter = createMetricExporter({}, METRICS_URL)

		expect(
			exporter.selectAggregationTemporality(InstrumentType.COUNTER),
		).toBe(AggregationTemporality.DELTA)
		expect(
			exporter.selectAggregationTemporality(InstrumentType.HISTOGRAM),
		).toBe(AggregationTemporality.DELTA)
		expect(
			exporter.selectAggregationTemporality(
				InstrumentType.UP_DOWN_COUNTER,
			),
		).toBe(AggregationTemporality.CUMULATIVE)
	})

	it('exports only new increments in each interval', async () => {
		const manager = new InstrumentationManager(localOptions as any)
		await manager.initialize(resourceFromAttributes({}))
		const attributes = { step: 'landing' }

		manager.recordIncr({ name: 'step', value: 1, attributes })
		manager.recordIncr({ name: 'step', value: 1, attributes })
		await manager.flush()
		manager.recordIncr({ name: 'step', value: 1, attributes })
		await manager.flush()

		const values = exported.flatMap((rm) =>
			rm.scopeMetrics.flatMap((sm) =>
				sm.metrics.flatMap((m) => {
					expect(m.dataPointType).toBe(DataPointType.SUM)
					expect(m.aggregationTemporality).toBe(
						AggregationTemporality.DELTA,
					)
					return m.dataPoints.map((dp) => dp.value as number)
				}),
			),
		)
		expect(values).toEqual([2, 1])

		await manager.stop()
	})
})
