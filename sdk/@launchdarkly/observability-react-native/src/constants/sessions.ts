/**
 * Maximum time between the last recorded activity of a session and the next JS
 * load for that load to be treated as a *continuation* (reload) of the same
 * session rather than a brand-new session.
 *
 * Mirrors the web SDK's `SESSION_PUSH_THRESHOLD` (15 minutes) so reload/session
 * continuity behaves consistently across the browser and React Native SDKs.
 */
export const SESSION_RESUME_THRESHOLD_MS = 15 * 60 * 1000

/**
 * Storage key under which the current session is persisted so it can be resumed
 * after a JS reload (soft reload / OTA reload / quick relaunch).
 */
export const SESSION_STORAGE_KEY = 'launchdarkly:observability:session'

/**
 * Span name emitted once on the JS load that resumes a previous session,
 * marking the reload boundary.
 */
export const APP_RELOAD_SPAN_NAME = 'app_reload'
