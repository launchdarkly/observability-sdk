import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	BACKOFF_DELAY_MS,
	BASE_DELAY_MS,
	MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS,
} from '../utils/graph'
import { ExportResult, ExportResultCode } from '@opentelemetry/core'
import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ExportSampler } from './sampling/ExportSampler'
import { sampleSpans } from './sampling/sampleSpans'

export type TraceExporterConfig = ConstructorParameters<
	typeof OTLPTraceExporter
>[0]
export type MetricExporterConfig = ConstructorParameters<
	typeof OTLPMetricExporter
>[0]

// This custom exporter is a temporary workaround for an issue we are having
// with requests stalling in the browser using the sendBeacon API. There is work
// being done to improve this by the OTEL team, but in the meantime we are using
// this custom exporter which will retry failed requests and send the data with
// an XHR request. More info:
// - https://github.com/open-telemetry/opentelemetry-js/issues/3489
// - https://github.com/open-telemetry/opentelemetry-js/blob/cf8edbed43c3e54eadcafe6fc6f39a1d03c89aa7/experimental/packages/otlp-exporter-base/src/platform/browser/OTLPExporterBrowserBase.ts#L51-L52
//
// For unload-triggered flushes (pagehide / visibilitychange: hidden) the XHR
// transport is no good because the browser cancels in-flight XHRs once the
// page starts unloading. In that path we bypass XHR and POST directly with
// `fetch(url, { keepalive: true })`, which the browser keeps alive across the
// navigation. Callers toggle this mode via `setUnloading(true)` before
// invoking forceFlush().

const keepaliveFetchExport = (
	url: string,
	body: Uint8Array | string,
	contentType: string,
	resultCallback: (result: ExportResult) => void,
) => {
	try {
		const blob = new Blob([body as BlobPart], { type: contentType })
		fetch(url, {
			method: 'POST',
			body: blob,
			keepalive: true,
			headers: { 'Content-Type': contentType },
			credentials: 'omit',
		}).then(
			(response) =>
				resultCallback({
					code: response.ok
						? ExportResultCode.SUCCESS
						: ExportResultCode.FAILED,
					error: response.ok
						? undefined
						: new Error(
								`keepalive fetch export failed with status ${response.status}`,
							),
				}),
			(error) =>
				resultCallback({
					code: ExportResultCode.FAILED,
					error,
				}),
		)
	} catch (error) {
		// fetch with keepalive has a per-request body size limit
		// (browser-dependent, typically 64KB). Fall back to sendBeacon which
		// has similar constraints but exists on older browsers.
		if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
			const blob = new Blob([body as BlobPart], { type: contentType })
			const queued = navigator.sendBeacon(url, blob)
			resultCallback({
				code: queued
					? ExportResultCode.SUCCESS
					: ExportResultCode.FAILED,
				error: queued ? undefined : (error as Error),
			})
			return
		}
		resultCallback({
			code: ExportResultCode.FAILED,
			error: error as Error,
		})
	}
}

export class OTLPTraceExporterBrowserWithXhrRetry extends OTLPTraceExporter {
	private readonly url: string
	private unloading = false

	constructor(
		config: TraceExporterConfig,
		private readonly sampler: ExportSampler,
	) {
		super({
			...config,
			headers: {}, // a truthy value enables sending with XHR instead of beacon
		})
		this.url = config?.url ?? ''
	}

	setUnloading(unloading: boolean) {
		this.unloading = unloading
	}

	export(
		items: ReadableSpan[],
		resultCallback: (result: ExportResult) => void,
	) {
		const sampledItems = sampleSpans(items, this.sampler)
		// Sampling removed all items and there is nothing to export.
		if (sampledItems.length === 0) {
			return
		}

		if (this.unloading) {
			const serializer = (this as any)._delegate?._serializer
			const serialized = serializer?.serializeRequest(sampledItems)
			if (!serialized || !this.url) {
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: new Error(
						'keepalive export failed: missing serializer or url',
					),
				})
			}
			keepaliveFetchExport(
				this.url,
				serialized,
				'application/json',
				resultCallback,
			)
			return
		}

		let retries = 0
		const retry = (result: ExportResult) => {
			if (result.code === ExportResultCode.SUCCESS) {
				return resultCallback({
					code: ExportResultCode.SUCCESS,
				})
			}
			retries++
			if (retries > MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS) {
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: result.error,
				})
			} else {
				new Promise((resolve) =>
					setTimeout(
						resolve,
						BASE_DELAY_MS + BACKOFF_DELAY_MS * Math.pow(2, retries),
					),
				).then(() => {
					super.export(sampledItems, retry)
				})
			}
		}

		super.export(sampledItems, retry)
	}
}

export class OTLPMetricExporterBrowser extends OTLPMetricExporter {
	private readonly url: string
	private unloading = false

	constructor(config?: MetricExporterConfig) {
		super({
			...config,
			headers: {},
		})
		this.url = config?.url ?? ''
	}

	setUnloading(unloading: boolean) {
		this.unloading = unloading
	}

	export(items: any, resultCallback: (result: ExportResult) => void) {
		if (this.unloading) {
			const serializer = (this as any)._delegate?._serializer
			const serialized = serializer?.serializeRequest(items)
			if (!serialized || !this.url) {
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: new Error(
						'keepalive export failed: missing serializer or url',
					),
				})
			}
			keepaliveFetchExport(
				this.url,
				serialized,
				'application/json',
				resultCallback,
			)
			return
		}

		let retries = 0
		const retry = (result: ExportResult) => {
			if (result.code === ExportResultCode.SUCCESS) {
				return resultCallback({
					code: ExportResultCode.SUCCESS,
				})
			}
			retries++
			if (retries > MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS) {
				console.error(
					`[@launchdarkly/observability] failed to export OTeL metrics: ${result.error?.message}`,
					result.error,
				)
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: result.error,
				})
			} else {
				new Promise((resolve) =>
					setTimeout(
						resolve,
						BASE_DELAY_MS + BACKOFF_DELAY_MS * Math.pow(2, retries),
					),
				).then(() => {
					console.warn(
						`[@launchdarkly/observability] retry ${retries}, failed to export OTeL metrics: ${result.error?.message}`,
						result.error,
					)
					super.export(items, retry)
				})
			}
		}

		super.export(items, retry)
	}
}
