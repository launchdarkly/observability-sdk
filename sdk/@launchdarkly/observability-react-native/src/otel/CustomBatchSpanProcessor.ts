import { RECORD_ATTRIBUTE } from '@launchdarkly/observability-shared'
import {
	BatchSpanProcessor,
	BufferConfig,
	ReadableSpan,
	SpanExporter,
} from '@opentelemetry/sdk-trace-web'

export class CustomBatchSpanProcessor extends BatchSpanProcessor {
	constructor(
		exporter: SpanExporter,
		private options?: BufferConfig & { debug?: boolean },
	) {
		const { debug, ...rest } = options ?? {}
		super(exporter, rest)
	}

	onEnd(span: ReadableSpan): void {
		if (span.attributes[RECORD_ATTRIBUTE] === false) {
			this._log('span set to not record - skipping', span.name)
			return // don't record spans that are marked as not to be recorded
		}

		super.onEnd(span)
	}

	private _log(...data: any[]): void {
		if (this.options?.debug) {
			console.log('[CustomBatchSpanProcessor]', ...data)
		}
	}
}
