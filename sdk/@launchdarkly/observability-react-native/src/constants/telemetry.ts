/**
 * Maximum number of spans/log records held in the in-memory export buffer.
 * Once full, newly recorded items are dropped until space frees up (buffered
 * items are kept and exported). Applied to both the trace and log batch
 * processors. Telemetry is buffered in memory only, so this bounds how much
 * can be retained while offline or between flushes.
 */
export const DEFAULT_MAX_BUFFER_SIZE = 2048

/**
 * Delay, in milliseconds, between scheduled flushes of the in-memory export
 * buffer for traces and logs.
 */
export const DEFAULT_UPLOAD_INTERVAL_MILLIS = 5000

/**
 * Maximum number of spans/log records sent in a single export batch.
 */
export const DEFAULT_MAX_EXPORT_BATCH_SIZE = 512

/**
 * Maximum time, in milliseconds, a single export may take before it is
 * considered failed.
 */
export const DEFAULT_EXPORT_TIMEOUT_MILLIS = 30000
