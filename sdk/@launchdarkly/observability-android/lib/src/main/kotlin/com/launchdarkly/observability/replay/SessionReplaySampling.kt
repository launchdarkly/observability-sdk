package com.launchdarkly.observability.replay

import kotlin.random.Random

internal object SessionReplaySampling {
    fun shouldSample(
        sampleRate: Double,
        randomValue: () -> Double = { Random.nextDouble() },
    ): Boolean {
        if (sampleRate <= 0.0) return false
        if (sampleRate >= 1.0) return true
        return randomValue() < sampleRate
    }
}

/**
 * Tracks whether sampling has been decided for the current enable cycle.
 * Mirrors `SessionReplaySamplingSession` in the Swift session replay SDK.
 */
internal class SessionReplaySamplingSession {
    private var decisionMade = false

    @Synchronized
    fun shouldStartCapture(
        ignoreSampling: Boolean,
        sampleRate: Double,
        randomValue: () -> Double = { Random.nextDouble() },
    ): Boolean {
        if (ignoreSampling) return true
        if (decisionMade) return false
        decisionMade = true
        return SessionReplaySampling.shouldSample(sampleRate, randomValue)
    }

    @Synchronized
    fun reset() {
        decisionMade = false
    }
}
