/**
 * SDK-internal diagnostics.
 *
 * Everything the SDK says about itself (exporter failures, init warnings,
 * internal catch blocks) goes through here rather than straight to `console`:
 *
 * 1. It only reaches the developer console when the SDK is in debug mode
 *    (`debug: true` or a `DebugOptions` object in the plugin options, or a
 *    `DEBUG=true` build). A production page is not filled with
 *    `[@launchdarkly/observability]` lines the app owner cannot act on.
 * 2. While it is being written, the console recorder is told to stand down
 *    (see `isSdkConsoleOutput`), so the line is never captured and shipped
 *    back as one of the customer's own log records. Before this, a failing
 *    OTLP export surfaced as error-level `launchdarkly.js.log` rows in the
 *    customer's own project.
 */

export type SdkLogLevel = 'debug' | 'log' | 'info' | 'warn' | 'error'

const SDK_LOG_LEVELS: readonly SdkLogLevel[] = [
	'debug',
	'log',
	'info',
	'warn',
	'error',
]

type SdkLoggerState = { debugEnabled: boolean; suppressDepth: number }

/**
 * `@launchdarkly/observability` and `@launchdarkly/session-replay` each bundle
 * their own copy of this module, and the console recorder lives in only one
 * of them. A module-level counter would therefore not be seen across copies:
 * session replay's diagnostics would be recorded by the observability
 * plugin's console recorder (and the bundler drops the counter entirely in a
 * copy that never reads it). Keep the state on the global object under a
 * registry symbol so every copy on the page shares it.
 */
const SDK_LOGGER_STATE_KEY = Symbol.for('@launchdarkly/o11y.sdkLogger')

const buildDebugEnabled = (() => {
	try {
		return import.meta.env?.DEBUG === 'true'
	} catch {
		return false
	}
})()

const state: SdkLoggerState = (() => {
	const holder = globalThis as unknown as Record<symbol, SdkLoggerState>
	const shared = (holder[SDK_LOGGER_STATE_KEY] ??= {
		debugEnabled: false,
		suppressDepth: 0,
	})
	if (buildDebugEnabled) {
		shared.debugEnabled = true
	}
	return shared
})()

/**
 * Turns SDK-internal console diagnostics on or off. Called by the SDK entry
 * points when the `debug` option is set; tests may call it directly.
 */
export const setSdkDebugLogging = (enabled: boolean) => {
	state.debugEnabled = enabled
}

export const isSdkDebugLogging = () => state.debugEnabled

/**
 * True while the SDK itself is writing to the console. The console recorder
 * checks this and skips the call so SDK diagnostics never become customer
 * log records, in any mode.
 */
export const isSdkConsoleOutput = () => state.suppressDepth > 0

/**
 * Runs `fn` with console recording suspended. Console patches are
 * synchronous, so a depth counter is enough to scope the suspension.
 */
export const withoutConsoleRecording = <T>(fn: () => T): T => {
	state.suppressDepth++
	try {
		return fn()
	} finally {
		state.suppressDepth--
	}
}

const toSdkLogLevel = (level: string): SdkLogLevel =>
	(SDK_LOG_LEVELS as readonly string[]).includes(level)
		? (level as SdkLogLevel)
		: 'log'

/**
 * Writes an SDK-internal diagnostic to the console, but only in debug mode,
 * and never into the console recorder.
 */
export const sdkLog = (level: SdkLogLevel | string, ...args: unknown[]) => {
	if (!state.debugEnabled) {
		return
	}
	withoutConsoleRecording(() => {
		try {
			const method = console[toSdkLogLevel(level)] ?? console.log
			method.apply(console, args)
		} catch {
			// a broken console must never break the SDK
		}
	})
}
