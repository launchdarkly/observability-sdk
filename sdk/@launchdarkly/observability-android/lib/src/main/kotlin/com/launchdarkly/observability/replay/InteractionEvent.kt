package com.launchdarkly.observability.replay

data class Position(
    val x: Int,
    val y: Int,
    val timestamp: Long,
)

data class InteractionEvent(
    val action: Int,
    val positions: List<Position>,
    val session: String,
    val targetClassName: String? = null,
    val targetText: String? = null,
    val targetResourceId: String? = null,
)
