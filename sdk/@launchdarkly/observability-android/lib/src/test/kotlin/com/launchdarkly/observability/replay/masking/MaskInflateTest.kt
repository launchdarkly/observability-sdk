package com.launchdarkly.observability.replay.masking

import android.graphics.RectF
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** Tests for [inflate], which grows a mask to cover a distortion of the pixels beneath it. */
class MaskInflateTest {
    /**
     * Coordinates are assigned rather than passed to the constructor: `RectF`'s constructor is a
     * stub in unit tests and leaves the fields untouched.
     */
    private fun rectOf(left: Float, top: Float, right: Float, bottom: Float) = RectF().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    @Test
    fun `grows the rect on every side`() {
        val mask = Mask(rectOf(10f, 20f, 30f, 40f), viewId = 1)

        mask.inflate(dx = 2f, dy = 3f)

        assertEquals(8f, mask.rect.left)
        assertEquals(17f, mask.rect.top)
        assertEquals(32f, mask.rect.right)
        assertEquals(43f, mask.rect.bottom)
    }

    @Test
    fun `pushes a transformed mask's corners away from its center`() {
        val corners = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val mask = Mask(rectOf(0f, 0f, 10f, 10f), viewId = 1, points = corners)

        mask.inflate(dx = 2f, dy = 3f)

        assertArrayEquals(
            floatArrayOf(-2f, -3f, 12f, -3f, 12f, 13f, -2f, 13f),
            mask.points,
            0.001f
        )
    }

    @Test
    fun `keeps a rotated mask's shape while enlarging it`() {
        val corners = floatArrayOf(5f, 0f, 10f, 5f, 5f, 10f, 0f, 5f)
        val mask = Mask(rectOf(0f, 0f, 10f, 10f), viewId = 1, points = corners)

        mask.inflate(dx = 2f, dy = 2f)

        // Every corner ends up further from the center (5, 5) than the 5px it started at.
        for (i in corners.indices step 2) {
            val dx = corners[i] - 5f
            val dy = corners[i + 1] - 5f
            assertEquals(7f, maxOf(abs(dx), abs(dy)), 0.001f)
        }
    }
}
