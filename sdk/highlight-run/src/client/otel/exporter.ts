import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	JsonMetricsSerializer,
	JsonTraceSerializer,
} from '@opentelemetry/otlp-transformer'
import {
	BACKOFF_DELAY_MS,
	BASE_DELAY_MS,
	MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS,
} from '../utils/graph'
import { ExportResult, ExportResultCode } from '@opentelemetry/core'
import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ResourceMetrics } from '@opentelemetry/sdk-metrics'
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

const trySendBeacon = (
	url: string,
	body: Uint8Array,
	contentType: string,
): boolean => {
	if (typeof navigator === 'undefined' || !navigator.sendBeacon) {
		return false
	}
	try {
		const blob = new Blob([body as BlobPart], { type: contentType })
		return navigator.sendBeacon(url, blob)
	} catch {
		return false
	}
}

const keepaliveFetchExport = (
	url: string,
	body: Uint8Array,
	contentType: string,
	resultCallback: (result: ExportResult) => void,
) => {
	const beaconFallback = (error: Error) => {
		// Both keepalive fetch and sendBeacon share a ~64KiB body limit in
		// most browsers, so sendBeacon rarely saves us from size overflow.
		// It can still succeed where keepalive fetch fails on CORS preflight
		// or when fetch is unavailable entirely, so try it as a last resort.
		if (trySendBeacon(url, body, contentType)) {
			return resultCallback({ code: ExportResultCode.SUCCESS })
		}
		resultCallback({ code: ExportResultCode.FAILED, error })
	}

	try {
		const blob = new Blob([body as BlobPart], { type: contentType })
		fetch(url, {
			method: 'POST',
			body: blob,
			keepalive: true,
			headers: { 'Content-Type': contentType },
			credentials: 'omit',
		}).then(
			(response) => {
				if (response.ok) {
					return resultCallback({ code: ExportResultCode.SUCCESS })
				}
				// Transport succeeded but server rejected; don't retry via
				// sendBeacon since the server would reject it the same way.
				resultCallback({
					code: ExportResultCode.FAILED,
					error: new Error(
						`keepalive fetch export failed with status ${response.status}`,
					),
				})
			},
			// Promise rejection path: keepalive body-size overflow, network
			// error, CORS preflight failure, etc. This is where keepalive's
			// ~64KiB overflow lands — per WHATWG Fetch §4.6 it's an async
			// network error, not a synchronous throw.
			(error: Error) => beaconFallback(error),
		)
	} catch (error) {
		// Synchronous path: fetch/Blob unavailable, malformed URL, etc.
		beaconFallback(error as Error)
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
			const serialized =
				JsonTraceSerializer.serializeRequest(sampledItems)
			if (!serialized || !this.url) {
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: new Error(
						'keepalive export failed: serializer returned empty or url missing',
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

	export(
		items: ResourceMetrics,
		resultCallback: (result: ExportResult) => void,
	) {
		if (this.unloading) {
			const serialized = JsonMetricsSerializer.serializeRequest(items)
			if (!serialized || !this.url) {
				return resultCallback({
					code: ExportResultCode.FAILED,
					error: new Error(
						'keepalive export failed: serializer returned empty or url missing',
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
