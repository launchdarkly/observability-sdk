package com.launchdarkly.observability.replay

data class InteractionEvent(
    val x: Int,
    val y: Int,
    val maxX: Int,
    val maxY: Int,
    val timestamp: Long,
    val session: String
)
