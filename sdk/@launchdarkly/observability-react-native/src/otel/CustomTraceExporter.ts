import { ExportResult } from '@opentelemetry/core'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'
import { sampleSpans, CustomSampler } from '@launchdarkly/observability-shared'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { deduplicateSpans } from './deduplicateSpans'

export type TraceExporterConfig = ConstructorParameters<
	typeof OTLPTraceExporter
>[0]

export class CustomTraceExporter extends OTLPTraceExporter {
	constructor(
		config: TraceExporterConfig,
		private readonly sampler: CustomSampler,
	) {
		super(config)
	}

	export(
		items: ReadableSpan[],
		resultCallback: (result: ExportResult) => void,
	) {
		const sampledItems = sampleSpans(items, this.sampler)
		if (sampledItems.length === 0) {
			resultCallback({ code: 0 })
			return
		}

		const deduplicatedItems = deduplicateSpans(sampledItems)
		if (deduplicatedItems.length === 0) {
			resultCallback({ code: 0 })
			return
		}

		super.export(deduplicatedItems, resultCallback)
	}
}
