/**
 * Strip the query string and fragment from a URL before it is used as an
 * OpenTelemetry span name. Query params and hash fragments can carry secrets
 * (OAuth tokens, auth codes, signed URLs); span names are exported to the
 * tracing backend, so the high-cardinality, potentially-sensitive tail must be
 * dropped. Works for both absolute URLs (`request.url`) and request-relative
 * paths (`req.url` in the Pages Router).
 */
export function sanitizeSpanUrl(url: string | undefined): string {
	if (!url) {
		return ''
	}
	return url.split('#')[0].split('?')[0]
}
