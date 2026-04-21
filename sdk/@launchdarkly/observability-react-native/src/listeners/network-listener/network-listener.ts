import { Span } from '@opentelemetry/api'
import { FetchCustomAttributeFunction } from '@opentelemetry/instrumentation-fetch'
import { NetworkRecordingOptions } from '../../api/Options'
import { sanitizeHeaders } from './utils/network-sanitizer'
import { getBodyThatShouldBeRecorded } from './utils/xhr-listener'

export const FetchHook =
	(
		recording: NetworkRecordingOptions,
		urlBlocklist: string[],
	): FetchCustomAttributeFunction =>
	(span, request, result) => {
		const url = request instanceof Request ? request.url : ''
		if (
			urlBlocklist.some((blocked) => url.toLowerCase().includes(blocked))
		) {
			return
		}

		const headersToRedact = (recording.networkHeadersToRedact ?? []).map(
			(h) => h.toLowerCase(),
		)
		const headersToRecord = recording.headerKeysToRecord?.map((h) =>
			h.toLowerCase(),
		)

		// Request headers
		let reqHeaders: Record<string, string> = {}
		if (request instanceof Request) {
			reqHeaders = Object.fromEntries(request.headers.entries())
		} else if (
			request &&
			typeof request === 'object' &&
			'headers' in request &&
			request.headers
		) {
			reqHeaders = Object.fromEntries(
				new Headers(request.headers as HeadersInit).entries(),
			)
		}
		const sanitizedReqHeaders = sanitizeHeaders(
			headersToRedact,
			reqHeaders,
			headersToRecord,
		)
		Object.entries(sanitizedReqHeaders).forEach(([key, value]) => {
			span.setAttribute(`http.request.header.${key}`, value)
		})

		// Request body (string only)
		const bodyInit =
			request instanceof Request
				? undefined
				: (request as RequestInit).body
		if (typeof bodyInit === 'string') {
			const bodyKeysToRedact = (
				recording.networkBodyKeysToRedact ?? []
			).map((k) => k.toLowerCase())
			const bodyKeysToRecord = recording.bodyKeysToRecord?.map((k) =>
				k.toLowerCase(),
			)
			const sanitizedBody = getBodyThatShouldBeRecorded(
				bodyInit,
				bodyKeysToRedact,
				bodyKeysToRecord,
				reqHeaders,
			)
			if (sanitizedBody != null) {
				span.setAttribute('http.request.body', sanitizedBody)
			}
		}

		// Response headers
		if (result instanceof Response) {
			const respHeaders = Object.fromEntries(result.headers.entries())
			const sanitizedRespHeaders = sanitizeHeaders(
				headersToRedact,
				respHeaders,
				headersToRecord,
			)
			Object.entries(sanitizedRespHeaders).forEach(([key, value]) => {
				span.setAttribute(`http.response.header.${key}`, value)
			})
		}
	}

export const XHRHook =
	(recording: NetworkRecordingOptions, urlBlocklist: string[]) =>
	(span: Span, xhr: XMLHttpRequest) => {
		const url = (xhr as any)._url ?? ''
		if (
			urlBlocklist.some((blocked) => url.toLowerCase().includes(blocked))
		) {
			return
		}

		const headersToRedact = (recording.networkHeadersToRedact ?? []).map(
			(h) => h.toLowerCase(),
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

		// Response headers
		const headerMap: Record<string, string> = {}
		xhr.getAllResponseHeaders()
			.trim()
			.split(/[\r\n]+/)
			.forEach((line) => {
				const parts = line.split(': ')
				const header = parts.shift()
				if (header) {
					headerMap[header] = parts.join(': ')
				}
			})
		const sanitizedRespHeaders = sanitizeHeaders(
			headersToRedact,
			headerMap,
			headersToRecord,
		)
		Object.entries(sanitizedRespHeaders).forEach(([key, value]) => {
			span.setAttribute(`http.response.header.${key}`, value)
		})

		// Response body
		if (xhr.responseType === '' || xhr.responseType === 'text') {
			const sanitizedBody = getBodyThatShouldBeRecorded(
				xhr.responseText,
				bodyKeysToRedact,
				bodyKeysToRecord,
				headerMap,
			)
			if (sanitizedBody != null) {
				span.setAttribute('http.response.body', sanitizedBody)
			}
		}
	}
