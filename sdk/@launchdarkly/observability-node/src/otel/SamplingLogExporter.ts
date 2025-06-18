import { ExportResult } from '@opentelemetry/core'
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import { CustomSampler } from './sampling/CustomSampler'
import { sampleLogs } from './sampling/sampleLogs'

export type LogExporterConfig = ConstructorParameters<typeof OTLPLogExporter>[0]

export class SamplingLogExporter extends OTLPLogExporter {
	constructor(
		config: LogExporterConfig,
		private readonly sampler: CustomSampler,
	) {
		super(config)
	}

	export(
		items: ReadableLogRecord[],
		resultCallback: (result: ExportResult) => void,
	) {
		const sampledItems = sampleLogs(items, this.sampler)
		if (sampledItems.length === 0) {
			return
		}
		super.export(sampledItems, resultCallback)
	}
}
