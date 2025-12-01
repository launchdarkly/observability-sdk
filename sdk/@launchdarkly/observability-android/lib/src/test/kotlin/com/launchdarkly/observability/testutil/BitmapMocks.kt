package com.launchdarkly.observability.testutil

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk

/**
 * Creates a MockK-based fake Android Bitmap that returns the provided width/height
 * and serves pixels from the given [pixels] array via getPixels.
 *
 * The [pixels] array must be of size width * height, in row-major order (left-to-right, top-to-bottom).
 */
fun mockBitmap(imageWidth: Int, imageHeight: Int, pixels: IntArray): Bitmap {
    require(imageWidth > 0 && imageHeight > 0) { "imageWidth and imageHeight must be positive." }
    require(pixels.size == imageWidth * imageHeight) {
        "pixels size ${pixels.size} must equal imageWidth*imageHeight=${imageWidth * imageHeight}"
    }

    val bmp = mockk<Bitmap>()

    every { bmp.width } returns imageWidth
    every { bmp.height } returns imageHeight
    every { bmp.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
        val dest = invocation.args[0] as IntArray
        val offset = invocation.args[1] as Int
        val stride = invocation.args[2] as Int
        val x = invocation.args[3] as Int
        val y = invocation.args[4] as Int
        val w = invocation.args[5] as Int
        val h = invocation.args[6] as Int

        for (row in 0 until h) {
            val srcRowStart = (y + row) * imageWidth + x
            val dstRowStart = offset + row * stride
            for (col in 0 until w) {
                dest[dstRowStart + col] = pixels[srcRowStart + col]
            }
        }
        Unit
    }

    return bmp
}

/**
 * Returns a copy of [basePixels] with a filled rectangle overlay applied.
 *
 * The rectangle is defined by [left], [top], [right], [bottom] in pixel coordinates and is
 * clamped to the image bounds defined by [imageWidth] and [imageHeight].
 */
fun withOverlayRect(
    basePixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    color: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
): IntArray {
    val out = basePixels.clone()
    val clampedLeft = left.coerceIn(0, imageWidth)
    val clampedTop = top.coerceIn(0, imageHeight)
    val clampedRight = right.coerceIn(0, imageWidth)
    val clampedBottom = bottom.coerceIn(0, imageHeight)
    for (y in clampedTop until clampedBottom) {
        val rowStart = y * imageWidth
        for (x in clampedLeft until clampedRight) {
            out[rowStart + x] = color
        }
    }
    return out
}

/**
 * Convenience overload to create a solid-color mock Bitmap.
 */
fun mockBitmap(imageWidth: Int, imageHeight: Int, color: Int): Bitmap {
    val pixels = IntArray(imageWidth * imageHeight) { color }
    return mockBitmap(imageWidth, imageHeight, pixels)
}


