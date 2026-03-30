package com.example.androidobservability

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launchdarkly.observability.replay.capture.TileSignatureManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileHashParityInstrumentedTest {

    @Test
    fun nativeSignaturesParity() {
        val width = 191
        val height = 67
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { i ->
            val value = (i * 1103515245 + 12345) and 0x00FFFFFF
            (0xFF shl 24) or value
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val nativePacked = nativeCompute(bitmap)
        assumeTrue(nativePacked != null)

        val manager = TileSignatureManager()
        val tileHeight = expectedDefaultTileHeight(height)
        val nativeSig = manager.compute(bitmap)
        val kotlinSig = manager.compute(bitmap, 64, tileHeight)

        assertNotNull(nativeSig)
        assertNotNull(kotlinSig)
        assertEquals(kotlinSig, nativeSig)
    }

    private fun nativeCompute(bitmap: Bitmap): LongArray? {
        return try {
            val cls = Class.forName("com.launchdarkly.observability.replay.capture.TileHashNative")
            val method = cls.getDeclaredMethod("nativeCompute", Bitmap::class.java)
            runCatching { method.invoke(null, bitmap) as? LongArray }
                .getOrElse {
                    val instance = cls.getDeclaredField("INSTANCE").get(null)
                    method.invoke(instance, bitmap) as? LongArray
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun expectedDefaultTileHeight(height: Int): Int {
        val preferred = 22
        val range = 22..44
        if (height <= 0) return preferred
        if (height % preferred == 0) return preferred

        val maxDistance = maxOf(
            kotlin.math.abs(range.first - preferred),
            kotlin.math.abs(range.last - preferred),
        )
        for (offset in 1..maxDistance) {
            val positive = preferred + offset
            if (positive in range && height % positive == 0) return positive
            val negative = preferred - offset
            if (negative in range && height % negative == 0) return negative
        }
        return preferred
    }
}
