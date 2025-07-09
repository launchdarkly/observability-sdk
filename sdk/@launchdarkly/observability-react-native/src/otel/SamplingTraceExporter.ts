import { ReadableSpan, SpanExporter } from '@opentelemetry/sdk-trace-base'
import { ExportResult, ExportResultCode } from '@opentelemetry/core'
import { ExportSampler } from './sampling/ExportSampler'
import { sampleSpans } from './sampling/sampleSpans'

export class SamplingTraceExporter implements SpanExporter {
	constructor(
		private readonly exporter: SpanExporter,
		private readonly sampler: ExportSampler,
	) {}

	export(
		spans: ReadableSpan[],
		resultCallback: (result: ExportResult) => void,
	): void {
		const sampledSpans = sampleSpans(spans, this.sampler)
		this.exporter.export(sampledSpans, resultCallback)
	}

	shutdown(): Promise<void> {
		return this.exporter.shutdown()
	}

	forceFlush(): Promise<void> {
		return this.exporter.forceFlush?.() ?? Promise.resolve()
	}
}
