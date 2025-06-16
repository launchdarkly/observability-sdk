import {
	Attributes,
	Context,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { RequestContext } from './RequestContext'
import { Metric } from './Metric'

export type Headers = Iterable<string | string[] | undefined>
export type IncomingHttpHeaders = Record<string, string | string[] | undefined>

export interface Observe {
	/**
	 * Record arbitrary metric values via as a Gauge.
	 * A Gauge records any point-in-time measurement, such as the current CPU utilization %.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordMetric(metric: Metric): void

	/**
	 * Record arbitrary metric values via as a Counter.
	 * A Counter efficiently records an increment in a metric, such as number of cache hits.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordCount(metric: Metric): void

	/**
	 * Record arbitrary metric values via as a Counter.
	 * A Counter efficiently records an increment in a metric, such as number of cache hits.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordIncr(metric: Omit<Metric, 'value'>): void

	/**
	 * Record arbitrary metric values via as a Histogram.
	 * A Histogram efficiently records near-by point-in-time measurement into a bucketed aggregate.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordHistogram(metric: Metric): void

	/**
	 * Record arbitrary metric values via as a UpDownCounter.
	 * A UpDownCounter efficiently records an increment or decrement in a metric, such as number of paying customers.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordUpDownCounter(metric: Metric): void

	/**
	 * Logs a message with optional session and request context
	 */
	recordLog(
		message: any,
		level: string,
		secureSessionId?: string | undefined,
		requestId?: string | undefined,
		metadata?: Attributes,
	): void

	/**
	 * Record an error.
	 */
	recordError(
		error: Error,
		secureSessionId: string | undefined,
		requestId: string | undefined,
		metadata?: Attributes,
		options?: { span: OtelSpan },
	): void

	/**
	 * Flushes all pending telemetry data
	 */
	flush(): Promise<void>

	/**
	 * Sets attributes on the active span
	 */
	setAttributes(attributes: Attributes): void

	/**
	 * Parses headers to extract highlight context
	 */
	parseHeaders(headers: Headers | IncomingHttpHeaders): RequestContext

	/**
	 * Runs a callback with headers context and returns the result
	 */
	runWithHeaders<T>(
		name: string,
		headers: Headers | IncomingHttpHeaders,
		cb: (span: OtelSpan) => T | Promise<T>,
		options?: SpanOptions,
	): Promise<T>

	/**
	 * Starts a span with headers context
	 */
	startWithHeaders<T>(
		spanName: string,
		headers: Headers | IncomingHttpHeaders,
		options?: SpanOptions,
	): { span: OtelSpan; ctx: Context }

	/**
	 * Stops the observability client and flushes remaining data
	 */
	stop(): Promise<void>
}
