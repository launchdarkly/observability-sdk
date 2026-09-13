import {
	BrowserXHR,
	getBodyData,
} from '../listeners/network-listener/utils/xhr-listener'

/**
 * Patches `XMLHttpRequest.prototype` so the request method, URL, headers and
 * body are stashed on each instance (`_method`, `_url`, `_requestHeaders`,
 * `_body`). The XMLHttpRequestInstrumentation `applyCustomAttributesOnSpan`
 * hook only receives the finished XHR object, so this is the only way for it
 * to attach `http.request.body` and `http.request.header.*` to the span.
 *
 * The session replay `XHRListener` populates the same fields, but it is only
 * installed when the SessionReplay plugin has `recordHeadersAndBody` enabled.
 * Tracing must not depend on that, so this is installed whenever the
 * Observability plugin asks for headers and bodies. Values the XHRListener
 * already stashed are left untouched.
 *
 * Returns a function that restores the original prototype methods.
 */
export const installXhrRequestCapture = (
	urlBlocklist: string[],
): (() => void) => {
	if (typeof XMLHttpRequest === 'undefined') {
		return () => {}
	}

	const XHR = XMLHttpRequest.prototype
	const originalOpen = XHR.open
	const originalSetRequestHeader = XHR.setRequestHeader
	const originalSend = XHR.send

	const isBlocklisted = (url: string | undefined) =>
		!!url &&
		urlBlocklist.some((blockedUrl) =>
			url.toLowerCase().includes(blockedUrl),
		)

	const patchedOpen = function (
		this: BrowserXHR,
		method: string,
		url: string | URL,
	) {
		this._method = method
		this._url = typeof url === 'string' ? url : String(url)
		this._requestHeaders = {}
		// A reused XMLHttpRequest keeps the previous request's body otherwise,
		// and `send` below would skip stashing the new one. Both this patch and
		// the XHRListener only set `_body` in `send`, which always runs after
		// `open`, so clearing here cannot drop a value stashed for this request.
		this._body = undefined

		// @ts-expect-error
		return originalOpen.apply(this, arguments)
	}

	const patchedSetRequestHeader = function (
		this: BrowserXHR,
		header: string,
		value: string,
	) {
		if (!this._requestHeaders) {
			this._requestHeaders = {}
		}
		this._requestHeaders[header] = value

		// @ts-expect-error
		return originalSetRequestHeader.apply(this, arguments)
	}

	const patchedSend = function (
		this: BrowserXHR,
		body?: Document | XMLHttpRequestBodyInit | null,
	) {
		if (
			this._body === undefined &&
			body !== undefined &&
			body !== null &&
			!isBlocklisted(this._url)
		) {
			const bodyData = getBodyData(body, this._url)
			if (bodyData) {
				this._body = bodyData
			}
		}

		// @ts-expect-error
		return originalSend.apply(this, arguments)
	}

	XHR.open = patchedOpen
	XHR.setRequestHeader = patchedSetRequestHeader
	XHR.send = patchedSend

	return () => {
		// Only unwind our own layer; leave any patch applied on top of us alone.
		if (XHR.open === patchedOpen) {
			XHR.open = originalOpen
		}
		if (XHR.setRequestHeader === patchedSetRequestHeader) {
			XHR.setRequestHeader = originalSetRequestHeader
		}
		if (XHR.send === patchedSend) {
			XHR.send = originalSend
		}
	}
}
