import { ExportResult } from '@opentelemetry/core'
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import { CustomSampler, sampleLogs } from '@launchdarkly/observability-shared'

export type LogExporterConfig = ConstructorParameters<typeof OTLPLogExporter>[0]

export class CustomLogExporter extends OTLPLogExporter {
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
