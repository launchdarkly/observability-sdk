package com.launchdarkly.observability.replay

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Outcome of a Session Replay request as far as recording is concerned. Any of them can refuse the launch,
 * including the `identifyReplaySession` that follows a successful `initializeReplaySession`. Reported by
 * [com.launchdarkly.observability.replay.exporter.SessionReplayExporter], which is public, hence public.
 */
sealed interface SessionReplayInitializationVerdict {
    /** The backend accepted the session, so recording may run. */
    data object Allowed : SessionReplayInitializationVerdict

    /**
     * The backend refused in a way retrying cannot fix (an unrecoverable status, or a GraphQL error
     * marked non-retryable), so recording must stop for this launch.
     */
    data class Unrecoverable(val reason: String) : SessionReplayInitializationVerdict
}

/**
 * The last unrecoverable failure, as persisted between launches.
 */
@Serializable
internal data class SessionReplayInitializationFailure(
    val reason: String,
    val timestamp: Long,
)

/**
 * Disk cache of the last unrecoverable Session Replay initialization failure, so the next launch can
 * hold off on taking screenshots until the backend has answered again.
 *
 * Only failures are stored: no record means "record immediately", which keeps the common path free of a
 * startup write. The read shares the preference file
 * [com.launchdarkly.observability.client.AppLaunchTracker] already loads during Observability start, so
 * it does not add a disk hit of its own.
 */
internal class SessionReplayInitializationStore(
    private val prefs: SharedPreferences,
    sdkKey: String,
) {
    private val failureKey = failureKey(sdkKey)

    /** The stored failure for this environment, or `null` when there is none. */
    fun loadFailure(): SessionReplayInitializationFailure? {
        val stored = prefs.getString(failureKey, null) ?: return null

        return decode(stored)
    }

    fun store(reason: String, timestamp: Long = System.currentTimeMillis()) {
        val failure = SessionReplayInitializationFailure(
            reason = reason.take(MAX_REASON_LENGTH),
            timestamp = timestamp,
        )
        prefs.edit().putString(failureKey, encode(failure)).apply()
    }

    fun clearFailure() {
        prefs.edit().remove(failureKey).apply()
    }

    internal companion object {
        const val KEY_PREFIX_LAST_UNRECOVERABLE_FAILURE = "sessionReplayLastUnrecoverableFailure"

        /**
         * The reason is diagnostic only, and an error message can carry a whole response body, so it is
         * kept short enough to stay cheap to read at every launch.
         */
        const val MAX_REASON_LENGTH = 512

        private val json = Json { ignoreUnknownKeys = true }

        fun encode(failure: SessionReplayInitializationFailure): String =
            json.encodeToString(SessionReplayInitializationFailure.serializer(), failure)

        fun decode(stored: String): SessionReplayInitializationFailure? = try {
            json.decodeFromString(SessionReplayInitializationFailure.serializer(), stored)
        } catch (_: Exception) {
            null
        }

        /**
         * Entitlements differ per environment, so a verdict from one must neither gate another nor
         * overwrite what is cached for it - hence a key per SDK key rather than a shared one.
         */
        fun failureKey(sdkKey: String): String =
            "$KEY_PREFIX_LAST_UNRECOVERABLE_FAILURE.${fingerprint(sdkKey)}"

        /**
         * Distinguishes environments without writing the SDK key itself to disk. Only equality matters,
         * so a truncated digest is enough.
         */
        private fun fingerprint(sdkKey: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sdkKey.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
    }
}
