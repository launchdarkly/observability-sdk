package com.launchdarkly.observability.replay.capture

/**
 * Represents an exported frame for replay instrumentation.
 */
data class ExportFrame(
    val keyFrameId: Int,
    val addImages: List<AddImage>,
    val removeImages: List<RemoveImage>?,
    val originalSize: IntSize,
    val scale: Float,
    val format: ExportFormat,
    val timestamp: Long,
    val orientation: Int,
    val isKeyframe: Boolean,
    val imageSignature: ImageSignature?,
    val session: String
){
    constructor(
        imageBase64: String,
        origHeight: Int,
        origWidth: Int,
        timestamp: Long,
        session: String,
    ) : this(
        keyFrameId = 0,
        addImages = listOf(
            AddImage(
                imageBase64 = imageBase64,
                rect = IntRect(left = 0, top = 0, width = origWidth, height = origHeight),
                imageSignature = null
            )
        ),
        removeImages = null,
        originalSize = IntSize(width = origWidth, height = origHeight),
        scale = 1f,
        format = ExportFormat.Webp(quality = 30),
        timestamp = timestamp,
        orientation = 0,
        isKeyframe = true,
        imageSignature = null,
        session = session,
    )

    data class RemoveImage(
        val keyFrameId: Int,
        val imageSignature: ImageSignature,
    )

    data class AddImage(
        val imageBase64: String,
        val rect: IntRect,
        val imageSignature: ImageSignature?,
    )

    sealed class ExportFormat {
        data object Png : ExportFormat()
        data class Jpeg(val quality: Float) : ExportFormat()
        data class Webp(val quality: Int) : ExportFormat()
    }
}

