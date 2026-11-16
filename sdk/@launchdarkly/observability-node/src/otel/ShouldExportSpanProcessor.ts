import { Context, diag } from '@opentelemetry/api'
import {
	ReadableSpan,
	Span,
	SpanProcessor,
} from '@opentelemetry/sdk-trace-base'
import type { ShouldExportSpan } from '../api/Options.js'

/**
 * Wraps a span processor and forwards only the finished spans for which
 * `shouldExportSpan` returns something other than `false`. A predicate that
 * throws keeps the span.
 */
export class ShouldExportSpanProcessor implements SpanProcessor {
	constructor(
		private readonly delegate: SpanProcessor,
		private readonly shouldExportSpan: ShouldExportSpan,
	) {}

	onStart(span: Span, parentContext: Context): void {
		this.delegate.onStart(span, parentContext)
	}

	onEnd(span: ReadableSpan): void {
		let keep = true
		try {
			keep = this.shouldExportSpan(span) !== false
		} catch (e) {
			diag.error(
				'@launchdarkly/observability-node: shouldExportSpan threw; exporting the span',
				e,
			)
		}
		if (keep) {
			this.delegate.onEnd(span)
		}
	}

	forceFlush(): Promise<void> {
		return this.delegate.forceFlush()
	}

	shutdown(): Promise<void> {
		return this.delegate.shutdown()
	}
}
