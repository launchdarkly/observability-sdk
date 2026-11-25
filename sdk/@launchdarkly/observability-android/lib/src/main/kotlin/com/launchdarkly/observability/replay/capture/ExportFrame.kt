package com.launchdarkly.observability.replay.capture

/**
 * Represents an exported frame for replay instrumentation.
 */
data class ExportFrame(
    val keyFrameId: Int,
    val addImages: List<AddImage>,
    val removeImages: List<RemoveImage>?,
    val originalSize: IntSize,
    val scale: Double,
    val timestamp: Long,
    val orientation: Int,
    val isKeyframe: Boolean,
    val imageSignature: ImageSignature?,
    val session: String
){
    companion object {
        val DEFAULT_EXPORT_FORMAT: ExportFormat = ExportFormat.Jpeg(quality = 0.3f)
    }

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
                imageBase64 = imageBase64.toByteArray(Charsets.US_ASCII),
                rect = IntRect(left = 0, top = 0, width = origWidth, height = origHeight),
                imageSignature = null
            )
        ),
        removeImages = null,
        originalSize = IntSize(width = origWidth, height = origHeight),
        scale = 1.0,
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

    /** [imageBase64] is the encoded image's base64, as the ASCII bytes the encoder produced. */
    data class AddImage(
        val imageBase64: ByteArray,
        val rect: IntRect,
        val imageSignature: ImageSignature?,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || other is AddImage && imageBase64.contentEquals(other.imageBase64) &&
                rect == other.rect && imageSignature == other.imageSignature

        override fun hashCode(): Int =
            31 * (31 * imageBase64.contentHashCode() + rect.hashCode()) + (imageSignature?.hashCode() ?: 0)
    }

    sealed class ExportFormat {
        data object Png : ExportFormat()
        data class Jpeg(val quality: Float) : ExportFormat()
        data class Webp(val quality: Int) : ExportFormat()
    }
}

