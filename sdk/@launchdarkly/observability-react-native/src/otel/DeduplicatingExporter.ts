import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'
import { SpanExporter } from '@opentelemetry/sdk-trace-web'
import { ExportResult } from '@opentelemetry/core'

export const FETCH_LIB = '@opentelemetry/instrumentation-fetch'
export const XHR_LIB = '@opentelemetry/instrumentation-xml-http-request'

export class DeduplicatingExporter implements SpanExporter {
	constructor(
		private readonly delegate: OTLPTraceExporter,
		private readonly debug?: boolean,
	) {}

	export(spans: ReadableSpan[], cb: (res: ExportResult) => void): void {
		const seenSpans = new Map<string, ReadableSpan>()
		const dedupedSpans: ReadableSpan[] = []

		for (const span of spans) {
			const sc = span.spanContext()
			const traceId = sc.traceId
			const lib = span.instrumentationLibrary.name
			const url = span.attributes['http.url']
			const method = span.attributes['http.method']

			if (!url || !method) {
				this._log('non-http span - continuing', span.name)
				dedupedSpans.push(span)
				continue
			}

			const reqId =
				lib === XHR_LIB && span.parentSpanId
					? span.parentSpanId // xhr requests can have a parent span from fetch instrumentation
					: sc.spanId

			const key = `${traceId}|${reqId}`
			const existing = seenSpans.get(key)

			if (!existing) {
				this._log('non-existing span - adding', span.name)
				seenSpans.set(key, span)
				dedupedSpans.push(span)
				continue
			}

			const existingLib = existing.instrumentationLibrary.name

			if (existingLib === FETCH_LIB && lib === XHR_LIB) {
				this._log(
					'incoming XHR w/ fetch span seen - dropping XHR',
					span.name,
				)
				continue
			}

			if (existingLib === XHR_LIB && lib === FETCH_LIB) {
				this._log(
					'incoming fetch w/ XHR span seen - replacing with fetch',
					span.name,
				)
				const idx = dedupedSpans.indexOf(existing)

				if (idx !== -1) {
					// Replace the XHR span with the fetch span
					dedupedSpans[idx] = span
				}

				seenSpans.set(key, span)
				continue
			}
		}

		this.delegate.export(dedupedSpans, cb)
	}

	shutdown() {
		return this.delegate.shutdown()
	}

	forceFlush() {
		return this.delegate.forceFlush()
	}

	private _log(...data: any[]): void {
		if (this.debug) {
			console.log('[DeduplicatingExporter]', ...data)
		}
	}
}
