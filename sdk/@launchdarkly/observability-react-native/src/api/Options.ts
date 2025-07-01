import { ResourceAttributes } from '@opentelemetry/resources'

export interface ReactNativeOptions {
	/**
	 * The endpoint URL for the OTLP exporter.
	 * @default 'https://otlp.highlight.io:4318'
	 */
	otlpEndpoint?: string

	/**
	 * The service name for the application.
	 * @default 'react-native-app'
	 */
	serviceName?: string

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
	 * Whether to enable console logging of telemetry data.
	 * @default false
	 */
	enableConsoleLogging?: boolean

	/**
	 * Whether to enable automatic error tracking.
	 * @default true
	 */
	enableErrorTracking?: boolean

	/**
	 * Whether to enable automatic performance monitoring.
	 * @default true
	 */
	enablePerformanceMonitoring?: boolean

	/**
	 * Whether to enable automatic trace collection.
	 * @default true
	 */
	enableTracing?: boolean

	/**
	 * Whether to enable automatic metrics collection.
	 * @default true
	 */
	enableMetrics?: boolean

	/**
	 * Whether to enable automatic log collection.
	 * @default true
	 */
	enableLogs?: boolean

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
	 * Whether to enable native crash reporting.
	 * @default true
	 */
	enableNativeCrashReporting?: boolean

	/**
	 * Debug mode - enables additional logging.
	 * @default false
	 */
	debug?: boolean
}
