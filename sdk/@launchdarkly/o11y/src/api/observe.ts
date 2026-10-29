import type { Attributes, Context, Span, SpanOptions } from '@opentelemetry/api'
import type { OTelMetric as Metric } from '../client/types/types'
import type { ErrorMessageType } from '../client/types/shared-types'
import { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { ConsoleMethods } from '../client/types/client'
import type { Hook, LDClient } from '../integrations/launchdarkly'

export interface Observe {
	/**
	 * Start the observability data capture when running in `manualStart` mode.
	 * Can safely be called before the SDK has initialized (e.g. before the
	 * plugin registers): the call resolves once the SDK is attached and
	 * started, without throwing.
	 */
	start: () => Promise<void>
	/**
	 * Stop the observability data capture.
	 * Can safely be called before the SDK has initialized; before `start()`
	 * it cancels a pending start instead of throwing.
	 */
	stop: () => Promise<void>
	/**
	 * Record arbitrary metric values via as a Gauge.
	 * A Gauge records any point-in-time measurement, such as the current CPU utilization %.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordGauge: (metric: Metric) => void
	/**
	 * Record arbitrary metric values via as a Counter.
	 * A Counter efficiently records an increment in a metric, such as number of cache hits.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordCount: (metric: Metric) => void
	/**
	 * Record arbitrary metric values via as a Counter.
	 * A Counter efficiently records an increment in a metric, such as number of cache hits.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordIncr: (metric: Omit<Metric, 'value'>) => void
	/**
	 * Record arbitrary metric values via as a Histogram.
	 * A Histogram efficiently records near-by point-in-time measurement into a bucketed aggregate.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordHistogram: (metric: Metric) => void
	/**
	 * Record arbitrary metric values via as a UpDownCounter.
	 * A UpDownCounter efficiently records an increment or decrement in a metric, such as number of paying customers.
	 * Values with the same metric name and attributes are aggregated via the OTel SDK.
	 * See https://opentelemetry.io/docs/specs/otel/metrics/data-model/ for more details.
	 */
	recordUpDownCounter: (metric: Metric) => void
	/**
	 * Starts a new span for tracing in Highlight. The span will be ended when the
	 * callback function returns.
	 *
	 * @example
	 * ```typescript
	 * H.startSpan('span-name', callbackFn)
	 * ```
	 * @example
	 * ```typescript
	 * H.startSpan('span-name', options, callbackFn)
	 * ```
	 * @example
	 * ```typescript
	 * H.startSpan('span-name', options, context, callbackFn)
	 * ```
	 * @example
	 * ```typescript
	 * H.startSpan('span-name', async (span) => {
	 *   span.setAttribute('key', 'value')
	 *   await someAsyncFunction()
	 * })
	 * ```
	 *
	 * @param name The name of the span.
	 * @param options Options for the span.
	 * @param context The context for the span.
	 * @param callbackFn The function to run in the span.
	 */
	startSpan: {
		<F extends (span?: Span) => ReturnType<F>>(
			name: string,
			fn: F,
		): ReturnType<F>
		<F extends (span?: Span) => ReturnType<F>>(
			name: string,
			options: SpanOptions,
			fn: F,
		): ReturnType<F>
		<F extends (span?: Span) => ReturnType<F>>(
			name: string,
			options: SpanOptions,
			context: Context,
			fn: F,
		): ReturnType<F>
	}
	/**
	 * Starts a new span for tracing in Highlight. The span will be ended when the
	 * `end()` is called on the span. It returns whatever is returned from the
	 * callback function.
	 *
	 * @example
	 * ```typescript
	 * H.startManualSpan('span-name', options, (span) => {
	 *   span.addEvent('event-name', { key: 'value' })
	 *   span.setAttribute('key', 'value')
	 *   await someAsyncFunction()
	 *   span.end()
	 * })
	 * ```
	 *
	 * @example
	 * ```typescript
	 * const span = H.startManualSpan('span-name', (s) => s)
	 * span.addEvent('event-name', { key: 'value' })
	 * await someAsyncFunction()
	 * span.end()
	 * ```
	 *
	 * @param name The name of the span.
	 * @param options Options for the span.
	 * @param context The context for the span.
	 * @param fn The function to run in the span.
	 */
	startManualSpan: {
		<F extends (span: Span) => ReturnType<F>>(
			name: string,
			fn: F,
		): ReturnType<F>
		<F extends (span: Span) => ReturnType<F>>(
			name: string,
			options: SpanOptions,
			fn: F,
		): ReturnType<F>
		<F extends (span: Span) => ReturnType<F>>(
			name: string,
			options: SpanOptions,
			context: Context,
			fn: F,
		): ReturnType<F>
	}
	/**
	 * Calling this method will report an error in Highlight and map it to the current session being recorded.
	 * A common use case for `H.error` is calling it right outside of an error boundary.
	 * @see {@link https://docs.highlight.run/grouping-errors} for more information.
	 */
	recordError: (
		error: any,
		message?: string,
		payload?: { [key: string]: string },
		source?: string,
		type?: ErrorMessageType,
	) => void

	/**
	 * Record arbitrary logs from your own integrations or manual.
	 * Useful when you don't want to emit a console log to the browser dev tools but still want to report a custom log.
	 */
	recordLog: (
		message: any,
		level: ConsoleMethods,
		metadata?: Attributes,
	) => void

	/**
	 * Set attributes that are added to every span, log, and metric recorded
	 * after the call, including automatically instrumented telemetry. Merges
	 * by key into the existing global attributes, which last for the page
	 * lifetime (they are not persisted and survive session changes).
	 *
	 * Attributes set explicitly on a span, log, or metric take precedence;
	 * global attributes only fill in keys that are missing. A span gets the
	 * global attributes current when it starts. SDK-managed session keys
	 * (`highlight.session_id`, `session.id`) and values that are not valid
	 * OpenTelemetry attribute values are ignored.
	 *
	 * Can be called before the SDK has initialized or started.
	 *
	 * @example
	 * ```typescript
	 * LDObserve.setAttributes({ 'app.tenant': 'acme', 'app.build': 42 })
	 * ```
	 */
	setAttributes: (attributes: Attributes) => void
	/**
	 * Remove global attributes previously set with `setAttributes`.
	 * Telemetry recorded after the call no longer carries them.
	 *
	 * @example
	 * ```typescript
	 * LDObserve.removeAttributes(['app.tenant'])
	 * ```
	 */
	removeAttributes: (keys: string[]) => void

	register(
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
	setLDContextKeyAttributes(contextKeys: Attributes): void
	getLDContextKeyAttributes(): Attributes | undefined
}
