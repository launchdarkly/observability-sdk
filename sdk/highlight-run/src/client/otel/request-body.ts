import stringify from 'json-stringify-safe'
import { getBodySizeLimit } from '../listeners/network-listener/utils/xhr-listener'

export type HeaderRecord = { [key: string]: string }

// Bodies larger than this are described rather than decoded or parsed.
const MAX_BINARY_DECODE_BYTES = 64 * 1024 * 1024

const isMultipartFormData = (
	contentType: string | null | undefined,
): contentType is string =>
	!!contentType &&
	contentType.split(';')[0].trim().toLowerCase() === 'multipart/form-data'

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
	if (
		(typeof File !== 'undefined' && blob instanceof File) ||
		tagOf(blob) === '[object File]'
	) {
		const file = blob as File
		return `[File name="${file.name}" type="${file.type}" size=${file.size}]`
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

type StreamBytes = {
	chunks: Uint8Array[]
	byteLength: number
	// True when the read stopped at maxBytes before the stream ended.
	truncated: boolean
}

const readStreamBytes = async (
	stream: ReadableStream<Uint8Array>,
	maxBytes: number,
): Promise<StreamBytes> => {
	const reader = stream.getReader()
	const chunks: Uint8Array[] = []
	let byteLength = 0
	while (byteLength < maxBytes) {
		const { done, value } = await reader.read()
		if (done) {
			return { chunks, byteLength, truncated: false }
		}
		const remaining = maxBytes - byteLength
		const chunk =
			value.byteLength > remaining ? value.subarray(0, remaining) : value
		chunks.push(chunk)
		byteLength += chunk.byteLength
	}
	// Past the recording limit; stop pulling from the clone. The request's
	// own stream is a separate tee branch and is unaffected.
	reader.cancel().catch(() => {})
	return { chunks, byteLength, truncated: true }
}

const concatChunks = ({
	chunks,
	byteLength,
}: StreamBytes): Uint8Array<ArrayBuffer> => {
	const out = new Uint8Array(byteLength)
	let offset = 0
	for (const chunk of chunks) {
		out.set(chunk, offset)
		offset += chunk.byteLength
	}
	return out
}

// The bytes as UTF-8 text, or a `[binary ...]` descriptor when they are not
// valid UTF-8 (a Blob / typed-array body), mirroring `decodeBinary`.
const decodeStreamBytes = (bytes: StreamBytes): string => {
	// fatal: anything that is not valid UTF-8 is treated as binary.
	const decoder = new TextDecoder('utf-8', { fatal: true })
	try {
		let text = ''
		for (const chunk of bytes.chunks) {
			text += decoder.decode(chunk, { stream: true })
		}
		// A body cut off at the size limit can end mid-character, so only
		// flush the decoder when the whole body was read.
		return bytes.truncated ? text : text + decoder.decode()
	} catch {
		return `[binary size${bytes.truncated ? '>' : '='}${bytes.byteLength}]`
	}
}

// A FormData body is serialized to multipart when the Request is built, so
// its stream holds raw multipart text: form fields the JSON-based key
// redaction cannot see, plus the contents of every file part. Parse it back
// into FormData so it is recorded exactly like an init.body FormData.
const parseMultipartBody = async (
	bytes: StreamBytes,
	contentType: string,
): Promise<string> => {
	if (bytes.truncated) {
		return `[multipart/form-data size>${bytes.byteLength}]`
	}
	try {
		const form = await new Response(concatChunks(bytes), {
			headers: { 'content-type': contentType },
		}).formData()
		return stringifyFormData(form)
	} catch {
		return `[multipart/form-data size=${bytes.byteLength}]`
	}
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
		const contentType = request.headers.get('content-type')
		if (isMultipartFormData(contentType)) {
			// The recorded value is the field JSON, not the raw body, so the
			// read is bounded by the decode limit rather than the recording
			// limit for this content type.
			return readStreamBytes(clone.body, MAX_BINARY_DECODE_BYTES)
				.then((bytes) => parseMultipartBody(bytes, contentType))
				.catch(() => undefined)
		}
		return readStreamBytes(clone.body, getBodySizeLimit(request.headers))
			.then(decodeStreamBytes)
			.catch(() => undefined)
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
