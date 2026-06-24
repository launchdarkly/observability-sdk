import { Span } from '@opentelemetry/api'
import { parseGraphQLOperation } from '@launchdarkly/observability-shared'
import { FetchCustomAttributeFunction } from '@opentelemetry/instrumentation-fetch'
import { NetworkRecordingOptions } from '../../api/Options'
import { sanitizeHeaders, sanitizeUrl } from './utils/network-sanitizer'
import { getBodyThatShouldBeRecorded } from './utils/xhr-listener'

const applyNetworkAttributes = (
	span: Span,
	url: string,
	recording: NetworkRecordingOptions,
	urlBlocklist: string[],
	requestHeaders: Record<string, string>,
	responseHeaders: Record<string, string>,
	requestBody: string | undefined,
	responseBody: string | undefined,
) => {
	// Always overwrite OTel-set URL attributes with sanitized version to
	// redact credentials and sensitive query params.
	if (url) {
		url = sanitizeUrl(url)
		span.setAttribute('http.url', url)
		span.setAttribute('url.full', url)
	}

	if (urlBlocklist.some((blocked) => url.toLowerCase().includes(blocked))) {
		return
	}

	// Give GraphQL requests a useful span name derived from the operation in the
	// request body. Every GraphQL call hits the same endpoint, so the default
	// OTel name ("HTTP POST" / "POST /graphql") carries no signal. This runs
	// regardless of `recordHeadersAndBody`: the operation name/type are
	// low-sensitivity and we only read them from the body, never store it.
	const gql = parseGraphQLOperation(requestBody)
	if (gql) {
		if (gql.name) {
			span.setAttribute('graphql.operation.name', gql.name)
		}
		if (gql.type) {
			span.setAttribute('graphql.operation.type', gql.type)
		}
		span.updateName(
			gql.name
				? `${gql.type ?? 'query'} ${gql.name}`
				: (gql.type ?? 'graphql'),
		)
	}

	if (!recording.recordHeadersAndBody) {
		return
	}

	const headersToRedact = (recording.networkHeadersToRedact ?? []).map((h) =>
		h.toLowerCase(),
	)
	const headersToRecord = recording.headerKeysToRecord?.map((h) =>
		h.toLowerCase(),
	)
	const bodyKeysToRedact = (recording.networkBodyKeysToRedact ?? []).map(
		(k) => k.toLowerCase(),
	)
	const bodyKeysToRecord = recording.bodyKeysToRecord?.map((k) =>
		k.toLowerCase(),
	)

	// Request headers
	const sanitizedReqHeaders = sanitizeHeaders(
		headersToRedact,
		requestHeaders,
		headersToRecord,
	)
	Object.entries(sanitizedReqHeaders).forEach(([key, value]) => {
		span.setAttribute(`http.request.header.${key}`, value)
	})

	// Request body
	if (requestBody !== undefined) {
		const sanitizedBody = getBodyThatShouldBeRecorded(
			requestBody,
			bodyKeysToRedact,
			bodyKeysToRecord,
			requestHeaders,
		)
		if (sanitizedBody != null) {
			span.setAttribute('http.request.body', sanitizedBody)
		}
	}

	// Response headers
	const sanitizedRespHeaders = sanitizeHeaders(
		headersToRedact,
		responseHeaders,
		headersToRecord,
	)
	Object.entries(sanitizedRespHeaders).forEach(([key, value]) => {
		span.setAttribute(`http.response.header.${key}`, value)
	})

	// Response body
	if (responseBody !== undefined) {
		const sanitizedBody = getBodyThatShouldBeRecorded(
			responseBody,
			bodyKeysToRedact,
			bodyKeysToRecord,
			responseHeaders,
		)
		if (sanitizedBody != null) {
			span.setAttribute('http.response.body', sanitizedBody)
		}
	}
}

export const FetchHook =
	(
		recording: NetworkRecordingOptions,
		urlBlocklist: string[],
	): FetchCustomAttributeFunction =>
	(span, request, result) => {
		const url = request instanceof Request ? request.url : ''

		let requestHeaders: Record<string, string> = {}
		if (request instanceof Request) {
			requestHeaders = Object.fromEntries(request.headers.entries())
		} else if (
			request &&
			typeof request === 'object' &&
			'headers' in request &&
			request.headers
		) {
			requestHeaders = Object.fromEntries(
				new Headers(request.headers as HeadersInit).entries(),
			)
		}

		const bodyInit =
			request instanceof Request
				? undefined
				: (request as RequestInit).body
		const requestBody = typeof bodyInit === 'string' ? bodyInit : undefined

		const responseHeaders =
			result instanceof Response
				? Object.fromEntries(result.headers.entries())
				: {}

		applyNetworkAttributes(
			span,
			url,
			recording,
			urlBlocklist,
			requestHeaders,
			responseHeaders,
			requestBody,
			undefined, // Fetch response body is a stream; not recorded
		)
	}

export const XHRHook =
	(recording: NetworkRecordingOptions, urlBlocklist: string[]) =>
	(span: Span, xhr: XMLHttpRequest) => {
		const url = (xhr as any)._url ?? ''

		const responseHeaders: Record<string, string> = {}
		xhr.getAllResponseHeaders()
			.trim()
			.split(/[\r\n]+/)
			.forEach((line) => {
				const parts = line.split(': ')
				const header = parts.shift()
				if (header) {
					responseHeaders[header] = parts.join(': ')
				}
			})

		const responseBody =
			xhr.responseType === '' || xhr.responseType === 'text'
				? xhr.responseText
				: undefined

		applyNetworkAttributes(
			span,
			url,
			recording,
			urlBlocklist,
			{}, // XHR does not expose request headers
			responseHeaders,
			undefined, // XHR request body is not accessible post-send
			responseBody,
		)
	}
