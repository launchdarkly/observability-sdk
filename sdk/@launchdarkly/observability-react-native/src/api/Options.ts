import { ResourceAttributes } from '@opentelemetry/resources'

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
	resourceAttributes?: ResourceAttributes

	/**
	 * Custom headers to include with OTLP exports.
	 */
	customHeaders?: Record<string, string>

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
