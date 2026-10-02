import { Context, Span as OtelSpan, SpanOptions } from '@opentelemetry/api'

/**
 * Options accepted by {@link Observe.withSpan} and {@link SpanScope.child}.
 *
 * In addition to the standard OpenTelemetry {@link SpanOptions}, an explicit
 * `parent` context can be supplied. This is the mechanism that makes spans
 * nest correctly across `await`s in React Native, where the active context is
 * tracked only synchronously (see the distributed tracing guide).
 */
export type WithSpanOptions = SpanOptions & {
	/**
	 * The context to parent the new span under. When omitted the currently
	 * active context is used. {@link SpanScope.child} sets this automatically to
	 * the parent scope's context, so children nest correctly even after an
	 * `await`.
	 */
	parent?: Context
}

/**
 * A handle to a span started with {@link Observe.withSpan}.
 *
 * A scope captures its own span context, so child spans created via
 * {@link SpanScope.child} are parented correctly even across `await`
 * boundaries — without manually threading context through your code.
 */
export interface SpanScope {
	/** The underlying OpenTelemetry span. */
	readonly span: OtelSpan

	/**
	 * This span's context. Pass it anywhere an explicit parent {@link Context}
	 * is required.
	 */
	readonly ctx: Context

	/**
	 * Start a child span parented to *this* scope, run `fn`, and end the span
	 * automatically. The span's status is set to `OK` on success, or `ERROR`
	 * (with the thrown error recorded) if `fn` throws or rejects.
	 *
	 * Because the parent is captured from this scope rather than read from the
	 * active context, the child nests correctly even when created after an
	 * `await`.
	 *
	 * @param name The child span name
	 * @param fn The callback to run within the child scope
	 * @param options Optional span options
	 */
	child<T>(
		name: string,
		fn: (scope: SpanScope) => T,
		options?: WithSpanOptions,
	): T

	/**
	 * Run `fn` with *this* span active. Use this to parent auto-instrumented
	 * `fetch`/`XMLHttpRequest` spans that are started after an `await` (the
	 * point at which React Native loses the active context). Calls started in
	 * the synchronous portion of a {@link Observe.withSpan} callback are already
	 * parented automatically.
	 *
	 * @param fn The callback to run with this span active
	 */
	active<T>(fn: () => T): T
}
