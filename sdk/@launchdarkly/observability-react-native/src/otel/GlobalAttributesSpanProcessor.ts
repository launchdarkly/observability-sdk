import { Attributes, Context } from '@opentelemetry/api'
import { ReadableSpan, Span, SpanProcessor } from '@opentelemetry/sdk-trace-web'

/**
 * Stamps the attributes set with `LDObserve.setAttributes` on every span when
 * it starts, including auto-instrumented ones (fetch/XHR, flag evaluations).
 * Keys the span already carries are left alone so per-call attributes win.
 * Must be registered before the exporting processor.
 */
export class GlobalAttributesSpanProcessor implements SpanProcessor {
	constructor(private readonly getAttributes: () => Attributes) {}

	onStart(span: Span, _parentContext: Context): void {
		const attributes = this.getAttributes()
		for (const key in attributes) {
			const value = attributes[key]
			if (value !== undefined && span.attributes[key] === undefined) {
				span.setAttribute(key, value)
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
