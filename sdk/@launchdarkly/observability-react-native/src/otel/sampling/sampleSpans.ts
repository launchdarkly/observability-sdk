import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ExportSampler } from './ExportSampler'

export function sampleSpans(
	spans: ReadableSpan[],
	sampler: ExportSampler,
): ReadableSpan[] {
	if (!sampler.isSamplingEnabled()) {
		return spans
	}

	return spans.filter((span) => {
		const result = sampler.sampleSpan(span)
		if (result.attributes) {
			Object.entries(result.attributes).forEach(([key, value]) => {
				span.attributes[key] = value
			})
		}
		return result.sample
	})
}
