import type {
	ConsoleMethods,
	NetworkRecordingOptions,
	OtelOptions,
} from './client'
import type { CommonOptions } from './types'
import type { EventName } from '@opentelemetry/instrumentation-user-interaction'

export type ObserveOptions = CommonOptions & {
	/**
	 * Specifies where the backend of the app lives. If specified, the SDK will attach the
	 * traceparent header to outgoing requests whose destination URLs match a substring
	 * or regexp from this list, so that backend errors can be linked back to the session.
	 * If 'true' is specified, all requests to the current domain will be matched.
	 * @example tracingOrigins: ['localhost', /^\//, 'backend.myapp.com']
	 */
	tracingOrigins?: boolean | (string | RegExp)[]
	/**
	 * Specifies how and what the SDK records from network requests and responses.
	 */
	networkRecording?: boolean | NetworkRecordingOptions
	/**
	 * Specifies whether the SDK will record console messages.
	 * @default false
	 */
	disableConsoleRecording?: boolean
	/**
	 * Specifies whether the SDK will report `console.error` invocations as Errors.
	 * @default false
	 */
	reportConsoleErrors?: boolean
	/**
	 * Specifies which console methods to record.
	 * The value here will be ignored if `disabledConsoleRecording` is `true`.
	 * @default All console methods.
	 * @example consoleMethodsToRecord: ['log', 'info', 'error']
	 */
	consoleMethodsToRecord?: ConsoleMethods[]
	/**
	 * Specifies whether to record performance metrics (e.g. FPS, device memory).
	 * @default true
	 */
	enablePerformanceRecording?: boolean
	/**
	 * Specifies the environment your application is running in.
	 * This is useful to distinguish whether your session was recorded on localhost or in production.
	 * @default 'production'
	 */
	environment?: 'development' | 'staging' | 'production' | string
	/**
	 * Specifies whether window.Promise should be patched
	 * to record the stack trace of promise rejections.
	 * @default true
	 */
	enablePromisePatch?: boolean
	/**
	 * OTLP options for OpenTelemetry tracing. Instrumentations are enabled by default.
	 */
	otel?: OtelOptions & {
		/**
		 * OTLP HTTP endpoint for OpenTelemetry tracing.
		 */
		otlpEndpoint?: string
		/**
		 * User interaction instrumentation event names to record.
		 * Defaults to 'click', 'input', 'submit' window events.
		 */
		eventNames?: EventName[]
	}
}
