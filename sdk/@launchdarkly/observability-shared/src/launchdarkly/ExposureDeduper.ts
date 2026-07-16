/**
 * Default exposure dedupe window, in milliseconds (3 minutes).
 */
export const DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS = 3 * 60 * 1000

/**
 * Tracks recently recorded feature flag exposures so that repeated evaluations
 * resolving to the same result do not emit a new exposure within a time window.
 *
 * Each unique exposure key is only recorded once per window.
 *
 * Shared by the web (`@launchdarkly/observability`) and React Native
 * (`@launchdarkly/observability-react-native`) plugins.
 */
export class ExposureDeduper {
	private readonly windowMillis: number
	private readonly lastRecordedAt = new Map<string, number>()

	/**
	 * @param windowMillis The dedupe window in milliseconds. A value of `0` (or
	 * negative) disables deduplication, so every exposure is recorded.
	 */
	constructor(
		windowMillis: number = DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS,
	) {
		this.windowMillis = windowMillis
	}

	/**
	 * Returns `true` if an exposure for the given key should be recorded (i.e. no
	 * exposure for the same key was recorded within the window). Returns `false`
	 * if it should be suppressed.
	 *
	 * This is a pure query and does NOT update the window. Call
	 * {@link markRecorded} only after the exposure has actually been emitted, so
	 * that a failure to emit doesn't suppress subsequent exposures.
	 *
	 * @param key A stable key identifying the exposure (e.g. flag key, value,
	 * variation, reason, and context).
	 * @param now The current time in milliseconds. Defaults to `Date.now()`.
	 */
	shouldRecord(key: string, now: number = Date.now()): boolean {
		if (this.windowMillis <= 0) {
			return true
		}

		const last = this.lastRecordedAt.get(key)
		return last === undefined || last <= now - this.windowMillis
	}

	/**
	 * Marks an exposure for the given key as recorded at `now`, starting a new
	 * dedupe window. Call this only after the exposure has been successfully
	 * emitted.
	 *
	 * @param key A stable key identifying the exposure.
	 * @param now The current time in milliseconds. Defaults to `Date.now()`.
	 */
	markRecorded(key: string, now: number = Date.now()): void {
		if (this.windowMillis <= 0) {
			return
		}

		this.lastRecordedAt.set(key, now)
	}

	/**
	 * Clears all recorded exposures. Call when the evaluation context changes
	 * (e.g. after `identify`).
	 */
	reset(): void {
		this.lastRecordedAt.clear()
	}
}
