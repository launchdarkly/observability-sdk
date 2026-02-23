package com.launchdarkly.observability.replay.capture

/**
 * Represents an exported frame for replay instrumentation.
 */
data class ExportFrame(
    val keyFrameId: Int,
    val addImages: List<AddImage>,
    val removeImages: List<RemoveImage>?,
    val originalSize: FrameSize,
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
                rect = FrameRect(left = 0, top = 0, width = origWidth, height = origHeight),
                tileSignature = null
            )
        ),
        removeImages = null,
        originalSize = FrameSize(width = origWidth, height = origHeight),
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
        val tileSignature: TileSignature,
    )

    data class AddImage(
        val imageBase64: String,
        val rect: FrameRect,
        val tileSignature: TileSignature?,
    )

    data class FrameRect(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    data class FrameSize(
        val width: Int,
        val height: Int,
    )

    data class ImageSignature(
        val value: String,
    )

    sealed class ExportFormat {
        data object Png : ExportFormat()
        data class Jpeg(val quality: Float) : ExportFormat()
        data class Webp(val quality: Int) : ExportFormat()
    }
}

