import { Attributes } from '@opentelemetry/api'
import type { InstrumentationConfigMap } from '@opentelemetry/auto-instrumentations-node'

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
	 * Configuration for the OpenTelemetry auto-instrumentations registered by
	 * the plugin, keyed by instrumentation name. This is the same shape that
	 * `getNodeAutoInstrumentations()` from
	 * `@opentelemetry/auto-instrumentations-node` accepts, and each entry is
	 * merged over the plugin's defaults for that instrumentation.
	 *
	 * Use it to filter spans at the source, for example to drop health checks
	 * or platform probes such as Vercel's `GET /_vercel/ping`, while keeping
	 * every other inbound request span.
	 *
	 * Setting `enabled: false` disables that instrumentation. Instrumentations
	 * that are disabled by default (such as `@opentelemetry/instrumentation-fs`)
	 * or by `OTEL_NODE_DISABLED_INSTRUMENTATIONS` cannot be enabled here, and
	 * options read when a module is patched (such as the http
	 * `disableIncomingRequestInstrumentation` and
	 * `disableOutgoingRequestInstrumentation` flags) only take effect for
	 * modules loaded after the plugin is initialized. Request-time options
	 * such as `ignoreIncomingRequestHook`, `ignoreOutgoingRequestHook`,
	 * `requestHook` and `responseHook` always apply.
	 *
	 * @example
	 * ```typescript
	 * new Observability({
	 *   instrumentationConfig: {
	 *     '@opentelemetry/instrumentation-http': {
	 *       ignoreIncomingRequestHook: (req) =>
	 *         req.url === '/_vercel/ping' || req.url === '/healthz',
	 *     },
	 *     '@opentelemetry/instrumentation-dns': { enabled: false },
	 *   },
	 * })
	 * ```
	 */
	instrumentationConfig?: InstrumentationConfigMap
}
