import { ReadableLogRecord, LogRecordExporter } from '@opentelemetry/sdk-logs'
import { ExportResult } from '@opentelemetry/core'
import { ExportSampler } from './sampling/ExportSampler'
import { sampleLogs } from './sampling/sampleLogs'

export class SamplingLogExporter implements LogRecordExporter {
	constructor(
		private readonly exporter: LogRecordExporter,
		private readonly sampler: ExportSampler,
	) {}

	export(
		logs: ReadableLogRecord[],
		resultCallback: (result: ExportResult) => void,
	): void {
		const sampledLogs = sampleLogs(logs, this.sampler)
		this.exporter.export(sampledLogs, resultCallback)
	}

	shutdown(): Promise<void> {
		return this.exporter.shutdown()
	}

	forceFlush(): Promise<void> {
		if (
			'forceFlush' in this.exporter &&
			typeof this.exporter.forceFlush === 'function'
		) {
			return this.exporter.forceFlush()
		}
		return Promise.resolve()
	}
}
