import { Context } from '@opentelemetry/api'
import { LogRecordProcessor, SdkLogRecord } from '@opentelemetry/sdk-logs'
import { ReadableSpan, Span, SpanProcessor } from '@opentelemetry/sdk-trace-web'

type Logger = (...data: any[]) => void

function runSafely(log: Logger, name: string, fn: () => void): void {
	try {
		fn()
	} catch (error) {
		log(`custom processor threw in ${name}:`, error)
	}
}

async function runSafelyAsync(
	log: Logger,
	name: string,
	fn: () => Promise<void>,
): Promise<void> {
	try {
		await fn()
	} catch (error) {
		log(`custom processor threw in ${name}:`, error)
	}
}

/**
 * Wraps a user-provided span processor so that a throwing or rejecting
 * processor can never break the SDK pipeline or the export of the span.
 */
export class SafeSpanProcessor implements SpanProcessor {
	constructor(
		private readonly delegate: SpanProcessor,
		private readonly log: Logger,
	) {}

	onStart(span: Span, parentContext: Context): void {
		runSafely(this.log, 'onStart', () =>
			this.delegate.onStart(span, parentContext),
		)
	}

	onEnding(span: Span): void {
		if (this.delegate.onEnding) {
			runSafely(this.log, 'onEnding', () => this.delegate.onEnding!(span))
		}
	}

	onEnd(span: ReadableSpan): void {
		runSafely(this.log, 'onEnd', () => this.delegate.onEnd(span))
	}

	forceFlush(): Promise<void> {
		return runSafelyAsync(this.log, 'forceFlush', () =>
			this.delegate.forceFlush(),
		)
	}

	shutdown(): Promise<void> {
		return runSafelyAsync(this.log, 'shutdown', () =>
			this.delegate.shutdown(),
		)
	}
}

/**
 * Wraps a user-provided log record processor so that a throwing or rejecting
 * processor can never break the SDK pipeline or the export of the record.
 */
export class SafeLogRecordProcessor implements LogRecordProcessor {
	constructor(
		private readonly delegate: LogRecordProcessor,
		private readonly log: Logger,
	) {}

	onEmit(logRecord: SdkLogRecord, context?: Context): void {
		runSafely(this.log, 'onEmit', () =>
			this.delegate.onEmit(logRecord, context),
		)
	}

	forceFlush(): Promise<void> {
		return runSafelyAsync(this.log, 'forceFlush', () =>
			this.delegate.forceFlush(),
		)
	}

	shutdown(): Promise<void> {
		return runSafelyAsync(this.log, 'shutdown', () =>
			this.delegate.shutdown(),
		)
	}
}
