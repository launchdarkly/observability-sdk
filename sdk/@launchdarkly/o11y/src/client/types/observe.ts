import type {
	ConsoleMethods,
	NetworkRecordingOptions,
	OtelOptions,
} from './client'
import type { CommonOptions } from './types'

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
	 * Specifies whether console output written by the LaunchDarkly SDK's own
	 * logger (lines prefixed `[LaunchDarkly]`, e.g. stream connection
	 * open/close messages) is recorded as logs. Those lines describe the SDK
	 * rather than your application, and the streaming URL they contain embeds
	 * the evaluation context, so they are skipped by default.
	 * @default false
	 */
	recordLaunchDarklySdkLogs?: boolean
	/**
	 * Specifies whether to record performance metrics (e.g. FPS, device memory).
	 * @default true
	 */
	enablePerformanceRecording?: boolean
	/**
	 * Specifies whether to record main-thread `longtask` entries (>50ms) as
	 * `long_task.duration` histogram samples.
	 * @default true
	 */
	enableLongtaskRecording?: boolean
	/**
	 * Specifies whether to subscribe to the browser Reporting API and emit
	 * CSP/intervention reports as errors and deprecation reports as warn logs.
	 * @default true
	 */
	enableReportingObserver?: boolean
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
	}
	/**
	 * Specifies whether to record product analytics events.
	 * @default true
	 */
	productAnalytics?: boolean | ProductAnalyticsEvents
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
	 * Query parameters to read from the page URL and add as attributes to all
	 * telemetry, as if passed to `LDObserve.setAttributes`.
	 *
	 * When the SDK initializes, each listed parameter present in
	 * `location.search` becomes an attribute named after the parameter (e.g.
	 * `utm_campaign`) on every span (including document load, page view and
	 * fetch/XHR spans), log, error and metric for the rest of the page load,
	 * including after SPA navigation drops it from the URL.
	 *
	 * Last touch: when an SPA navigation lands on a URL with any listed
	 * parameter, the captured values are replaced by that URL's values, and
	 * previously captured keys missing from it are removed, so the set always
	 * reflects one landing. A navigation without listed parameters keeps the
	 * current values. Spans already started keep the values they had.
	 *
	 * Values are kept in memory only; nothing is written to storage or
	 * cookies, so a later full page load captures again from its own URL.
	 *
	 * Only the listed keys are captured, so other query parameters (which may
	 * hold personal data such as emails or tokens) are never recorded this
	 * way. The default list contains UTM parameters only. A name ending in `*`
	 * matches every parameter with that prefix (e.g. `'utm_*'`).
	 *
	 * Keys set with `setAttributes` win: capture never replaces or removes a
	 * key the app set itself. `removeAttributes` removes a captured value
	 * (a later landing with that parameter captures it again). Keys in the
	 * namespaces the SDK sets itself (`highlight.*`, `launchdarkly.*`,
	 * `session.*`, `context.contextKeys.*`) are ignored.
	 *
	 * A custom list replaces the default. Set to `false` or `[]` to turn
	 * capture off.
	 * @default ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content']
	 * @example captureQueryParams: ['utm_source', 'utm_campaign', 'gclid']
	 */
	captureQueryParams?: string[] | false
	/**
	 * Specifies whether to record the page that linked to this one. When the
	 * SDK initializes and `document.referrer` is a different origin, it is
	 * added as the `landing.referrer` attribute to all telemetry for the rest
	 * of the page load, as if passed to `LDObserve.setAttributes`.
	 *
	 * Only the origin and path are kept: the query string, fragment and any
	 * credentials are stripped so tokens or personal data in the referrer URL
	 * are not recorded. An empty or same-origin referrer sets nothing, and
	 * the value is not updated on SPA navigation.
	 * @default true
	 */
	captureReferrer?: boolean
}

export type ProductAnalyticsEvents = {
	/**
	 * Specifies whether to record product analytics for clicks.
	 * Requires the use of the @opentelemetry/instrumentation-user-interaction instrumentation.
	 * @default true
	 */
	clicks?: boolean
	/**
	 * Specifies whether to record product analytics for page views.
	 * Requires the use of the @opentelemetry/instrumentation-document-load instrumentation.
	 * @default true
	 */
	pageViews?: boolean
	/**
	 * Specifies whether to record product analytics for custom events.
	 * @default true
	 */
	trackEvents?: boolean
}
