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
	 * A list of URLs to block from tracing.
	 * @example urlBlocklist: ['localhost', 'backend.myapp.com']
	 */
	urlBlocklist?: string[]

	/**
	 * Session timeout in milliseconds.
	 * @default 30 * 60 * 1000 (30 minutes)
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
	 * Whether traces are disabled.
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
}
