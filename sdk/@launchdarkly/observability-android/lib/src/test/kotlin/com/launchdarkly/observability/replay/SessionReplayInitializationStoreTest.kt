package com.launchdarkly.observability.replay

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionReplayInitializationStoreTest {

    @Test
    fun `no failure is stored by default`() {
        val store = SessionReplayInitializationStore(prefs = fakePrefs(), sdkKey = "mob-key")

        assertNull(store.loadFailure())
    }

    @Test
    fun `stored failure survives a new store for the same environment`() {
        val prefs = fakePrefs()
        SessionReplayInitializationStore(prefs, sdkKey = "mob-key")
            .store(reason = "SESSION_REPLAY_BLOCKED_IN_REGION", timestamp = 42L)

        val failure = SessionReplayInitializationStore(prefs, sdkKey = "mob-key").loadFailure()

        assertEquals("SESSION_REPLAY_BLOCKED_IN_REGION", failure?.reason)
        assertEquals(42L, failure?.timestamp)
    }

    @Test
    fun `failure from another environment is ignored`() {
        val prefs = fakePrefs()
        SessionReplayInitializationStore(prefs, sdkKey = "mob-staging").store(reason = "unauthorized")

        assertNull(SessionReplayInitializationStore(prefs, sdkKey = "mob-production").loadFailure())
        assertNotNull(SessionReplayInitializationStore(prefs, sdkKey = "mob-staging").loadFailure())
    }

    @Test
    fun `clearing removes the stored failure`() {
        val store = SessionReplayInitializationStore(fakePrefs(), sdkKey = "mob-key")
        store.store(reason = "unauthorized")
        store.clearFailure()

        assertNull(store.loadFailure())
    }

    @Test
    fun `long reasons are truncated`() {
        val store = SessionReplayInitializationStore(fakePrefs(), sdkKey = "mob-key")
        store.store(reason = "x".repeat(SessionReplayInitializationStore.MAX_REASON_LENGTH * 3))

        assertEquals(
            SessionReplayInitializationStore.MAX_REASON_LENGTH,
            store.loadFailure()?.reason?.length
        )
    }

    @Test
    fun `unreadable stored values are ignored`() {
        val prefs = fakePrefs()
        prefs.edit()
            .putString(SessionReplayInitializationStore.KEY_LAST_UNRECOVERABLE_FAILURE, "not json")
            .apply()

        assertNull(SessionReplayInitializationStore(prefs, sdkKey = "mob-key").loadFailure())
    }

    @Test
    fun `the sdk key is never written to disk`() {
        val prefs = fakePrefs()
        SessionReplayInitializationStore(prefs, sdkKey = "mob-secret-key").store(reason = "unauthorized")

        val stored = prefs.getString(SessionReplayInitializationStore.KEY_LAST_UNRECOVERABLE_FAILURE, null)

        assertNotNull(stored)
        assertFalse(stored!!.contains("mob-secret-key"))
    }

    /** In-memory stand-in for [SharedPreferences], enough for the string get/put/remove this store uses. */
    private fun fakePrefs(): SharedPreferences {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)

        every { prefs.getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }

        return prefs
    }
}
