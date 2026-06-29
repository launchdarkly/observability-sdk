import {
	Attributes,
	Context,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { Metric } from './Metric'
import { RequestContext } from './RequestContext'
import { SessionInfo } from '../client/SessionManager'
import { LDTracer } from './LDTracer'
import { SpanScope, WithSpanOptions } from './SpanScope'
import { TrackProperties } from './TrackProperties'

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
	 * Record a custom track event as a `track` span.
	 *
	 * Mirrors the iOS and Android `LDObserve.track(...)` API (and `LDClient.track`):
	 * emits a span named `track` carrying the event `key`, an optional numeric
	 * `value` for LaunchDarkly numeric custom metrics, and any `properties` as
	 * additional span attributes.
	 *
	 * `properties` is a plain dictionary (like the native `[String: Any]` /
	 * `Map<String, Any?>` surfaces): nested objects are flattened with
	 * dot-separated keys (e.g. `user.id`), arrays of objects with indexed dotted
	 * keys (e.g. `products.0.price`), and homogeneous scalar arrays become array
	 * attributes. `null` / `undefined` values are skipped.
	 *
	 * @param key The key for the event.
	 * @param properties Optional data associated with the event; flattened and attached as span attributes.
	 * @param metricValue Optional numeric value used by LaunchDarkly experimentation for numeric custom metrics.
	 */
	track(key: string, properties?: TrackProperties, metricValue?: number): void

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
	 * Start a span, run `fn` within it, and end the span automatically.
	 *
	 * This is an ergonomic wrapper over {@link startSpan} designed for React
	 * Native, where the active context is tracked only synchronously and is lost
	 * across each `await`. The {@link SpanScope} passed to `fn` exposes a
	 * {@link SpanScope.child} method that parents child spans off the captured
	 * context, so the hierarchy is preserved across `await`s and even under
	 * concurrent (`Promise.all`) work — without manually threading context.
	 *
	 * The span's status is set to `OK` on success, or `ERROR` (with the error
	 * recorded) if `fn` throws or returns a rejecting promise. If `fn` returns a
	 * promise, the span ends when it settles and the promise is returned.
	 *
	 * @param spanName The span name
	 * @param fn The callback to run within the span's scope
	 * @param options Optional span options, including an explicit `parent`
	 */
	withSpan<T>(
		spanName: string,
		fn: (scope: SpanScope) => T,
		options?: WithSpanOptions,
	): T

	/**
	 * Get the OpenTelemetry {@link LDTracer} backing this SDK.
	 *
	 * Returns a standard OpenTelemetry {@link Tracer} (`startSpan`,
	 * `startActiveSpan`) plus {@link LDTracer.withSpan} for async-safe nested
	 * spans in React Native. Use this to follow the official OpenTelemetry JS
	 * documentation or integrate a third-party library that expects a `Tracer`.
	 * The tracer is wired to the same exporter/sampler as the rest of the SDK.
	 *
	 * Before the SDK finishes initializing (or when `disableTraces` is set) this
	 * returns a no-op tracer, so it is always safe to call.
	 */
	getTracer(): LDTracer

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
