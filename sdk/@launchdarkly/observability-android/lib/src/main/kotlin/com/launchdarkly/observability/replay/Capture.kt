package com.launchdarkly.observability.replay

data class Capture(
    val imageBase64: String,
    val origHeight: Int,
    val origWidth: Int,
    val timestamp: Long,
    val session: String
)
