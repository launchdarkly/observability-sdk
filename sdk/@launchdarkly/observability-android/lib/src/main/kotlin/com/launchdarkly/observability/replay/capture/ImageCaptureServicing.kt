package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.sdk.ImageCapturing

interface ImageCaptureServicing : ImageCapturing {
    suspend fun captureRawFrame(): RawFrame?
}

typealias RawFrame = ImageCaptureService.RawFrame
