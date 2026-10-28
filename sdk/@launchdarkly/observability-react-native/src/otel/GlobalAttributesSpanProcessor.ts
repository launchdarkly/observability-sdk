import { Context } from '@opentelemetry/api'
import { ReadableSpan, Span, SpanProcessor } from '@opentelemetry/sdk-trace-web'
import { GlobalAttributes } from './GlobalAttributes'

/**
 * Fills global attributes onto each span at start. Attributes already set on
 * the span (e.g. passed to `startSpan`) take precedence.
 */
export class GlobalAttributesSpanProcessor implements SpanProcessor {
	constructor(private readonly store: GlobalAttributes) {}

	onStart(span: Span, _parentContext: Context): void {
		const attributes = this.store.getAttributes()
		for (const key in attributes) {
			if (span.attributes[key] === undefined) {
				span.setAttribute(key, attributes[key]!)
			}
		}
	}

	onEnd(_span: ReadableSpan): void {}

	forceFlush(): Promise<void> {
		return Promise.resolve()
	}

	shutdown(): Promise<void> {
		return Promise.resolve()
	}
}
