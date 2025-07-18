import { Attributes } from '@opentelemetry/api'

export interface ReactNativeOptions {
	/**
	 * The service name for the application.
	 * @default 'react-native-app'
	 */
	serviceName?: string

	/**
	 * The endpoint URL for the OTLP exporter.
	 * @default 'https://otlp.highlight.io:4318'
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
}
