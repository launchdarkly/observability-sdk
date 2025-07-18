import { Attributes } from '@opentelemetry/api'
import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { SamplingConfig } from '../../graph/generated/graphql'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'

export interface SamplingResult {
	/**
	 * Whether the span should be sampled.
	 */
	sample: boolean

	/**
	 * The attributes to add to the span.
	 */
	attributes?: Attributes
}

/**
 * A sampler that runs during the export phase of the trace pipeline.
 */
export interface ExportSampler {
	/**
	 * Returns a sampling result for a span.
	 *
	 * @param span The span to sample.
	 */
	sampleSpan(span: ReadableSpan): SamplingResult

	/**
	 * Returns a sampling result for a log.
	 *
	 * @param log The log to sample.
	 */
	sampleLog(log: ReadableLogRecord): SamplingResult

	/**
	 * Returns true if sampling is enabled. If there are no sampling configurations, then sampling can be skipped.
	 */
	isSamplingEnabled(): boolean

	setConfig(config?: SamplingConfig): void
}
