package com.launchdarkly.observability.replay

import kotlin.random.Random

internal object SessionReplaySampling {
    fun shouldSample(sampleRate: Double, randomValue: () -> Double = { Random.nextDouble() }): Boolean {
        if (sampleRate <= 0.0) return false
        if (sampleRate >= 1.0) return true

        return randomValue() < sampleRate
    }
}
