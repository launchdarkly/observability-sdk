import stringify from 'json-stringify-safe'

import { NetworkListenerCallback } from '../network-listener'
import { Headers, Request, RequestResponsePair, Response } from './models'
import {
	createNetworkRequestId,
	getHighlightRequestHeader,
	HIGHLIGHT_REQUEST_HEADER,
	shouldNetworkRequestBeRecorded,
	shouldNetworkRequestBeTraced,
} from './utils'

export interface BrowserXHR extends XMLHttpRequest {
	_method: string
	_url: string
	_requestHeaders: Headers
	_responseSize?: number
	_shouldRecordHeaderAndBody: boolean
	_body?: any
}

/**
 * Listens to all XMLHttpRequests made.
 */
export const XHRListener = (
	callback: NetworkListenerCallback,
	highlightEndpoints: string[],
	tracingOrigins: boolean | (string | RegExp)[],
	urlBlocklist: string[],
	bodyKeysToRedact: string[],
	bodyKeysToRecord: string[] | undefined,
) => {
	const XHR = XMLHttpRequest.prototype

	const originalOpen = XHR.open
	const originalSend = XHR.send
	const originalSetRequestHeader = XHR.setRequestHeader

	/**
	 * When a request gets initiated, store metadata for that specific request.
	 */
	XHR.open = function (this: BrowserXHR, method: string, url: string | URL) {
		if (typeof url === 'string') {
			this._url = url
		} else {
			this._url = url.toString()
		}
		this._method = method
		this._requestHeaders = {}
		this._shouldRecordHeaderAndBody = !urlBlocklist.some((blockedUrl) =>
			this._url.toLowerCase().includes(blockedUrl),
		)

		// @ts-expect-error
		return originalOpen.apply(this, arguments)
	}

	XHR.setRequestHeader = function (
		this: BrowserXHR,
		header: string,
		value: string,
	) {
		this._requestHeaders[header] = value

		// @ts-expect-error
		return originalSetRequestHeader.apply(this, arguments)
	}

	XHR.send = function (this: BrowserXHR, postData: any) {
		if (
			!shouldNetworkRequestBeRecorded(
				this._url,
				highlightEndpoints,
				tracingOrigins,
			)
		) {
			// @ts-expect-error
			return originalSend.apply(this, arguments)
		}

		const [sessionSecureID, requestId] = createNetworkRequestId()
		if (
			shouldNetworkRequestBeTraced(
				this._url,
				tracingOrigins,
				urlBlocklist,
			)
		) {
			this.setRequestHeader(
				HIGHLIGHT_REQUEST_HEADER,
				getHighlightRequestHeader(sessionSecureID, requestId),
			)
		}

		const shouldRecordHeaderAndBody = this._shouldRecordHeaderAndBody
		const requestModel: Request = {
			sessionSecureID,
			id: requestId,
			url: this._url,
			verb: this._method,
			headers: shouldRecordHeaderAndBody ? this._requestHeaders : {},
			body: undefined,
		}

		if (shouldRecordHeaderAndBody) {
			if (postData) {
				const bodyData = getBodyData(postData, requestModel.url)
				if (bodyData) {
					this._body = bodyData
					requestModel['body'] = getBodyThatShouldBeRecorded(
						bodyData,
						bodyKeysToRedact,
						bodyKeysToRecord,
						requestModel.headers,
					)
				}
			}
		}

		// The load event for XMLHttpRequest is fired when a request completes successfully.
		this.addEventListener('load', async function () {
			const responseModel: Response = {
				status: this.status,
				headers: {},
				body: undefined,
			}

			if (shouldRecordHeaderAndBody) {
				const responseHeaders = this.getAllResponseHeaders()
				// Convert the header string into an array
				// of individual headers
				const normalizedResponseHeaders = responseHeaders
					.trim()
					.split(/[\r\n]+/)

				// Create a map of header names to values
				const headerMap: { [key: string]: any } = {}
				normalizedResponseHeaders.forEach(function (line) {
					const parts = line.split(': ')
					const header = parts.shift() as string
					headerMap[header] = parts.join(': ')
				})
				responseModel.headers = headerMap

				if (postData) {
					const bodyData = getBodyData(postData, requestModel.url)
					if (bodyData) {
						requestModel['body'] = getBodyThatShouldBeRecorded(
							bodyData,
							bodyKeysToRedact,
							bodyKeysToRecord,
							responseModel.headers,
						)
					}
				}

				if (this.responseType === '' || this.responseType === 'text') {
					responseModel['body'] = getBodyThatShouldBeRecorded(
						this.responseText,
						bodyKeysToRedact,
						bodyKeysToRecord,
						responseModel.headers,
					)
					// Each character is 8 bytes, total size is number of characters multiplied by 8.
					responseModel['size'] = this.responseText.length * 8
				} else if (this.responseType === 'blob') {
					if (this.response instanceof Blob) {
						try {
							const response = await this.response.text()

							responseModel['body'] = getBodyThatShouldBeRecorded(
								response,
								bodyKeysToRedact,
								bodyKeysToRecord,
								responseModel.headers,
							)
							responseModel['size'] = this.response.size
						} catch {}
					}
				} else {
					try {
						responseModel['body'] = getBodyThatShouldBeRecorded(
							this.response,
							bodyKeysToRedact,
							bodyKeysToRecord,
							responseModel.headers,
						)
					} catch {}
				}
			}

			const event: RequestResponsePair = {
				request: requestModel,
				response: responseModel,
				urlBlocked: !shouldRecordHeaderAndBody,
			}

			callback(event)
		})

		/**
		 * The error event happens when a network request fails. A 4xx or 5xx
		 * response will not trigger this, those will still trigger a load event.
		 * An error is if the request is blocked, some scenarios:
		 * 1. The request is blocked by an extension
		 * 2. The request is blocked by the DevTools
		 * 3. The client is offline
		 */
		this.addEventListener('error', async function () {
			const responseModel: Response = {
				status: this.status,
				headers: undefined,
				body: undefined,
			}

			const event: RequestResponsePair = {
				request: requestModel,
				response: responseModel,
				urlBlocked: false,
			}

			callback(event)
		})

		// @ts-expect-error
		return originalSend.apply(this, arguments)
	}

	return () => {
		XHR.open = originalOpen
		XHR.send = originalSend
		XHR.setRequestHeader = originalSetRequestHeader
	}
}

// Body shapes that must never go through JSON.stringify: stringifying a typed
// array yields `{"0":123,"1":34,...}`, ~15x the payload. The SDK's own OTLP
// exporter sends its batches as a Uint8Array through XMLHttpRequest.send, so
// this was run on every export (and retry) once XHR bodies were captured.
// Object.prototype.toString reads the internal class tag, so it also matches
// values created in another realm (an iframe, a worker, a test runner).
const BINARY_BODY_TAG =
	/^\[object (ArrayBuffer|SharedArrayBuffer|DataView|(?:Ui|I)nt(?:8|16|32)Array|Uint8ClampedArray|Float(?:16|32|64)Array|Big(?:Ui|I)nt64Array|Blob|File|FormData|URLSearchParams|ReadableStream)\]$/
const isBinaryBody = (body: unknown): boolean =>
	typeof body === 'object' &&
	body !== null &&
	BINARY_BODY_TAG.test(Object.prototype.toString.call(body))

const describeBinaryBody = (body: any): string => {
	if (typeof Blob !== 'undefined' && body instanceof Blob) {
		return `[Blob type="${body.type}" size=${body.size}]`
	}
	if (typeof FormData !== 'undefined' && body instanceof FormData) {
		return '[FormData]'
	}
	if (
		typeof URLSearchParams !== 'undefined' &&
		body instanceof URLSearchParams
	) {
		return body.toString().slice(0, DEFAULT_BODY_LIMIT)
	}
	const size = body?.byteLength ?? body?.size ?? 0
	return `[binary size=${size}]`
}

export const getBodyData = (postData: any, url: string | undefined) => {
	if (isBinaryBody(postData)) {
		return describeBinaryBody(postData)
	}
	if (typeof postData === 'string') {
		// TODO: This should be removed when we move recording logic from client to firstload.
		// This is only for development purposes. We don't want to send the body of pushPayload requests because it'll end up being recursive.
		if (
			!(
				(url?.includes('localhost') ||
					url?.includes('highlight.run')) &&
				(postData.includes('pushPayload') ||
					postData.includes('pushSessionEvents'))
			)
		) {
			return postData
		}
	} else if (
		typeof postData === 'object' ||
		typeof postData === 'number' ||
		typeof postData === 'boolean'
	) {
		return stringify(postData)
	}

	return null
}

// Bodies are cut to 64 KiB by the ingest path anyway, so anything recorded
// past that only costs client memory and upload bandwidth. JSON and text get a
// little more room so key redaction still sees a complete document. The old
// limits here were 64 MiB, which let multi-megabyte API responses be copied,
// parsed and re-serialized on the main thread for every request.
const DEFAULT_BODY_LIMIT = 64 * 1024 // 64 KiB
const BODY_SIZE_LIMITS = {
	'application/json': 256 * 1024, // 256 KiB
	'text/plain': 256 * 1024, // 256 KiB
} as const

/** Recorded in place of a body that exceeds the size limit for its content type. */
export const bodyOmittedPlaceholder = (size: number, limit: number) =>
	`[body omitted: ${size} bytes exceeds the ${limit} byte limit]`

/** Max recorded body size in characters, keyed by the content type. */
export const getBodySizeLimit = (
	headers?: Headers | { [key: string]: string },
): number => {
	if (!headers) {
		return DEFAULT_BODY_LIMIT
	}
	let contentType: string = ''
	if (typeof headers['get'] === 'function') {
		contentType = headers.get('content-type') ?? ''
	} else {
		// Plain header objects keep whatever casing the caller used.
		const record = headers as { [key: string]: string }
		const key = Object.keys(record).find(
			(k) => k.toLowerCase() === 'content-type',
		)
		contentType = (key && record[key]) || ''
	}
	try {
		contentType = contentType.split(';')[0].trim()
	} catch {}
	return (
		BODY_SIZE_LIMITS[contentType as keyof typeof BODY_SIZE_LIMITS] ??
		DEFAULT_BODY_LIMIT
	)
}

export const getBodyThatShouldBeRecorded = (
	bodyData: any,
	bodyKeysToRedact?: string[],
	bodyKeysToRecord?: string[],
	headers?: Headers | { [key: string]: string },
) => {
	const bodyLimit = getBodySizeLimit(headers)

	// Check the size before any JSON parsing. Parsing a multi-megabyte body only
	// to slice it afterwards was the most expensive thing the SDK did per request
	// on low-end devices, and a truncated JSON document cannot be redacted
	// safely, so oversized bodies are described instead of recorded.
	if (typeof bodyData === 'string' && bodyData.length > bodyLimit) {
		return bodyOmittedPlaceholder(bodyData.length, bodyLimit)
	}

	if (bodyData) {
		if (bodyKeysToRedact) {
			try {
				const json = JSON.parse(bodyData)

				if (Array.isArray(json)) {
					json.forEach((element) => {
						Object.keys(element).forEach((key) => {
							if (
								bodyKeysToRedact.includes(
									key.toLocaleLowerCase(),
								)
							) {
								element[key] = '[REDACTED]'
							}
						})
					})
				} else {
					Object.keys(json).forEach((key) => {
						if (
							bodyKeysToRedact.includes(key.toLocaleLowerCase())
						) {
							json[key] = '[REDACTED]'
						}
					})
				}

				bodyData = JSON.stringify(json)
			} catch {}
		}

		if (bodyKeysToRecord) {
			try {
				const json = JSON.parse(bodyData)

				Object.keys(json).forEach((key) => {
					if (!bodyKeysToRecord.includes(key.toLocaleLowerCase())) {
						json[key] = '[REDACTED]'
					}
				})

				bodyData = JSON.stringify(json)
			} catch {}
		}
	}

	try {
		bodyData = bodyData.slice(0, bodyLimit)
	} catch {}

	return bodyData
}
