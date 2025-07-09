import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import { ExportSampler } from './ExportSampler'

export function sampleLogs(
	logs: ReadableLogRecord[],
	sampler: ExportSampler,
): ReadableLogRecord[] {
	if (!sampler.isSamplingEnabled()) {
		return logs
	}

	return logs.filter((log) => {
		const result = sampler.sampleLog(log)
		if (result.attributes) {
			Object.entries(result.attributes).forEach(([key, value]) => {
				if (log.attributes) {
					log.attributes[key] = value
				}
			})
		}
		return result.sample
	})
}
