import type { ConsoleMethods } from '../types/client'

export type ReportingObserverReportKind = 'error' | 'log'

export interface ReportingObserverReport {
	kind: ReportingObserverReportKind
	/** Log level when kind === 'log'. */
	level?: ConsoleMethods
	/** `deprecation`, `intervention`, `csp-violation`, ... */
	type: string
	/** Human-readable message for error/log routing. */
	message: string
	/** URL the report originated from. */
	url?: string
	/** Flattened `report.body` fields (prefixed with `report.body.`). */
	attributes: Record<string, string | number | boolean | undefined>
}

const BODY_PRIMITIVE = new Set(['string', 'number', 'boolean'])

// `originalPolicy` repeats the page's whole CSP on every violation; the
// directive, disposition and blocked URL identify the violation.
const BODY_FIELDS_TO_SKIP = new Set(['originalPolicy'])

const isReportingObserverSupported = (): boolean =>
	typeof window !== 'undefined' &&
	typeof (window as unknown as { ReportingObserver?: unknown })
		.ReportingObserver !== 'undefined'

/**
 * Reads a report body's fields. Browsers hand the callback a `ReportBody`
 * (`CSPViolationReportBody`, `DeprecationReportBody`, ...) whose fields are
 * getters on the prototype, so `Object.entries(body)` is empty for real
 * reports. `toJSON()` is the supported way to read them; `for...in` (which
 * walks enumerable prototype attributes) is the fallback, and also covers
 * plain objects.
 */
const readBody = (body: unknown): Record<string, unknown> => {
	if (!body || typeof body !== 'object') return {}
	const toJSON = (body as { toJSON?: unknown }).toJSON
	if (typeof toJSON === 'function') {
		try {
			const json = toJSON.call(body)
			if (json && typeof json === 'object') {
				return json as Record<string, unknown>
			}
		} catch {
			// fall through to enumeration
		}
	}
	const fields: Record<string, unknown> = {}
	for (const key in body as Record<string, unknown>) {
		try {
			const value = (body as Record<string, unknown>)[key]
			if (typeof value !== 'function') fields[key] = value
		} catch {
			// ignore fields whose getter throws
		}
	}
	return fields
}

const flattenBody = (
	body: Record<string, unknown>,
): Record<string, string | number | boolean> => {
	const attrs: Record<string, string | number | boolean> = {}
	for (const [key, value] of Object.entries(body)) {
		if (value === null || value === undefined) continue
		if (BODY_FIELDS_TO_SKIP.has(key)) continue
		if (BODY_PRIMITIVE.has(typeof value)) {
			attrs[`report.body.${key}`] = value as string | number | boolean
		} else {
			try {
				attrs[`report.body.${key}`] = JSON.stringify(value)
			} catch {
				// ignore un-serializable fields
			}
		}
	}
	return attrs
}

const messageFromReport = (report: {
	type: string
	body: Record<string, unknown>
}): string => {
	const { body } = report
	if (report.type === 'csp-violation') {
		// Group by directive, not by blocked URL, to keep cardinality bounded.
		const directive = body['effectiveDirective']
		return typeof directive === 'string' && directive
			? `csp-violation: ${directive}`
			: report.type
	}
	const message =
		(body['message'] as string | undefined) ||
		(body['reason'] as string | undefined) ||
		(body['id'] as string | undefined) ||
		report.type
	return typeof message === 'string' ? message : report.type
}

export interface ReportingObserverListenerOptions {
	/**
	 * Which report types to observe. Defaults to all three browser types
	 * that contribute actionable diagnostics.
	 */
	types?: string[]
	/**
	 * Emit `csp-violation` reports as errors instead of warn-level logs.
	 * Off by default: a CSP report is the browser describing a policy
	 * decision (and, for a Report-Only policy, one it did not even enforce),
	 * not an exception in the app, and pages with a broad policy produce one
	 * per blocked resource.
	 */
	cspViolationsAsErrors?: boolean
}

/**
 * Subscribes to the browser's Reporting API and fans reports out to the
 * provided callback. Intervention reports are emitted as errors; deprecation
 * and CSP violation reports are emitted as warn-level logs (CSP violations
 * become errors with `cspViolationsAsErrors`).
 *
 * Returns a disconnect function. If `ReportingObserver` is unavailable in
 * the current environment, returns a no-op.
 */
export const ReportingObserverListener = (
	callback: (report: ReportingObserverReport) => void,
	options?: ReportingObserverListenerOptions,
): (() => void) => {
	if (!isReportingObserverSupported()) return () => {}

	const types = options?.types ?? [
		'deprecation',
		'intervention',
		'csp-violation',
	]

	const ReportingObserverCtor = (
		window as unknown as {
			ReportingObserver: new (
				cb: (reports: ReadonlyArray<any>) => void,
				opts: { buffered?: boolean; types?: string[] },
			) => { observe(): void; disconnect(): void }
		}
	).ReportingObserver

	let observer: { observe(): void; disconnect(): void }
	try {
		observer = new ReportingObserverCtor(
			(reports) => {
				for (const report of reports) {
					const type = report.type ?? 'unknown'
					const body = readBody(report.body)
					const attributes = {
						'report.type': type,
						'report.url': report.url,
						...flattenBody(body),
					}
					const message = messageFromReport({ type, body })
					const asLog =
						type === 'deprecation' ||
						(type === 'csp-violation' &&
							!options?.cspViolationsAsErrors)
					if (asLog) {
						callback({
							kind: 'log',
							level: 'warn',
							type,
							message,
							url: report.url,
							attributes,
						})
					} else {
						callback({
							kind: 'error',
							type,
							message,
							url: report.url,
							attributes,
						})
					}
				}
			},
			{ buffered: true, types },
		)
		observer.observe()
	} catch {
		return () => {}
	}

	return () => {
		try {
			observer.disconnect()
		} catch {
			// ignore
		}
	}
}
