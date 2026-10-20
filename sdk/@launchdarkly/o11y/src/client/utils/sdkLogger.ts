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

let debugEnabled = (() => {
	try {
		return import.meta.env?.DEBUG === 'true'
	} catch {
		return false
	}
})()

let suppressDepth = 0

/**
 * Turns SDK-internal console diagnostics on or off. Called by the SDK entry
 * points when the `debug` option is set; tests may call it directly.
 */
export const setSdkDebugLogging = (enabled: boolean) => {
	debugEnabled = enabled
}

export const isSdkDebugLogging = () => debugEnabled

/**
 * True while the SDK itself is writing to the console. The console recorder
 * checks this and skips the call so SDK diagnostics never become customer
 * log records, in any mode.
 */
export const isSdkConsoleOutput = () => suppressDepth > 0

/**
 * Runs `fn` with console recording suspended. Console patches are
 * synchronous, so a depth counter is enough to scope the suspension.
 */
export const withoutConsoleRecording = <T>(fn: () => T): T => {
	suppressDepth++
	try {
		return fn()
	} finally {
		suppressDepth--
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
	if (!debugEnabled) {
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
