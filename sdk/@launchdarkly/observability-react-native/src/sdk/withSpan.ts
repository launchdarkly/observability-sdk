import {
	Attributes,
	context,
	Context,
	Span as OtelSpan,
	SpanOptions,
	SpanStatusCode,
	trace,
} from '@opentelemetry/api'
import { SpanScope, WithSpanOptions } from '../api/SpanScope'
import { noOpSpan } from '../utils/NoOpSpan'

/**
 * The minimal surface {@link runInSpan} needs. Satisfied directly by the
 * `LDObserve` singleton when loaded, and by {@link NOOP_SPAN_OPS} before init.
 *
 * Internal: not part of the public API.
 */
export interface SpanOps {
	startSpan(spanName: string, options?: SpanOptions, ctx?: Context): OtelSpan
	recordError(
		error: Error,
		attributes?: Attributes,
		options?: { span: OtelSpan },
	): void
}

/**
 * Used before the client is initialized: produces no-op spans and swallows
 * errors, while still running the callback so application logic is unaffected.
 *
 * Internal: not part of the public API.
 */
export const NOOP_SPAN_OPS: SpanOps = {
	startSpan: () => noOpSpan,
	recordError: () => {},
}

/**
 * Shared implementation behind `LDObserve.withSpan` and {@link SpanScope.child}.
 * Parents the span off the supplied/active context, runs `fn` with the span
 * active (so synchronous auto-instrumented calls are parented), and ends the
 * span — handling both sync and promise returns.
 *
 * Internal: not part of the public API.
 */
export function runInSpan<T>(
	ops: SpanOps,
	name: string,
	fn: (scope: SpanScope) => T,
	options?: WithSpanOptions,
): T {
	const { parent: parentOption, ...spanOptions } = options ?? {}
	const parent = parentOption ?? context.active()
	const span = ops.startSpan(name, spanOptions, parent)
	const ctx = trace.setSpan(parent, span)

	const scope: SpanScope = {
		span,
		ctx,
		child: (childName, childFn, childOptions) =>
			runInSpan(ops, childName, childFn, {
				...childOptions,
				parent: ctx,
			}),
		active: (activeFn) => context.with(ctx, activeFn),
	}

	const succeed = () => {
		span.setStatus({ code: SpanStatusCode.OK })
		span.end()
	}
	const failWith = (error: unknown) => {
		try {
			ops.recordError(error as Error, undefined, { span })
		} catch {
			// recordError should never throw, but never mask the original error.
		}
		span.setStatus({ code: SpanStatusCode.ERROR })
		span.end()
	}

	try {
		// Make the span active for the synchronous window so auto-instrumented
		// fetch/XHR calls started before the first `await` parent to it.
		const result = context.with(ctx, () => fn(scope))
		if (result instanceof Promise) {
			return result.then(
				(value) => {
					succeed()
					return value
				},
				(error) => {
					failWith(error)
					throw error
				},
			) as unknown as T
		}
		succeed()
		return result
	} catch (error) {
		failWith(error)
		throw error
	}
}
