import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import { Attributes } from '@opentelemetry/api'
import { merge } from '@opentelemetry/core'
import { ExportSampler } from './ExportSampler'

export function cloneLogRecordWithAttributes(
	log: ReadableLogRecord,
	attributes: Attributes,
): ReadableLogRecord {
	const cloned = {
		...log,
		attributes: merge(log.attributes, attributes),
	}
	return cloned
}

export function sampleLogs(
	items: ReadableLogRecord[],
	sampler: ExportSampler,
): ReadableLogRecord[] {
	if (!sampler.isSamplingEnabled()) {
		return items
	}

	const sampledLogs: ReadableLogRecord[] = []

	for (const item of items) {
		const sampleResult = sampler.sampleLog(item)
		if (sampleResult.sample) {
			if (sampleResult.attributes) {
				sampledLogs.push(
					cloneLogRecordWithAttributes(item, sampleResult.attributes),
				)
			} else {
				sampledLogs.push(item)
			}
		}
		// If not sampled, we simply don't include it in the result
	}

	return sampledLogs
}
