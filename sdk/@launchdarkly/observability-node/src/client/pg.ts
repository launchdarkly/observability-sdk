import type { Attributes, Span } from '@opentelemetry/api'
import type { PgInstrumentationConfig } from '@opentelemetry/instrumentation-pg'
import {
	createDenyListAttributesProcessor,
	type ViewOptions,
} from '@opentelemetry/sdk-metrics'

export const PG_INSTRUMENTATION_NAME = '@opentelemetry/instrumentation-pg'
export const PG_REDACTED_VALUE = '[REDACTED]'

/**
 * Span attributes that `@opentelemetry/instrumentation-pg` may set and that can
 * carry query text, bound values, or connection details. Both the stable
 * database semantic convention names (emitted by instrumentation-pg >= 0.70)
 * and the legacy names (emitted by older releases) are listed, so the option
 * keeps working whichever convention the instrumentation uses.
 */
export const PG_REDACTED_ATTRIBUTES = [
	// Stable database / network semantic conventions.
	'db.query.text',
	'db.query.summary',
	'db.namespace',
	'db.system.name',
	'db.operation.name',
	'db.collection.name',
	'db.stored_procedure.name',
	'server.address',
	'server.port',
	'network.peer.address',
	'network.peer.port',
	// instrumentation-pg specific.
	'db.postgresql.values',
	'db.postgresql.plan',
	// Metric-only: pool names have the form `<host>:<port>/<database>`.
	'db.client.connection.pool.name',
	// Legacy semantic conventions.
	'db.connection_string',
	'db.name',
	'db.statement',
	'db.system',
	'db.user',
	'db.operation',
	'db.sql.table',
	'net.peer.name',
	'net.peer.port',
] as const

/**
 * Overwrites the sensitive pg attributes on a span. When the span exposes its
 * attributes (SDK spans do), only the attributes that are present are
 * overwritten, so spans are not padded with placeholder attributes.
 */
export function redactPgSpan(span: Span, database?: string): void {
	if (!span.isRecording()) {
		return
	}
	const existing = (span as unknown as { attributes?: Attributes }).attributes
	for (const key of PG_REDACTED_ATTRIBUTES) {
		if (!existing || key in existing) {
			span.setAttribute(key, PG_REDACTED_VALUE)
		}
	}
	// Query span names have the form `pg.query:<COMMAND> <database>`; drop the
	// database so it does not survive in the span name.
	const name = (span as unknown as { name?: string }).name
	if (database && name?.endsWith(` ${database}`)) {
		span.updateName(name.slice(0, -(database.length + 1)))
	}
}

/**
 * Instrumentation config applied to instrumentation-pg when
 * `disablePgInstrumentationAttributes` is set. `requestHook` runs once the
 * query span has all of its attributes, before the query is sent, so the
 * redaction also covers queries that fail (`responseHook` only runs on
 * success). `connectionHook` covers `pg.connect` and `pg-pool.connect` spans.
 */
export function getRedactedPgConfig(): PgInstrumentationConfig {
	return {
		enhancedDatabaseReporting: false,
		requestHook: (span, { connection }) =>
			redactPgSpan(span, connection?.database),
		responseHook: (span) => redactPgSpan(span),
		connectionHook: (span, { connection }) =>
			redactPgSpan(span, connection?.database),
	}
}

/**
 * Metric views that strip connection details from the metrics recorded by
 * instrumentation-pg (for example `db.client.operation.duration`, which
 * carries `db.namespace`, `server.address` and `server.port`).
 */
export function getRedactedPgMetricViews(): ViewOptions[] {
	return [
		{
			meterName: PG_INSTRUMENTATION_NAME,
			instrumentName: '*',
			attributesProcessors: [
				createDenyListAttributesProcessor([...PG_REDACTED_ATTRIBUTES]),
			],
		},
	]
}
