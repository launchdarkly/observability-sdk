import type {
	Attributes,
	HrTime,
	Link,
	SpanContext,
	SpanKind,
	SpanStatus,
} from '@opentelemetry/api'
import type { InstrumentationConfig } from '@opentelemetry/instrumentation'
import type { IncomingMessage, RequestOptions } from 'http'

/**
 * Config for one OpenTelemetry instrumentation: the standard
 * `InstrumentationConfig` plus that instrumentation's own options.
 */
export type InstrumentationOptions = InstrumentationConfig & {
	[option: string]: any
}

/**
 * The most used options of `@opentelemetry/instrumentation-http`, typed so
 * their callbacks need no annotations. Any other `HttpInstrumentationConfig`
 * option is accepted too.
 */
export interface HttpInstrumentationOptions extends InstrumentationConfig {
	/** Return `true` to record no span for this incoming request. */
	ignoreIncomingRequestHook?: (request: IncomingMessage) => boolean
	/** Return `true` to record no span for this outgoing request. */
	ignoreOutgoingRequestHook?: (request: RequestOptions) => boolean
	[option: string]: any
}

/**
 * Per-instrumentation configuration, keyed by instrumentation package name
 * (for example `@opentelemetry/instrumentation-http`). Each value is the
 * config object accepted by that OpenTelemetry instrumentation. Only the
 * http entry is typed here; for the others, type the value with the config
 * type the instrumentation package exports (for example
 * `satisfies PgInstrumentationConfig`) if you want its callbacks typed.
 */
export type InstrumentationConfigMap = {
	'@opentelemetry/instrumentation-http'?: HttpInstrumentationOptions
	[instrumentationName: string]: InstrumentationOptions | undefined
}

/**
 * The finished span passed to {@link NodeOptions.shouldExportSpan}. This is
 * the subset of OpenTelemetry's `ReadableSpan` (from
 * `@opentelemetry/sdk-trace-base`) that the predicate can rely on; the object
 * passed is the `ReadableSpan` itself.
 */
export interface FinishedSpan {
	readonly name: string
	readonly kind: SpanKind
	spanContext(): SpanContext
	readonly parentSpanContext?: SpanContext
	readonly startTime: HrTime
	readonly endTime: HrTime
	readonly duration: HrTime
	readonly status: SpanStatus
	readonly attributes: Attributes
	readonly links: Link[]
	readonly resource: { readonly attributes: Attributes }
	readonly instrumentationScope: {
		readonly name: string
		readonly version?: string
	}
}

/**
 * Predicate deciding whether a finished span is exported.
 * Return `false` to drop the span.
 *
 * Declared through a method signature so that a predicate typed against the
 * full `ReadableSpan`, `(span: ReadableSpan) => boolean`, is also accepted.
 */
export type ShouldExportSpan = {
	bivarianceHack(span: FinishedSpan): boolean
}['bivarianceHack']

export const ALL_CONSOLE_METHODS = [
	'assert',
	'count',
	'countReset',
	'debug',
	'dir',
	'dirxml',
	'error',
	'group',
	'groupCollapsed',
	'groupEnd',
	'info',
	'log',
	'table',
	'time',
	'timeEnd',
	'timeLog',
	'trace',
	'warn',
] as const
export type ConsoleMethodsTuple = typeof ALL_CONSOLE_METHODS
export type ConsoleMethods = ConsoleMethodsTuple[number]

/**
 * Options for configuring the LaunchDarkly Observability Plugin.
 *
 * Additionally the following environment variables can be used to configure
 * the plugin.
 *
 * LAUNCHDARKLY_OTEL_NODE_ENABLE_FILESYSTEM_INSTRUMENTATION - Enable filesystem
 * instrumentation. Defaults to false.
 * LAUNCHDARKLY_OTEL_NODE_ENABLE_OUTGOING_HTTP_INSTRUMENTATION - Enable outgoing
 * HTTP instrumentation. Defaults to true.
 * This only affects the outgoing HTTP requests instrumented by
 * `@opentelemetry/instrumentation-http`. It does not affect fetch for example.
 *
 * OTEL_NODE_ENABLED_INSTRUMENTATIONS, and OTEL_NODE_DISABLED_INSTRUMENTATIONS
 * can be used per the OpenTelemetry documentation, but with a few exceptions.
 * https://opentelemetry.io/docs/zero-code/js/configuration/
 *
 * The `@opentelemetry/instrumentation-fs` instrumentation will only be enabled
 * if LAUNCHDARKLY_OTEL_NODE_ENABLE_FILESYSTEM_INSTRUMENTATION is true, and
 * will be unaffected by the OTEL_NODE_ENABLED_INSTRUMENTATIONS and
 * OTEL_NODE_DISABLED_INSTRUMENTATIONS environment variables.
 */
export interface NodeOptions {
	/**
	 * The endpoint string to send OTLP HTTP data to.
	 * @default https://otel.observability.app.launchdarkly.com
	 */
	otlpEndpoint?: string

	/**
	 * Specifies the URL used for non-OTLP operations.
	 * These include accessing client sampling configuration.
	 */
	backendUrl?: string

	/**
	 * This app's service name.
	 */
	serviceName?: string

	/**
	 * This app's version ideally set to the latest deployed git SHA.
	 */
	serviceVersion?: string

	/**
	 * Specifies the environment your application is running in.
	 * This is useful to distinguish whether your session was recorded on localhost or in production.
	 */
	environment?: 'development' | 'staging' | 'production' | string

	/**
	 * Attributes to be added to the OpenTelemetry Resource.
	 */
	attributes?: Attributes

	/**
	 * Turn off enhanced attribute reporting for pg instrumentation.
	 * See the following for additional details.
	 * https://github.com/open-telemetry/opentelemetry-js-contrib/blob/64fcbf3b70e7293e143266838ff94b94cf2c30da/plugins/node/opentelemetry-instrumentation-pg/src/types.ts#L52
	 */
	disablePgInstrumentationAttributes?: boolean

	/**
	 * Specifies whether the LaunchDarkly Observability Plugin will record console messages.
	 * @default false
	 */
	disableConsoleRecording?: boolean

	/**
	 * Set to try to serialize console object arguments into the message body.
	 */
	serializeConsoleAttributes?: boolean

	/**
	 * Specifies which console methods to record.
	 * The value here will be ignored if `disabledConsoleRecording` is `true`.
	 * @default All console methods.
	 * @example consoleMethodsToRecord: ['log', 'info', 'error']
	 */
	consoleMethodsToRecord?: ConsoleMethods[]

	/**
	 * Configuration passed through to the OpenTelemetry instrumentations the
	 * plugin registers, keyed by instrumentation package name. Use it to reach
	 * the standard options of those packages, such as
	 * `ignoreIncomingRequestHook` on `@opentelemetry/instrumentation-http`.
	 *
	 * The instrumentations are registered when this package is loaded, so this
	 * config is applied to the existing instances with `setConfig()` when the
	 * plugin is constructed. Each instrumentation's config is merged over the
	 * plugin's own defaults:
	 * - plain options you set replace the plugin's value;
	 * - hooks that the plugin also sets (for example the pino `logHook`, or the
	 *   pg `responseHook` used by `disablePgInstrumentationAttributes`) are
	 *   chained: the plugin's hook runs first, then yours, and yours provides
	 *   the return value.
	 *
	 * Limitations:
	 * - `enabled: false` disables an instrumentation, but an instrumentation
	 *   that was not registered at load (such as `instrumentation-fs` without
	 *   `LAUNCHDARKLY_OTEL_NODE_ENABLE_FILESYSTEM_INSTRUMENTATION=true`, or one
	 *   listed in `OTEL_NODE_DISABLED_INSTRUMENTATIONS`) cannot be enabled here.
	 * - Options an instrumentation only reads when it patches a module (for
	 *   example `disableOutgoingRequestInstrumentation` on http) may not take
	 *   effect for modules already loaded. Hooks are read per call and always
	 *   apply.
	 *
	 * @example
	 * ```typescript
	 * new Observability({
	 *   instrumentationConfig: {
	 *     '@opentelemetry/instrumentation-http': {
	 *       ignoreIncomingRequestHook: (req) => req.url === '/_vercel/ping',
	 *     },
	 *   },
	 * })
	 * ```
	 */
	instrumentationConfig?: InstrumentationConfigMap

	/**
	 * Called for every finished span before it is queued for export. Return
	 * `false` to drop the span. If the predicate throws, the span is exported.
	 *
	 * This runs after the span has been recorded, so prefer an instrumentation
	 * hook from `instrumentationConfig` (such as `ignoreIncomingRequestHook`)
	 * when one exists: that avoids creating the span at all. Dropping a span
	 * does not drop its children, which are exported without their parent.
	 *
	 * @example
	 * ```typescript
	 * new Observability({
	 *   shouldExportSpan: (span) =>
	 *     span.attributes['http.target'] !== '/healthz',
	 * })
	 * ```
	 */
	shouldExportSpan?: ShouldExportSpan
}
