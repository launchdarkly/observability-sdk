package com.launchdarkly.observability.internal.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.internal.ComposeFixture
import com.launchdarkly.observability.internal.ComposeFixture.Canvas
import com.launchdarkly.observability.internal.ComposeFixture.at
import com.launchdarkly.observability.internal.ComposeFixture.onEdt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage

/** Replay's capture of a window with Compose for Desktop in it. */
class ComposeCaptureTest {
  private var shown: ComposeFixture.Shown? = null

  @After
  fun tearDown() {
    shown?.close()
  }

  @Test
  fun theFrameHasWhatComposeDrewMasked() {
    val on = ComposeFixture.panel(10, 20, 300, 200) {
      Canvas {
        Box(Modifier.at(0, 0, 50, 50).background(Color.Red))
        BasicTextField("hunter2", {}, Modifier.at(100, 0, 100, 30).background(Color.Blue),
          visualTransformation = PasswordVisualTransformation())
      }
    }.also { shown = it }
    val raw = onEdt {
      SwingCapture(1.0, SwingMasks(MaskRules.defaults()), LDLogger.none(), { true }, { on.frame }).paint()
    }
    assertEquals("Compose's red, which painting offscreen leaves blank", 0xff0000, rgb(raw.image, 35, 45))
    assertEquals("the password, masked", CaptureLoop.MASK_COLOR.rgb and 0xffffff, rgb(raw.image, 150, 35))
  }

  @Test
  fun aLayerThatNeverDrewIsLeftAsPainted() {
    val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    onEdt {
      val layer = org.jetbrains.skiko.SkiaLayer()
      layer.setSize(20, 20)
      SwingCapture(1.0, SwingMasks(MaskRules.defaults()), LDLogger.none(), { true }, { null }).drawCompose(g, layer)
    }
    g.dispose()
    assertEquals(0, rgb(image, 10, 10))
  }

  @Test
  fun onlyWhatShowsIsLookedInto() {
    val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    onEdt {
      val holder = javax.swing.JPanel(null)
      holder.setSize(20, 20)
      val hidden = javax.swing.JPanel()
      hidden.isVisible = false
      hidden.setBounds(0, 0, 10, 10)
      val empty = javax.swing.JPanel()
      empty.setBounds(0, 0, 0, 10)
      val flat = javax.swing.JPanel()
      flat.setBounds(0, 0, 10, 0)
      holder.add(hidden)
      holder.add(empty)
      holder.add(flat)
      holder.add(java.awt.Canvas().also { it.setBounds(0, 0, 10, 10) })
      SwingCapture(1.0, SwingMasks(MaskRules.defaults()), LDLogger.none(), { true }, { null }).drawCompose(g, holder)
    }
    g.dispose()
    assertEquals(0, rgb(image, 5, 5))
  }

  private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xffffff
}
