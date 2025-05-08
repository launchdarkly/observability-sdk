import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import { MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS } from '../utils/graph'
import { ExportResult, ExportResultCode, merge } from '@opentelemetry/core'
import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { Attributes } from '@opentelemetry/api'
import { Sampler } from './sampling/Sampler'
import { CustomSampler } from './sampling/CustomSampler'
import { Maybe, SamplingConfig } from 'client/graph/generated/operations'
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

function cloneReadableSpanWithAttributes(span: ReadableSpan, attributes: Attributes): ReadableSpan {
	const spanContext = span.spanContext();
	const cloned = {
		name: span.name,
		kind: span.kind,
		spanContext: () => spanContext,
		parentSpanId: span.parentSpanId,
		startTime: span.startTime,
		endTime: span.endTime,
		status: span.status,
		attributes: merge(span.attributes, attributes),
		links: span.links,
		events: span.events,
		duration: span.duration,
		ended: span.ended,
		resource: span.resource,
		instrumentationLibrary: span.instrumentationLibrary,
		droppedAttributesCount: span.droppedAttributesCount,
		droppedEventsCount: span.droppedEventsCount,
		droppedLinksCount: span.droppedLinksCount,
	}
	return cloned;
}

export class OTLPTraceExporterBrowserWithXhrRetry extends OTLPTraceExporter {
	private readonly xhrTraceExporter: OTLPTraceExporter
	private readonly sampler: Sampler

	constructor(config?: TraceExporterConfig, samplingConfig?: Maybe<SamplingConfig>) {
		super(config)
		this.sampler = new CustomSampler(samplingConfig)
		this.xhrTraceExporter = new OTLPTraceExporter({
			...(config ?? {}),
			headers: {}, // a truthy value enables sending with XHR instead of beacon
		})
	}

	export(items: ReadableSpan[], resultCallback: (result: ExportResult) => void) {
		const omittedSpansIds: string[] = [];
		const spanById: Record<string, ReadableSpan> = {};
		const childrenByParentId: Record<string, string[]> = {};
	
		// The first pass we sample items which are directly impacted by a sampling decision.
		// We also build a map of children spans by parent span id, which allows us to quickly traverse the span tree.
		for (const item of items) {
			if(item.parentSpanId) {
				childrenByParentId[item.parentSpanId] = childrenByParentId[item.parentSpanId] || [];
				childrenByParentId[item.parentSpanId].push(item.spanContext().spanId);
			}
			console.log("Before sampling", item.name);
			const sampleResult = this.sampler.shouldSample(item);
			console.log('After sampling', item.name, 'sampleResult', sampleResult);
			if (sampleResult.sample) {
				if(sampleResult.attributes) {
					spanById[item.spanContext().spanId] = cloneReadableSpanWithAttributes(item, sampleResult.attributes);
				} else {
					spanById[item.spanContext().spanId] = item;
				}
			} else {
				omittedSpansIds.push(item.spanContext().spanId)
			}
		}

		// Find all children of spans that have been sampled out and remove them.
		// Repeat until there are no more children to remove.
		while(omittedSpansIds.length > 0) {
			console.log('omittedSpansIds', omittedSpansIds);
			const spanId = omittedSpansIds.shift();
			const affectedSpans: string[] | undefined = childrenByParentId[spanId!];
			if(!affectedSpans) {
				continue;
			}

			for(const spanIdToRemove of affectedSpans) {
				delete spanById[spanIdToRemove];
				omittedSpansIds.push(spanIdToRemove);
			}
		}
		const sampledItems = Object.values(spanById);
		
		let retries = 0
		const retry = (result: ExportResult) => {
			retries++
			if (retries > MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS) {
				console.error(
					`[highlight.io] failed to export OTeL traces: ${result.error?.message}`,
					result.error,
				)
				resultCallback({
					code: ExportResultCode.FAILED,
					error: result.error,
				})
			} else {
				this.xhrTraceExporter.export(sampledItems, resultCallback)
			}
		}

		super.export(sampledItems, retry)
	}
}

export class OTLPMetricExporterBrowser extends OTLPMetricExporter {
	private readonly xhrMeterExporter: OTLPMetricExporter

	constructor(config?: MetricExporterConfig) {
		super(config)
		this.xhrMeterExporter = new OTLPMetricExporter({
			...(config ?? {}),
			headers: {}, // a truthy value enables sending with XHR instead of beacon
		})
	}

	export(items: any, resultCallback: (result: ExportResult) => void) {
		let retries = 0
		const retry = (result: ExportResult) => {
			retries++
			if (retries > MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS) {
				console.error(
					`[highlight.io] failed to export OTeL metrics: ${result.error?.message}`,
					result.error,
				)
				resultCallback({
					code: ExportResultCode.FAILED,
					error: result.error,
				})
			} else {
				this.xhrMeterExporter.export(items, resultCallback)
			}
		}

		super.export(items, retry)
	}
}
