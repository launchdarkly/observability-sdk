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

export class OTLPTraceExporterBrowserWithXhrRetry extends OTLPTraceExporter {
	constructor(
		config: TraceExporterConfig,
		private readonly sampler: ExportSampler,
	) {
		super({
			...config,
			headers: {}, // a truthy value enables sending with XHR instead of beacon
		})
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
	constructor(config?: MetricExporterConfig) {
		super({
			...config,
			headers: {},
		})
	}

	export(items: any, resultCallback: (result: ExportResult) => void) {
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
