package com.launchdarkly.observability.replay

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Outcome of an `initializeSession` / `pushPayload` attempt as far as recording is concerned. Reported by
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
    /**
     * Fingerprint of the SDK key the failure was produced for. Entitlements differ per environment, so
     * a verdict from one must not gate another.
     */
    val environment: String,
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
    private val environment = fingerprint(sdkKey)

    /** The stored failure, or `null` when there is none or it belongs to a different environment. */
    fun loadFailure(): SessionReplayInitializationFailure? {
        val stored = prefs.getString(KEY_LAST_UNRECOVERABLE_FAILURE, null) ?: return null

        return decode(stored)?.takeIf { it.environment == environment }
    }

    fun store(reason: String, timestamp: Long = System.currentTimeMillis()) {
        val failure = SessionReplayInitializationFailure(
            reason = reason.take(MAX_REASON_LENGTH),
            timestamp = timestamp,
            environment = environment,
        )
        prefs.edit().putString(KEY_LAST_UNRECOVERABLE_FAILURE, encode(failure)).apply()
    }

    fun clearFailure() {
        prefs.edit().remove(KEY_LAST_UNRECOVERABLE_FAILURE).apply()
    }

    internal companion object {
        const val KEY_LAST_UNRECOVERABLE_FAILURE = "sessionReplayLastUnrecoverableFailure"

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
         * Distinguishes environments without writing the SDK key itself to disk. Only equality matters,
         * so a truncated digest is enough.
         */
        fun fingerprint(sdkKey: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sdkKey.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
    }
}
