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

const isReportingObserverSupported = (): boolean =>
	typeof window !== 'undefined' &&
	typeof (window as unknown as { ReportingObserver?: unknown })
		.ReportingObserver !== 'undefined'

const flattenBody = (
	body: unknown,
): Record<string, string | number | boolean> => {
	const attrs: Record<string, string | number | boolean> = {}
	if (!body || typeof body !== 'object') return attrs
	for (const [key, value] of Object.entries(
		body as Record<string, unknown>,
	)) {
		if (value === null || value === undefined) continue
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
	body?: Record<string, unknown> | null
}): string => {
	const body = report.body ?? {}
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
}

/**
 * Subscribes to the browser's Reporting API and fans reports out to the
 * provided callback. CSP and intervention reports are emitted as errors;
 * deprecation reports are emitted as warn-level logs.
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
					const body = (report.body ?? {}) as Record<string, unknown>
					const attributes = {
						'report.type': type,
						'report.url': report.url,
						...flattenBody(body),
					}
					const message = messageFromReport({ type, body })
					if (type === 'deprecation') {
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
