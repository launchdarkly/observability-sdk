import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ExportResultCode } from '@opentelemetry/core'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	AggregationTemporality,
	DataPointType,
	InstrumentType,
	MeterProvider,
	PeriodicExportingMetricReader,
	type ResourceMetrics,
} from '@opentelemetry/sdk-metrics'
import { createMetricExporter } from './index'

const METRICS_URL = 'https://pub.test.invalid/v1/metrics'

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
		const reader = new PeriodicExportingMetricReader({
			exporter: createMetricExporter({}, METRICS_URL),
			exportIntervalMillis: 60_000,
		})
		const meterProvider = new MeterProvider({ readers: [reader] })
		const counter = meterProvider.getMeter('test').createCounter('step')
		const attributes = { step: 'landing' }

		counter.add(1, attributes)
		counter.add(1, attributes)
		await meterProvider.forceFlush()
		counter.add(1, attributes)
		await meterProvider.forceFlush()

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
		expect(values.reduce((a, b) => a + b, 0)).toBe(3)

		await meterProvider.shutdown()
	})
})
