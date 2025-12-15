package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.sdk.android.LDClient
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InstrumentationContributorManagerTest {

    private lateinit var client: LDClient

    @BeforeEach
    fun setUp() {
        client = mockk()
    }

    @AfterEach
    fun tearDown() {
        InstrumentationContributorManager.reset()
    }

    @Test
    fun `add() stores contributors associated to a client and get() returns them`() {
        val contributorOne = MockInstrumentationContributor()
        val contributorTwo = MockInstrumentationContributor()
        val contributorThree = MockInstrumentationContributor()

        InstrumentationContributorManager.add(client, contributorOne)
        InstrumentationContributorManager.add(client, contributorTwo)

        val firstSnapshot = InstrumentationContributorManager.get(client)

        InstrumentationContributorManager.add(client, contributorThree)

        val secondSnapshot = InstrumentationContributorManager.get(client)

        assertEquals(listOf(contributorOne, contributorTwo), firstSnapshot)
        assertEquals(listOf(contributorOne, contributorTwo, contributorThree), secondSnapshot)
    }

    @Test
    fun `reset clears all contributors`() {
        val contributorOne = MockInstrumentationContributor()
        val contributorTwo = MockInstrumentationContributor()

        InstrumentationContributorManager.add(client, contributorOne)
        InstrumentationContributorManager.add(client, contributorTwo)

        val firstSnapshot = InstrumentationContributorManager.get(client)

        InstrumentationContributorManager.reset()

        val secondSnapshot = InstrumentationContributorManager.get(client)

        assertEquals(listOf(contributorOne, contributorTwo), firstSnapshot)
        assertTrue(secondSnapshot.isEmpty())
    }

    private class MockInstrumentationContributor() : InstrumentationContributor {
        override fun provideInstrumentations(): List<LDExtendedInstrumentation> = emptyList()
    }
}
