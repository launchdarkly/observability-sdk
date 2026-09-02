import stringify from 'json-stringify-safe'
import { getBodySizeLimit } from '../listeners/network-listener/utils/xhr-listener'

export type HeaderRecord = { [key: string]: string }

// Bodies larger than this are described rather than decoded to text.
const MAX_BINARY_DECODE_BYTES = 64 * 1024 * 1024

/**
 * Turns any of the header shapes a caller can hand to fetch/XHR into a plain
 * object keyed by header name. `Headers` instances and `[name, value]` arrays
 * have no own enumerable properties, so spreading them (which is what the
 * header sanitizer does) silently produced `{}` and dropped every header.
 */
export const normalizeHeaders = (
	headers: HeadersInit | HeaderRecord | string | undefined | null,
): HeaderRecord => {
	if (!headers || typeof headers !== 'object') {
		return {}
	}
	if (typeof Headers !== 'undefined' && headers instanceof Headers) {
		return Object.fromEntries(headers.entries())
	}
	if (Array.isArray(headers)) {
		try {
			// Headers() combines repeated names the same way the browser does.
			return Object.fromEntries(new Headers(headers).entries())
		} catch {
			return Object.fromEntries(
				headers.map(([name, value]) => [String(name), String(value)]),
			)
		}
	}
	return { ...(headers as HeaderRecord) }
}

// Object.prototype.toString reads the internal class tag, so it also matches
// values created in another realm (an iframe, a worker) where instanceof
// against this realm's constructors is false.
const tagOf = (value: unknown): string => Object.prototype.toString.call(value)

const isArrayBufferLike = (value: unknown): value is ArrayBuffer =>
	(typeof ArrayBuffer !== 'undefined' && value instanceof ArrayBuffer) ||
	tagOf(value) === '[object ArrayBuffer]' ||
	tagOf(value) === '[object SharedArrayBuffer]'

const describeBlob = (blob: Blob): string => {
	if (typeof File !== 'undefined' && blob instanceof File) {
		return `[File name="${blob.name}" type="${blob.type}" size=${blob.size}]`
	}
	return `[Blob type="${blob.type}" size=${blob.size}]`
}

// Recorded as JSON so bodyKeysToRedact / bodyKeysToRecord apply to form
// fields too. File parts are described, not read.
const stringifyFormData = (form: FormData): string => {
	const fields: { [key: string]: string | string[] } = {}
	form.forEach((value, key) => {
		const text = typeof value === 'string' ? value : describeBlob(value)
		const existing = fields[key]
		if (existing === undefined) {
			fields[key] = text
		} else if (Array.isArray(existing)) {
			existing.push(text)
		} else {
			fields[key] = [existing, text]
		}
	})
	return JSON.stringify(fields)
}

const decodeBinary = (body: ArrayBuffer | ArrayBufferView): string => {
	const bytes = ArrayBuffer.isView(body)
		? new Uint8Array(body.buffer, body.byteOffset, body.byteLength)
		: new Uint8Array(body)
	if (bytes.byteLength > MAX_BINARY_DECODE_BYTES) {
		return `[binary size=${bytes.byteLength}]`
	}
	try {
		// fatal: anything that is not valid UTF-8 is treated as binary.
		return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
	} catch {
		return `[binary size=${bytes.byteLength}]`
	}
}

/**
 * Normalizes a request body into the string that gets recorded on the span.
 * OTel drops attribute values that are not primitives (or arrays of them),
 * so any body left as an object (`FormData`, `URLSearchParams`, `Blob`,
 * `ArrayBuffer`, ...) was silently lost.
 *
 * Returns undefined when there is nothing recordable: no body, or a stream
 * that has to be captured up front (see `installFetchRequestBodyCapture`).
 */
export const normalizeRequestBody = (body: unknown): string | undefined => {
	if (body === undefined || body === null) {
		return undefined
	}
	switch (typeof body) {
		case 'string':
			return body
		case 'number':
		case 'boolean':
		case 'bigint':
			return String(body)
		case 'object':
			break
		default:
			return undefined
	}
	const tag = tagOf(body)
	if (
		(typeof URLSearchParams !== 'undefined' &&
			body instanceof URLSearchParams) ||
		tag === '[object URLSearchParams]'
	) {
		return (body as URLSearchParams).toString()
	}
	if (
		(typeof FormData !== 'undefined' && body instanceof FormData) ||
		tag === '[object FormData]'
	) {
		return stringifyFormData(body as FormData)
	}
	if (
		(typeof Blob !== 'undefined' && body instanceof Blob) ||
		tag === '[object Blob]' ||
		tag === '[object File]'
	) {
		return describeBlob(body as Blob)
	}
	if (isArrayBufferLike(body) || ArrayBuffer.isView(body)) {
		return decodeBinary(body as ArrayBuffer | ArrayBufferView)
	}
	if (
		(typeof ReadableStream !== 'undefined' &&
			body instanceof ReadableStream) ||
		tag === '[object ReadableStream]'
	) {
		return undefined
	}
	if (typeof Document !== 'undefined' && body instanceof Document) {
		try {
			return new XMLSerializer().serializeToString(body)
		} catch {
			return undefined
		}
	}
	try {
		return stringify(body)
	} catch {
		return undefined
	}
}

// Request objects seen by the fetch wrapper, mapped to their body text.
const capturedRequestBodies = new WeakMap<
	Request,
	Promise<string | undefined>
>()

/** Body text stashed for a Request object by `installFetchRequestBodyCapture`. */
export const getCapturedRequestBody = (
	request: Request,
): Promise<string | undefined> | undefined => capturedRequestBodies.get(request)

const readStreamText = async (
	stream: ReadableStream<Uint8Array>,
	maxBytes: number,
): Promise<string> => {
	const reader = stream.getReader()
	const decoder = new TextDecoder()
	let text = ''
	let bytesRead = 0
	while (bytesRead < maxBytes) {
		const { done, value } = await reader.read()
		if (done) {
			return text + decoder.decode()
		}
		const remaining = maxBytes - bytesRead
		const chunk =
			value.byteLength > remaining ? value.subarray(0, remaining) : value
		bytesRead += chunk.byteLength
		text += decoder.decode(chunk, { stream: true })
	}
	// Past the recording limit; stop pulling from the clone. The request's
	// own stream is a separate tee branch and is unaffected.
	reader.cancel().catch(() => {})
	return text + decoder.decode()
}

const captureRequestBody = (
	request: Request,
	init?: RequestInit,
): Promise<string | undefined> => {
	try {
		if (init && init.body !== undefined && init.body !== null) {
			// An explicit init.body overrides the Request's own body.
			return Promise.resolve(normalizeRequestBody(init.body))
		}
		if (!request.body || request.bodyUsed) {
			return Promise.resolve(undefined)
		}
		const clone = request.clone()
		if (!clone.body) {
			return Promise.resolve(undefined)
		}
		return readStreamText(
			clone.body,
			getBodySizeLimit(request.headers),
		).catch(() => undefined)
	} catch {
		return Promise.resolve(undefined)
	}
}

/**
 * When an app calls `fetch(new Request(...))`, the OTel fetch instrumentation
 * hands our hook the Request object itself. Its body is a stream that the
 * network layer has already consumed by the time the hook runs, so there is
 * nothing left to read. Wrap `window.fetch` so a clone of the body is read
 * up front and can be looked up by Request identity later.
 *
 * Install after `registerInstrumentations` so this sits on top of the OTel
 * wrapper. Returns a function that removes the wrapper.
 */
export const installFetchRequestBodyCapture = (): (() => void) => {
	if (
		typeof window === 'undefined' ||
		typeof window.fetch !== 'function' ||
		typeof Request === 'undefined'
	) {
		return () => {}
	}
	const originalFetch = window.fetch
	const patchedFetch: typeof window.fetch = function (
		this: unknown,
		input,
		init,
	) {
		if (input instanceof Request && !capturedRequestBodies.has(input)) {
			capturedRequestBodies.set(input, captureRequestBody(input, init))
		}
		return originalFetch.call(this ?? window, input, init)
	}
	window.fetch = patchedFetch
	return () => {
		// Only unwind our own layer; leave any patch applied on top of us alone.
		if (window.fetch === patchedFetch) {
			window.fetch = originalFetch
		}
	}
}
