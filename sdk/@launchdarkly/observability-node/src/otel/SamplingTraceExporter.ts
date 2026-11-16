import { ExportResult, ExportResultCode } from '@opentelemetry/core'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { CustomSampler } from './sampling/CustomSampler'
import { sampleSpans } from './sampling/sampleSpans'

export type TraceExporterConfig = ConstructorParameters<
	typeof OTLPTraceExporter
>[0]

export class SamplingTraceExporter extends OTLPTraceExporter {
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
			// Nothing to send, but the caller (e.g. the batch processor) still
			// waits on the callback; without it, flushes stall until they time out.
			resultCallback({ code: ExportResultCode.SUCCESS })
			return
		}
		super.export(sampledItems, resultCallback)
	}
}
