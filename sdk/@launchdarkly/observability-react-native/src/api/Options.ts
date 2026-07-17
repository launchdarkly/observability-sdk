import { Attributes } from '@opentelemetry/api'
import type { LDContext } from '@launchdarkly/js-sdk-common'

export type NetworkRecordingOptions = {
	/**
	 * This enables recording XMLHttpRequest and Fetch headers and bodies.
	 * @default false
	 */
	recordHeadersAndBody?: boolean
	/**
	 * Request and response headers where the value is not recorded.
	 * The header value is replaced with '[REDACTED]'.
	 * These headers are case-insensitive.
	 * `recordHeadersAndBody` needs to be enabled.
	 * This option will be ignored if `headerKeysToRecord` is set.
	 * @example
	 * networkHeadersToRedact: ['Secret-Header', 'Plain-Text-Password']
	 */
	networkHeadersToRedact?: string[]
	/**
	 * Specifies the keys for request/response JSON body that should not be recorded.
	 * The body value is replaced with '[REDACTED]'.
	 * These keys are case-insensitive.
	 * `recordHeadersAndBody` needs to be `true`. Otherwise this option will be ignored.
	 * @example bodyKeysToRedact: ['secret-token', 'plain-text-password']
	 */
	networkBodyKeysToRedact?: string[]
	/**
	 * Specifies the keys for request/response headers to record.
	 * This option will override `networkHeadersToRedact` if specified.
	 * `recordHeadersAndBody` needs to be `true`. Otherwise this option will be ignored.
	 * @example headerKeysToRecord: ['id', 'pageNumber']
	 */
	headerKeysToRecord?: string[]
	/**
	 * Specifies the keys for request/response JSON body to record.
	 * This option will override `networkBodyKeysToRedact` if specified.
	 * `recordHeadersAndBody` needs to be `true`. Otherwise this option will be ignored.
	 * @example bodyKeysToRecord: ['id', 'pageNumber']
	 */
	bodyKeysToRecord?: string[]
}

export interface ReactNativeOptions {
	/**
	 * The service name for the application.
	 * @default 'react-native-app'
	 */
	serviceName?: string

	/**
	 * The backend URL for the application.
	 * @default 'https://pub.observability.app.launchdarkly.com'
	 */
	backendUrl?: string

	/**
	 * The endpoint URL for the OTLP exporter.
	 * @default 'https://otel.observability.app.launchdarkly.com:4318'
	 */
	otlpEndpoint?: string

	/**
	 * The service version for the application.
	 * @default '1.0.0'
	 */
	serviceVersion?: string

	/**
	 * Additional resource attributes to include in telemetry data.
	 */
	resourceAttributes?: Attributes

	/**
	 * Custom headers to include with OTLP exports.
	 */
	customHeaders?: Record<string, string>

	/**
	 * Specifies where the backend of the app lives. If specified, the SDK will attach tracing headers to outgoing requests whose destination URLs match a substring or regexp from this list, so that backend errors can be linked back to the session.
	 * If 'true' is specified, all requests to the current domain will be matched.
	 * @example tracingOrigins: ['localhost', /^\//, 'backend.myapp.com']
	 */
	tracingOrigins?: boolean | (string | RegExp)[]

	/**
	 * URLs to not record headers and bodies for, and to not propagate trace
	 * headers (e.g. `traceparent`) to. Each entry is matched as a
	 * case-insensitive substring of the full request URL; a match suppresses
	 * both recording and trace-header propagation for that request.
	 * @example urlBlocklist: ['localhost', 'backend.myapp.com']
	 */
	urlBlocklist?: string[]

	/**
	 * Maximum inactivity, in milliseconds, before the next app launch / JS reload
	 * starts a **new** session instead of continuing the previous one. Measured
	 * from the last recorded activity to the next load.
	 *
	 * The session id is never rotated while the app is running (in-process): it is
	 * decided once per JS load and held for that load's lifetime, mirroring the
	 * native session replay / observability instance, which treats an externally
	 * supplied id as a custom session and never auto-rotates it. Session
	 * boundaries therefore only occur at a launch/reload, governed by this value.
	 *
	 * @default 15 * 60 * 1000 (15 minutes)
	 */
	sessionTimeout?: number

	/**
	 * Debug mode - enables additional logging.
	 * @default false
	 */
	debug?: boolean

	/**
	 * Whether errors tracking is disabled.
	 */
	disableErrorTracking?: boolean

	/**
	 * Whether logs are disabled.
	 */
	disableLogs?: boolean

	/**
	 * Disables public custom tracing APIs (`startSpan`, `startActiveSpan`,
	 * `withSpan`, `getTracer()`, `track`, `runWithHeaders`, `startWithHeaders`).
	 * SDK auto-instrumentation (network requests, internal telemetry) is unaffected.
	 */
	disableTraces?: boolean

	/**
	 * Whether metrics are disabled.
	 */
	disableMetrics?: boolean

	/**
	 * Options for recording network request and response headers and bodies,
	 * with controls for redacting sensitive data.
	 */
	networkRecording?: NetworkRecordingOptions

	/**
	 * A function that returns a friendly name for a given context.
	 * This name will be used to identify the session in the observability UI.
	 * ```ts
	 * contextFriendlyName: (context: LDContext) => {
	 *   if(context.kind === 'multi' && context.user?.email) {
	 *     return context.user.email;
	 *   } else if(context.kind === 'user') {
	 *     return context.key;
	 *   }
	 *   // Use the default identifier for contexts which don't contain a user.
	 *   return undefined;
	 * }
	 * ```
	 * @param context The context to get a friendly name for.
	 * @returns The friendly name for the context, or undefined to use the
	 * default identifier.
	 */
	contextFriendlyName?: (context: LDContext) => string | undefined

	/**
	 * The time window, in milliseconds, during which repeated feature flag
	 * evaluations that resolve to the same result are deduplicated, so that only
	 * a single `feature_flag` exposure span is emitted per unique
	 * (flag key, value, variation, reason, context) within the window.
	 *
	 * This is useful for reducing exposure volume caused by frequent
	 * re-evaluations (for example, React re-renders).
	 *
	 * Set to `0` (the default) to disable deduplication and emit an exposure
	 * for every evaluation. Set a positive value to enable it.
	 *
	 * @default 0 (disabled)
	 */
	flagExposureDedupeWindowMillis?: number

	/**
	 * The maximum number of unique feature flag exposure keys tracked for
	 * deduplication at once. When exceeded, the least recently recorded keys are
	 * evicted to bound memory usage.
	 *
	 * @default 2000
	 */
	flagExposureDedupeMaxSize?: number

	/**
	 * The maximum number of spans and log records held in the in-memory export
	 * buffer before the oldest are dropped. Applied to both traces and logs.
	 *
	 * Telemetry is buffered in memory only (there is no on-disk persistence), so
	 * this value bounds how much can be retained while the device is offline or
	 * between uploads. When the buffer is full, newly recorded items are dropped
	 * until space frees up (the already-buffered items are kept and exported).
	 * Larger values retain more data across short outages at the cost of memory;
	 * anything still buffered is lost if the app is terminated.
	 *
	 * @default 1000
	 */
	maxBufferSize?: number

	/**
	 * The delay, in milliseconds, between scheduled uploads of buffered spans
	 * and log records. Applied to both traces and logs. Lower values upload more
	 * frequently in smaller batches; higher values upload less frequently in
	 * larger batches.
	 *
	 * @default 500
	 */
	uploadIntervalMillis?: number
}
