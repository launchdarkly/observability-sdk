package com.example.androidobservability

import com.launchdarkly.observability.testing.ObservabilityDispatcherTestHooks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineRule : TestWatcher() {
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    val dispatcher: TestDispatcher
        get() = testDispatcher

    override fun starting(description: Description) {
        ObservabilityDispatcherTestHooks.overrideWith(testDispatcher)
    }

    override fun finished(description: Description) {
        ObservabilityDispatcherTestHooks.reset()
    }
}
