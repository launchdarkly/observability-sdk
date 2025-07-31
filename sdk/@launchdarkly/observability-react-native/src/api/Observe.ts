import {
	Attributes,
	Context,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { Metric } from './Metric'
import { RequestContext } from './RequestContext'
import { SessionInfo } from '../client/SessionManager'

export interface Observe {
	/**
	 * Record an error with optional context.
	 * @param error The error to record
	 * @param attributes Optional additionalattributes
	 * @param options Optional span options
	 */
	recordError(
		error: Error,
		attributes?: Attributes,
		options?: { span: OtelSpan },
	): void

	/**
	 * Record a metric value.
	 * @param metric The metric to record
	 */
	recordMetric(metric: Metric): void

	/**
	 * Record a count metric.
	 * @param metric The count metric to record
	 */
	recordCount(metric: Metric): void

	/**
	 * Record an increment metric.
	 * @param metric The increment metric to record
	 */
	recordIncr(metric: Metric): void

	/**
	 * Record a histogram metric.
	 * @param metric The histogram metric to record
	 */
	recordHistogram(metric: Metric): void

	/**
	 * Record an up/down counter metric.
	 * @param metric The up/down counter metric to record
	 */
	recordUpDownCounter(metric: Metric): void

	/**
	 * Flush all pending telemetry data.
	 */
	flush(): Promise<void>

	/**
	 * Record a log message.
	 * @param message The log message
	 * @param level The log level
	 * @param attributes Optional additional attributes
	 */
	recordLog(message: any, level: string, attributes?: Attributes): void

	/**
	 * Parse headers to extract request context.
	 * @param headers The headers to parse
	 */
	parseHeaders(headers: Record<string, string>): RequestContext

	/**
	 * Run a function with header context.
	 * @param name The span name
	 * @param headers The headers
	 * @param cb The callback function
	 * @param options Optional span options
	 */
	runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any

	/**
	 * Start a span with header context.
	 * @param spanName The span name
	 * @param headers The headers
	 * @param options Optional span options
	 */
	startWithHeaders(
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	): OtelSpan

	/**
	 * Start a new span without making it active.
	 * @param spanName The span name
	 * @param options Optional span options
	 */
	startSpan(spanName: string, options?: SpanOptions, ctx?: Context): OtelSpan

	/**
	 * Start a new active span and run a callback function within its context.
	 * @param spanName The span name
	 * @param fn The callback function to run with the active span
	 * @param options Optional span options
	 */
	startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
		ctx?: Context,
	): T

	/**
	 * Get the context from a span.
	 * @param span The span to get the context from
	 */
	getContextFromSpan(span: OtelSpan): Context

	/**
	 * Get the current session information.
	 */
	getSessionInfo(): SessionInfo

	/**
	 * Stop the observability client.
	 */
	stop(): Promise<void>

	/**
	 * Check if the observability client is initialized.
	 */
	isInitialized(): boolean
}
