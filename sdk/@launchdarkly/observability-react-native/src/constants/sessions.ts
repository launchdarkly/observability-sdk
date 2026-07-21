/**
 * Maximum gap between a session's last recorded activity and the next JS load
 * for that load to *continue* the previous session instead of starting a new
 * one.
 *
 * This window only applies to a **surviving process** — a soft/OTA reload where
 * the native app process stayed alive. A **cold start** (the app was terminated
 * and relaunched) always begins a fresh session regardless of this window,
 * because the native session-replay recording restarts its payload sequence at
 * 0 on a new process and reusing the old session id would corrupt the previous
 * recording on the backend (see SessionManager.resolveSession, which uses the
 * native process start time to tell the two apart).
 *
 * 15 minutes mirrors the web SDK's `SESSION_PUSH_THRESHOLD` so reload/session
 * continuity behaves consistently across the browser and React Native SDKs.
 */
export const SESSION_RESUME_THRESHOLD_MS = 15 * 60 * 1000 // 15 minutes

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
