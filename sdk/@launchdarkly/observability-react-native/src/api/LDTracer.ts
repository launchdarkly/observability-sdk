import {
	Context,
	Span as OtelSpan,
	SpanOptions,
	Tracer,
} from '@opentelemetry/api'
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

function parseStartActiveSpanArgs(
	name: string,
	...rest: unknown[]
): {
	fn: (span: OtelSpan) => unknown
	options?: SpanOptions
	ctx?: Context
} {
	if (typeof rest[0] === 'function') {
		return { fn: rest[0] as (span: OtelSpan) => unknown }
	}
	if (typeof rest[1] === 'function') {
		return {
			options: rest[0] as SpanOptions,
			fn: rest[1] as (span: OtelSpan) => unknown,
		}
	}
	return {
		options: rest[0] as SpanOptions,
		ctx: rest[1] as Context,
		fn: rest[2] as (span: OtelSpan) => unknown,
	}
}

/**
 * Wrap span operations with the {@link LDTracer} surface (`startSpan`,
 * `startActiveSpan`, `withSpan`).
 *
 * All three methods delegate to `ops` so callers can enforce SDK guards
 * (for example `disableTraces`) and pre-init no-op behavior consistently.
 */
export function wrapTracer(ops: SpanOps): LDTracer {
	const startActiveSpan = ((name: string, ...rest: unknown[]) => {
		const { fn, options, ctx } = parseStartActiveSpanArgs(name, ...rest)
		return ops.startActiveSpan(name, fn, options, ctx)
	}) as LDTracer['startActiveSpan']

	return {
		startSpan: (name, options, ctx) => ops.startSpan(name, options, ctx),
		startActiveSpan,
		withSpan: (name, fn, options) => runInSpan(ops, name, fn, options),
	}
}
