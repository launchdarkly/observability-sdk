import * as api from '@opentelemetry/api'
import { Context, Span } from '@opentelemetry/api'
import {
	CompositePropagator,
	W3CBaggagePropagator,
	W3CTraceContextPropagator,
} from '@opentelemetry/core'
import {
	Instrumentation,
	registerInstrumentations,
} from '@opentelemetry/instrumentation'
import { DocumentLoadInstrumentation } from '@opentelemetry/instrumentation-document-load'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request'
import { Resource } from '@opentelemetry/resources'
import {
	BatchSpanProcessor,
	ConsoleSpanExporter,
	PropagateTraceHeaderCorsUrls,
	ReadableSpan,
	SimpleSpanProcessor,
	Span as SDKSpan,
	StackContextManager,
	WebTracerProvider,
} from '@opentelemetry/sdk-trace-web'
import * as SemanticAttributes from '@opentelemetry/semantic-conventions'
import { getResponseBody } from '../listeners/network-listener/utils/fetch-listener'
import {
	DEFAULT_URL_BLOCKLIST,
	safeParseUrl,
	sanitizeHeaders,
	sanitizeUrl,
} from '../listeners/network-listener/utils/network-sanitizer'
import {
	shouldNetworkRequestBeRecorded,
	shouldNetworkRequestBeTraced,
} from '../listeners/network-listener/utils/utils'
import {
	BrowserXHR,
	getBodyThatShouldBeRecorded,
} from '../listeners/network-listener/utils/xhr-listener'
import {
	NetworkRecordingOptions,
	OtelInstrumentatonOptions,
} from '../types/client'
import {
	OTLPMetricExporterBrowser,
	OTLPTraceExporterBrowserWithXhrRetry,
	TraceExporterConfig,
} from './exporter'
import { UserInteractionInstrumentation } from './user-interaction'
import {
	MeterProvider,
	PeriodicExportingMetricReader,
} from '@opentelemetry/sdk-metrics'
import { IntegrationClient } from '../../integrations'
import { LD_METRIC_NAME_DOCUMENT_LOAD } from '../../integrations/launchdarkly'
import version from '../../version'

import { ExportSampler } from './sampling/ExportSampler'
import { getPersistentSessionSecureID } from '../utils/sessionStorage/highlightSession'
import type { EventName } from '@opentelemetry/instrumentation-user-interaction'

export type Callback = (span?: Span) => any

export type BrowserTracingConfig = {
	projectId: string | number
	sessionSecureId: string
	otlpEndpoint: string
	backendUrl?: string
	environment?: string
	networkRecordingOptions?: NetworkRecordingOptions
	serviceName?: string
	serviceVersion?: string
	tracingOrigins?: boolean | (string | RegExp)[]
	urlBlocklist?: string[]
	eventNames?: EventName[]
	instrumentations?: OtelInstrumentatonOptions
	getIntegrations?: () => IntegrationClient[]
}

let providers: {
	tracerProvider?: WebTracerProvider
	meterProvider?: MeterProvider
} = {}
let otelConfig: BrowserTracingConfig | undefined

const RECORD_ATTRIBUTE = 'highlight.record'
const SESSION_ID_ATTRIBUTE = 'highlight.session_id'
export const LOG_SPAN_NAME = 'launchdarkly.js.log'

export const ATTR_EXCEPTION_ID = 'launchdarkly.exception.id'
export const ATTR_SAMPLING_RATIO = 'launchdarkly.sampling.ratio'
export const ATTR_LOG_SEVERITY = 'log.severity'
export const ATTR_LOG_MESSAGE = 'log.message'

export const setupBrowserTracing = (
	config: BrowserTracingConfig,
	sampler: ExportSampler,
) => {
	if (providers.tracerProvider || providers.meterProvider) {
		console.warn('OTEL already initialized. Skipping...')
		return
	}
	otelConfig = config

	const backendUrl =
		config.backendUrl ||
		import.meta.env.REACT_APP_PUBLIC_GRAPH_URI ||
		'https://pub.highlight.io'

	const urlBlocklist = [
		...(config.networkRecordingOptions?.urlBlocklist ?? []),
		...DEFAULT_URL_BLOCKLIST,
	]
	const isDebug = import.meta.env.DEBUG === 'true'
	const environment = config.environment ?? 'production'

	const exporterOptions: TraceExporterConfig = {
		url: config.otlpEndpoint + '/v1/traces',
		concurrencyLimit: 10,
		timeoutMillis: 30_000,
		// Using any because we were getting an error importing CompressionAlgorithm
		// from @opentelemetry/otlp-exporter-base.
		compression: 'gzip' as any,
		keepAlive: true,
		httpAgentOptions: {
			timeout: 60_000,
			keepAlive: true,
		},
	}
	const exporter = new OTLPTraceExporterBrowserWithXhrRetry(
		exporterOptions,
		sampler,
	)

	// https://opentelemetry.io/docs/specs/otel/logs/sdk/
	const spanProcessor = new CustomBatchSpanProcessor(exporter, {
		maxExportBatchSize: 1024, // Default value from SDK is 512
		maxQueueSize: 2048, // Default value from SDK is 2048
		exportTimeoutMillis: exporterOptions.timeoutMillis, // Default value from SDK is 30_000
		scheduledDelayMillis: exporterOptions.timeoutMillis, // Default value from SDK is 1000
	})

	const resource = new Resource({
		[SemanticAttributes.ATTR_SERVICE_NAME]:
			config.serviceName ?? 'highlight-browser',
		[SemanticAttributes.ATTR_SERVICE_VERSION]: config.serviceVersion,
		'deployment.environment.name': environment,
		'highlight.project_id': config.projectId,
		[SemanticAttributes.ATTR_USER_AGENT_ORIGINAL]: navigator.userAgent,
		'browser.language': navigator.language,
		'telemetry.distro.name': '@highlight-run/observability',
		'telemetry.distro.version': version,
	})

	providers.tracerProvider = new WebTracerProvider({
		resource,
		mergeResourceWithDefaults: true,
		spanProcessors: isDebug
			? [
					new SimpleSpanProcessor(new ConsoleSpanExporter()),
					spanProcessor,
				]
			: [spanProcessor],
	})
	api.trace.setGlobalTracerProvider(providers.tracerProvider)

	const meterExporter = new OTLPMetricExporterBrowser({
		...exporterOptions,
		url: config.otlpEndpoint + '/v1/metrics',
	})
	const reader = new PeriodicExportingMetricReader({
		exporter: meterExporter,
		exportIntervalMillis: exporterOptions.timeoutMillis,
		exportTimeoutMillis: exporterOptions.timeoutMillis,
	})

	providers.meterProvider = new MeterProvider({ resource, readers: [reader] })
	api.metrics.setGlobalMeterProvider(providers.meterProvider)

	// TODO: allow passing in custom instrumentations/configurations
	let instrumentations: Instrumentation[] = []

	const documentLoadConfig =
		config.instrumentations?.[
			'@opentelemetry/instrumentation-document-load'
		]
	if (documentLoadConfig !== false) {
		instrumentations.push(
			new DocumentLoadInstrumentation({
				applyCustomAttributesOnSpan: {
					documentLoad: assignDocumentDurations,
					documentFetch: assignDocumentDurations,
					resourceFetch: assignResourceFetchDurations,
				},
			}),
		)
	}

	const userInteractionConfig =
		config.instrumentations?.[
			'@opentelemetry/instrumentation-user-interaction'
		]
	if (userInteractionConfig !== false) {
		instrumentations.push(
			new UserInteractionInstrumentation({
				eventNames: config.eventNames,
			}),
		)
	}

	if (config.networkRecordingOptions?.enabled) {
		const fetchInstrumentationConfig =
			config.instrumentations?.['@opentelemetry/instrumentation-fetch']
		if (fetchInstrumentationConfig !== false) {
			const fetchInstrumentation = new FetchInstrumentation({
				propagateTraceHeaderCorsUrls: getCorsUrlsPattern(
					config.tracingOrigins,
				),
				applyCustomAttributesOnSpan: async (
					span,
					request,
					response,
				) => {
					if (!(span as any).attributes) {
						return
					}
					const readableSpan = span as unknown as ReadableSpan
					if (readableSpan.attributes[RECORD_ATTRIBUTE] === false) {
						return
					}

					enhanceSpanWithHttpRequestAttributes(
						span,
						request.body,
						request.headers,
						config.networkRecordingOptions,
					)

					if (!(response instanceof Response)) {
						span.setAttributes({
							'http.response.error': response.message,
							[SemanticAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE]:
								response.status,
						})
						return
					}

					if (config.networkRecordingOptions?.recordHeadersAndBody) {
						const responseBody = await getResponseBody(
							response,
							config.networkRecordingOptions?.bodyKeysToRecord,
							config.networkRecordingOptions
								?.networkBodyKeysToRedact,
						)
						const responseHeaders = Object.fromEntries(
							response.headers.entries(),
						)

						enhanceSpanWithHttpResponseAttributes(
							span,
							responseHeaders,
							responseBody,
							config.networkRecordingOptions,
						)
					}
				},
			})

			// The upstream FetchInstrumentation calls applyCustomAttributesOnSpan
			// via safeExecuteInTheMiddle (sync), which does not await async callbacks.
			// This means the span ends before the response body is read, silently
			// dropping the http.response.body attribute. Patch _applyAttributesAfterFetch
			// and _endSpan to properly await async callbacks before ending the span.
			const pendingAttributes = new Map<api.Span, Promise<void>>()
			const inst = fetchInstrumentation as any
			const origEndSpan = inst.__proto__._endSpan

			inst._applyAttributesAfterFetch = function (
				span: api.Span,
				request: Request | RequestInit,
				result: Response | Error,
			) {
				const applyCustomAttributesOnSpan =
					this.getConfig().applyCustomAttributesOnSpan
				if (applyCustomAttributesOnSpan) {
					try {
						const maybePromise = applyCustomAttributesOnSpan(
							span,
							request,
							result,
						)
						if (
							maybePromise &&
							typeof (maybePromise as any).then === 'function'
						) {
							pendingAttributes.set(
								span,
								(maybePromise as Promise<void>).catch(() => {}),
							)
						}
					} catch {
						// match safeExecuteInTheMiddle behavior
					}
				}
			}

			inst._endSpan = function (
				span: api.Span,
				spanData: unknown,
				response: unknown,
			) {
				const pending = pendingAttributes.get(span)
				if (pending) {
					pending.finally(() => {
						pendingAttributes.delete(span)
						origEndSpan.call(this, span, spanData, response)
					})
				} else {
					origEndSpan.call(this, span, spanData, response)
				}
			}

			instrumentations.push(fetchInstrumentation)
		}

		const xmlInstrumentationConfig =
			config.instrumentations?.[
				'@opentelemetry/instrumentation-xml-http-request'
			]
		if (xmlInstrumentationConfig !== false) {
			instrumentations.push(
				new XMLHttpRequestInstrumentation({
					propagateTraceHeaderCorsUrls: getCorsUrlsPattern(
						config.tracingOrigins,
					),
					applyCustomAttributesOnSpan: (span, xhr) => {
						const browserXhr = xhr as BrowserXHR
						if (!(span as any).attributes) {
							return
						}
						const readableSpan = span as unknown as ReadableSpan
						if (
							readableSpan.attributes[RECORD_ATTRIBUTE] === false
						) {
							return
						}

						enhanceSpanWithHttpRequestAttributes(
							span,
							browserXhr._body,
							browserXhr._requestHeaders as Headers,
							config.networkRecordingOptions,
						)

						if (
							config.networkRecordingOptions?.recordHeadersAndBody
						) {
							let responseBody = ''
							if (
								xhr.responseType === '' ||
								xhr.responseType === 'text'
							) {
								responseBody = getBodyThatShouldBeRecorded(
									xhr.responseText,
									config.networkRecordingOptions
										?.networkBodyKeysToRedact,
									config.networkRecordingOptions
										?.bodyKeysToRecord,
									undefined,
								)
							}

							const responseHeaders = parseXhrResponseHeaders(
								xhr.getAllResponseHeaders(),
							)

							enhanceSpanWithHttpResponseAttributes(
								span,
								responseHeaders,
								responseBody,
								config.networkRecordingOptions,
							)
						}
					},
				}),
			)
		}
	}

	registerInstrumentations({ instrumentations })

	const contextManager = new StackContextManager()
	contextManager.enable()

	providers.tracerProvider.register({
		contextManager,
		propagator: new CompositePropagator({
			propagators: [
				new W3CBaggagePropagator(),
				new CustomTraceContextPropagator({
					backendUrl,
					otlpEndpoint: config.otlpEndpoint,
					tracingOrigins: config.tracingOrigins,
					urlBlocklist,
				}),
			],
		}),
	})

	return providers
}

class CustomBatchSpanProcessor extends BatchSpanProcessor {
	onStart(span: SDKSpan, parentContext: Context): void {
		span.setAttribute(SESSION_ID_ATTRIBUTE, getPersistentSessionSecureID())
		super.onStart(span, parentContext)
	}

	onEnd(span: ReadableSpan): void {
		if (span.attributes[RECORD_ATTRIBUTE] === false) {
			return // don't record spans that are marked as not to be recorded
		}

		super.onEnd(span)
	}
}

type CustomTraceContextPropagatorConfig = {
	backendUrl: string
	otlpEndpoint: string
	tracingOrigins: BrowserTracingConfig['tracingOrigins']
	urlBlocklist: string[]
}

class CustomTraceContextPropagator extends W3CTraceContextPropagator {
	private highlightEndpoints: string[]
	private tracingOrigins: BrowserTracingConfig['tracingOrigins']
	private urlBlocklist: string[]

	constructor(config: CustomTraceContextPropagatorConfig) {
		super()

		this.highlightEndpoints = [
			config.backendUrl,
			`${config.otlpEndpoint}/v1/traces`,
			`${config.otlpEndpoint}/v1/logs`,
			`${config.otlpEndpoint}/v1/metrics`,
		]
		this.tracingOrigins = config.tracingOrigins
		this.urlBlocklist = config.urlBlocklist
	}

	inject(
		context: api.Context,
		carrier: unknown,
		setter: api.TextMapSetter,
	): void {
		const span = api.trace.getSpan(context)
		if (!span || !(span as any).attributes) {
			return
		}

		const url = getUrlFromSpan(span as unknown as ReadableSpan)
		if (typeof url === 'string') {
			const shouldRecord = shouldRecordRequest(
				url,
				this.highlightEndpoints,
				this.tracingOrigins,
				this.urlBlocklist,
			)

			if (!shouldRecord) {
				span.setAttribute(RECORD_ATTRIBUTE, false) // used later to avoid additional processing
			}

			const shouldTrace = shouldNetworkRequestBeTraced(
				url,
				this.tracingOrigins ?? [],
				this.urlBlocklist,
			)
			if (!shouldTrace) {
				return // return early to prevent headers from being injected
			}
		}

		super.inject(context, carrier, setter)
	}
}

export const BROWSER_TRACER_NAME = 'highlight-browser'
export const BROWSER_METER_NAME = BROWSER_TRACER_NAME
export const getTracer = () => {
	return providers.tracerProvider?.getTracer(BROWSER_TRACER_NAME)
}
export const getMeter = () => {
	return providers.meterProvider?.getMeter(BROWSER_METER_NAME)
}

export const getActiveSpan = () => {
	return api.trace.getActiveSpan()
}

export const getActiveSpanContext = () => {
	return api.context.active()
}

export const shutdown = async () => {
	await Promise.allSettled([
		(async () => {
			if (providers.tracerProvider) {
				await providers.tracerProvider.shutdown()
				providers.tracerProvider = undefined
			} else {
				console.warn(
					'OTEL shutdown called without initialized tracerProvider.',
				)
			}
		})(),
		(async () => {
			if (providers.meterProvider) {
				await providers.meterProvider.shutdown()
				providers.meterProvider = undefined
			} else {
				console.warn(
					'OTEL shutdown called without initialized meterProvider.',
				)
			}
		})(),
	])
}

const enhanceSpanWithHttpRequestAttributes = (
	span: api.Span,
	body: Request['body'] | RequestInit['body'] | BrowserXHR['_body'],
	headers:
		| Headers
		| RequestInit['headers']
		| ReturnType<XMLHttpRequest['getAllResponseHeaders']>,
	networkRecordingOptions?: NetworkRecordingOptions,
) => {
	if (!(span as any).attributes) {
		return
	}
	const readableSpan = span as unknown as ReadableSpan
	const url = getUrlFromSpan(readableSpan)
	const sanitizedUrl = sanitizeUrl(url)
	const sanitizedUrlObject = safeParseUrl(sanitizedUrl)

	const stringBody = typeof body === 'string' ? body : String(body)
	try {
		const parsedBody = body ? JSON.parse(stringBody) : undefined
		if (parsedBody?.operationName) {
			span.setAttribute(
				'graphql.operation.name',
				parsedBody.operationName,
			)
		}
	} catch {
		// Ignore parsing errors
	}

	span.setAttributes({
		'highlight.type': 'http.request',
		[SemanticAttributes.SEMATTRS_HTTP_URL]: sanitizedUrl, // overwrite with sanitized version
		[SemanticAttributes.ATTR_URL_FULL]: sanitizedUrl,
		[SemanticAttributes.ATTR_URL_PATH]: sanitizedUrlObject.pathname,
		[SemanticAttributes.ATTR_URL_QUERY]: sanitizedUrlObject.search,
	})

	// Set sanitized query params as JSON object for easier querying
	const searchParamsEntries = Array.from(
		sanitizedUrlObject.searchParams.entries(),
	)
	if (searchParamsEntries.length > 0) {
		span.setAttribute(
			'url.query_params',
			JSON.stringify(Object.fromEntries(searchParamsEntries)),
		)
	}

	if (networkRecordingOptions?.recordHeadersAndBody) {
		const requestBody = getBodyThatShouldBeRecorded(
			body,
			networkRecordingOptions.networkBodyKeysToRedact,
			networkRecordingOptions.bodyKeysToRecord,
			headers as Headers,
		)
		span.setAttribute('http.request.body', requestBody)

		const sanitizedHeaders = sanitizeHeaders(
			networkRecordingOptions.networkHeadersToRedact ?? [],
			headers as Headers,
			networkRecordingOptions.headerKeysToRecord,
		)

		const headerAttributes = convertHeadersToOtelAttributes(
			sanitizedHeaders,
			'http.request.header',
		)
		span.setAttributes(headerAttributes)
	}
}

export const parseXhrResponseHeaders = (
	headerString: string,
): { [key: string]: string } => {
	const headers: { [key: string]: string } = {}
	if (headerString) {
		headerString
			.trim()
			.split(/[\r\n]+/)
			.forEach((line) => {
				const parts = line.split(': ')
				const header = parts.shift()
				if (header) {
					headers[header] = parts.join(': ')
				}
			})
	}
	return headers
}

/**
 * Converts headers object to OpenTelemetry semantic convention format.
 * Headers are set as individual attributes with the pattern:
 * - http.request.header.<lowercase-name>: value (or [value1, value2] if multiple)
 * - http.response.header.<lowercase-name>: value (or [value1, value2] if multiple)
 *
 * According to OTel spec, header values should be arrays when they contain
 * comma-separated values. Single values remain as strings for simpler querying.
 *
 * @param headers - Object with header key-value pairs
 * @param prefix - Either 'http.request.header' or 'http.response.header'
 * @returns Object with OTel semantic convention attribute names
 */
export const convertHeadersToOtelAttributes = (
	headers: { [key: string]: string },
	prefix: 'http.request.header' | 'http.response.header',
): { [key: string]: string | string[] } => {
	const attributes: { [key: string]: string | string[] } = {}

	Object.entries(headers).forEach(([key, value]) => {
		const normalizedKey = key.toLowerCase().replace(/_/g, '-')
		const attributeName = `${prefix}.${normalizedKey}`
		const values = splitHeaderValue(normalizedKey, value)

		// Handle duplicate header keys (same header appearing multiple times)
		if (attributes[attributeName]) {
			const existing = attributes[attributeName]

			if (Array.isArray(existing)) {
				attributes[attributeName] = [...existing, ...values]
			} else {
				attributes[attributeName] = [existing, ...values]
			}
		} else {
			attributes[attributeName] = values.length === 1 ? values[0] : values
		}
	})

	return attributes
}

/**
 * HTTP headers that are explicitly defined as comma-separated lists
 * per RFC 7231 and related specifications. Only these headers should
 * be split by comma. Other headers (especially date headers like
 * Date, Last-Modified, Expires) contain commas as part of their value
 * and should NOT be split.
 *
 * @see https://www.rfc-editor.org/rfc/rfc7231
 * @see https://www.rfc-editor.org/rfc/rfc7230#section-3.2.6
 */
const COMMA_SEPARATED_HEADERS = new Set([
	'accept',
	'accept-charset',
	'accept-encoding',
	'accept-language',
	'accept-ranges',
	'allow',
	'cache-control',
	'connection',
	'content-encoding',
	'content-language',
	'expect',
	'if-match',
	'if-none-match',
	'pragma',
	'proxy-authenticate',
	'te',
	'trailer',
	'transfer-encoding',
	'upgrade',
	'vary',
	'via',
	'warning',
	'www-authenticate',
	'access-control-allow-headers',
	'access-control-allow-methods',
	'access-control-expose-headers',
	'access-control-request-headers',
])

/**
 * Splits a header value by commas if the header is defined as comma-separated.
 * Headers like Date, Last-Modified, Expires contain commas in RFC 7231 date
 * format (e.g., "Mon, 01 Jan 2024 12:00:00 GMT") and should NOT be split.
 *
 * @param headerName - The lowercase header name
 * @param value - The header value string
 * @returns Array of values (single element for non-comma-separated headers)
 */
export const splitHeaderValue = (
	headerName: string,
	value: string,
): string[] => {
	// Only split headers that are explicitly defined as comma-separated lists
	if (!COMMA_SEPARATED_HEADERS.has(headerName)) {
		return [value]
	}

	// Split by comma and trim whitespace from each value
	return value
		.split(',')
		.map((v) => v.trim())
		.filter((v) => v.length > 0)
}

const enhanceSpanWithHttpResponseAttributes = (
	span: api.Span,
	responseHeaders: { [key: string]: string },
	responseBody: string,
	networkRecordingOptions: NetworkRecordingOptions,
) => {
	span.setAttribute('http.response.body', responseBody)

	const sanitizedResponseHeaders = sanitizeHeaders(
		networkRecordingOptions.networkHeadersToRedact ?? [],
		responseHeaders,
		networkRecordingOptions.headerKeysToRecord,
	)

	const headerAttributes = convertHeadersToOtelAttributes(
		sanitizedResponseHeaders,
		'http.response.header',
	)
	span.setAttributes(headerAttributes)
}

const shouldRecordRequest = (
	url: string,
	highlightEndpoints: string[],
	tracingOrigins: BrowserTracingConfig['tracingOrigins'],
	urlBlocklist: string[],
) => {
	const shouldRecordHeaderAndBody = !urlBlocklist?.some((blockedUrl) =>
		url.toLowerCase().includes(blockedUrl),
	)
	if (!shouldRecordHeaderAndBody) {
		return false
	}

	return shouldNetworkRequestBeRecorded(
		url,
		highlightEndpoints,
		tracingOrigins,
	)
}

const assignDocumentDurations = (span: api.Span) => {
	if (!(span as any).events) {
		return
	}
	const readableSpan = span as unknown as ReadableSpan
	const events = readableSpan.events

	const durations_ns = {
		unload: calculateDuration('unloadEventStart', 'unloadEventEnd', events),
		dom_interactive: calculateDuration(
			'fetchStart',
			'domInteractive',
			events,
		),
		dom_content_loaded: calculateDuration(
			'domContentLoadedEventStart',
			'domContentLoadedEventEnd',
			events,
		),
		dom_complete: calculateDuration('fetchStart', 'domComplete', events),
		load_event: calculateDuration('loadEventStart', 'loadEventEnd', events),
		document_load: calculateDuration('fetchStart', 'loadEventEnd', events),
		first_paint: calculateDuration('fetchStart', 'firstPaint', events),
		first_contentful_paint: calculateDuration(
			'fetchStart',
			'firstContentfulPaint',
			events,
		),
		domain_lookup: calculateDuration(
			'domainLookupStart',
			'domainLookupEnd',
			events,
		),
		connect: calculateDuration('connectStart', 'connectEnd', events),
		request: calculateDuration('requestStart', 'requestEnd', events),
		response: calculateDuration('responseStart', 'responseEnd', events),
	}
	const integrations = otelConfig?.getIntegrations?.() ?? []
	if (durations_ns.document_load !== undefined) {
		for (const _integration of integrations) {
			_integration.recordGauge(otelConfig?.sessionSecureId ?? '', {
				name: LD_METRIC_NAME_DOCUMENT_LOAD,
				value: durations_ns.document_load / 1e6,
			})
		}
	}

	Object.entries(durations_ns).forEach(([key, value]) => {
		if (value !== undefined) {
			span.setAttribute(`timings.${key}.ns`, value)
			span.setAttribute(
				`timings.${key}.readable`,
				humanizeDuration(value),
			)
		}
	})
}

type TimeEvent = {
	name: string
	time: [number, number] // seconds since epoch, nano seconds
}

function calculateDuration(
	startEventName: string,
	endEventName: string,
	events: TimeEvent[],
) {
	const startEvent = events.find((e) => e.name === startEventName)
	const endEvent = events.find((e) => e.name === endEventName)

	if (!startEvent || !endEvent) {
		return undefined
	}

	const startNs = startEvent.time[0] * 1e9 + startEvent.time[1]
	const endNs = endEvent.time[0] * 1e9 + endEvent.time[1]
	return endNs - startNs
}

const assignResourceFetchDurations = (
	span: api.Span,
	resource: PerformanceResourceTiming,
) => {
	const durations = {
		domain_lookup:
			(resource.domainLookupEnd - resource.domainLookupStart) * 1e9,
		connect: (resource.connectEnd - resource.connectStart) * 1e9,
		request: (resource.responseEnd - resource.requestStart) * 1e9,
		response: (resource.responseEnd - resource.responseStart) * 1e9,
	}

	Object.entries(durations).forEach(([key, value]) => {
		span.setAttribute(`timings.${key}.ns`, value)
		span.setAttribute(`timings.${key}.readable`, humanizeDuration(value))
	})
}

// Transform a raw value to a human readable string with nanosecond precision.
// Use the highest unit that results in a value greater than 1.
const humanizeDuration = (nanoseconds: number): string => {
	const microsecond = 1000
	const millisecond = microsecond * 1000
	const second = millisecond * 1000
	const minute = second * 60
	const hour = minute * 60

	if (nanoseconds >= hour) {
		const hours = nanoseconds / hour
		return `${Number(hours.toFixed(1))}h`
	} else if (nanoseconds >= minute) {
		const minutes = nanoseconds / minute
		return `${Number(minutes.toFixed(1))}m`
	} else if (nanoseconds >= second) {
		const seconds = nanoseconds / second
		return `${Number(seconds.toFixed(1))}s`
	} else if (nanoseconds >= millisecond) {
		const milliseconds = nanoseconds / millisecond
		return `${Number(milliseconds.toFixed(1))}ms`
	} else if (nanoseconds >= microsecond) {
		const microseconds = nanoseconds / microsecond
		return `${Number(microseconds.toFixed(1))}µs`
	} else {
		return `${Number(nanoseconds.toFixed(1))}ns`
	}
}

export const getCorsUrlsPattern = (
	tracingOrigins: BrowserTracingConfig['tracingOrigins'],
): PropagateTraceHeaderCorsUrls => {
	if (tracingOrigins === true) {
		return [/localhost/, /^\//, new RegExp(window.location.host)]
	} else if (Array.isArray(tracingOrigins)) {
		return tracingOrigins.map((pattern) =>
			typeof pattern === 'string' ? new RegExp(pattern) : pattern,
		)
	}

	return /^$/ // Match nothing if tracingOrigins is false or undefined
}

const getUrlFromSpan = (span: ReadableSpan) => {
	if (span.attributes[SemanticAttributes.ATTR_URL_FULL]) {
		return span.attributes[SemanticAttributes.ATTR_URL_FULL] as string
	}

	return span.attributes[SemanticAttributes.SEMATTRS_HTTP_URL] as string
}
