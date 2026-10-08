package com.launchdarkly.observability.replay

data class Position(
    val x: Int,
    val y: Int,
    val timestamp: Long,
)

/**
 * A scaled touch interaction for replay, describing only where and when the pointer moved.
 *
 * The tapped element is deliberately not described here: replay renders `Click` events from
 * Observability's click funnel instead, which is the only source that also sees clicks an embedder
 * resolved in its own UI tree.
 */
data class InteractionEvent(
    val action: Int,
    val positions: List<Position>,
    val session: String,
)
