package com.launchdarkly.observability.replay.capture

interface ImageCaptureServicing {
    suspend fun captureRawFrame(): RawFrame?
}

typealias RawFrame = ImageCaptureService.RawFrame
