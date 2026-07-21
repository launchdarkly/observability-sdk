import { NativeModules, TurboModuleRegistry } from 'react-native'

/**
 * Reads the wall-clock time (ms since epoch) at which the current OS process
 * first touched the native session-replay module, if that module is installed.
 *
 * The value is process-global native state: it survives a JS soft/OTA reload
 * (same process) and is regenerated on a cold start (new process). Comparing a
 * persisted session's `lastActivityTime` against it lets the session manager
 * tell a cold restart from a surviving process.
 *
 * Returns `undefined` when the native module isn't available — e.g. an
 * observability-only app (no session replay), React Native for Web, or an older
 * native module without this seam. Callers then fall back to purely time-based
 * resume, which is safe because the cold-start payload collision only exists
 * when native session replay is present.
 */
export function getNativeProcessStartTimeMs(): number | undefined {
	try {
		const mod =
			(
				TurboModuleRegistry as {
					get?: (name: string) => unknown
				} | null
			)?.get?.('SessionReplayReactNative') ??
			(NativeModules as Record<string, unknown> | null)?.[
				'SessionReplayReactNative'
			]
		const getter = (
			mod as { getProcessStartTimeMillis?: () => unknown } | undefined
		)?.getProcessStartTimeMillis
		if (typeof getter === 'function') {
			const value = getter()
			if (
				typeof value === 'number' &&
				Number.isFinite(value) &&
				value > 0
			) {
				return value
			}
		}
	} catch {
		// Native module unavailable or threw — fall back to time-based resume.
	}
	return undefined
}
