package com.launchdarkly.observability.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLaunchTrackerTest {
    @Test
    fun `no stored version is an install`() {
        assertEquals(
            AppLaunchSignal.LaunchType.INSTALL,
            AppLaunchTracker.classify(storedVersion = null, currentVersion = "1.0.0")
        )
    }

    @Test
    fun `same version is a relaunch`() {
        assertEquals(
            AppLaunchSignal.LaunchType.RELAUNCH,
            AppLaunchTracker.classify(storedVersion = "1.0.0", currentVersion = "1.0.0")
        )
    }

    @Test
    fun `changed version is an update`() {
        assertEquals(
            AppLaunchSignal.LaunchType.UPDATE,
            AppLaunchTracker.classify(storedVersion = "1.0.0", currentVersion = "1.1.0")
        )
    }
}
