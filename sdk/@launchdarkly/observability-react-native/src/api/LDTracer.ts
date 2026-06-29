import { Tracer } from '@opentelemetry/api'
import { runInSpan, SpanOps } from '../sdk/withSpan'
import { SpanScope, WithSpanOptions } from './SpanScope'

/**
 * An OpenTelemetry {@link Tracer} enriched with {@link withSpan} for React
 * Native, where the active context is tracked only synchronously.
 *
 * Returned by {@link Observe.getTracer}. Implements the full standard
 * `Tracer` API (`startSpan`, `startActiveSpan`) plus the async-safe
 * {@link LDTracer.withSpan} helper.
 */
export interface LDTracer extends Tracer {
	/**
	 * Start a span, run `fn` within it, and end the span automatically.
	 *
	 * Same behavior as {@link Observe.withSpan}: the callback receives a
	 * {@link SpanScope} whose {@link SpanScope.child} method parents spans
	 * correctly across `await`s in React Native.
	 */
	withSpan<T>(
		spanName: string,
		fn: (scope: SpanScope) => T,
		options?: WithSpanOptions,
	): T
}

/**
 * Wrap an OpenTelemetry {@link Tracer} with {@link LDTracer.withSpan}.
 *
 * @param tracer The underlying OpenTelemetry tracer
 * @param ops Span operations used by `withSpan` (`startSpan` defaults to
 *   `tracer.startSpan`; `recordError` is required for error reporting)
 */
export function wrapTracer(tracer: Tracer, ops: SpanOps): LDTracer {
	return {
		startSpan: tracer.startSpan.bind(tracer),
		startActiveSpan: tracer.startActiveSpan.bind(tracer),
		withSpan: (name, fn, options) => runInSpan(ops, name, fn, options),
	}
}
