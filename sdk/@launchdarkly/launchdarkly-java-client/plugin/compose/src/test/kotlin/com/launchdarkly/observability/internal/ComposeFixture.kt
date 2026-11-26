package com.launchdarkly.observability.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import org.junit.Assume
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.util.concurrent.Callable
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Compose for Desktop on screen, for the tests: a ComposePanel in a frame. */
object ComposeFixture {
  /** Runs the task on the event dispatch thread, which is Compose's, and returns what it returns. */
  fun <T> onEdt(task: Callable<T>): T {
    var out: T? = null
    var failure: Throwable? = null
    SwingUtilities.invokeAndWait {
      try {
        out = task.call()
      } catch (e: Throwable) {
        failure = e
      }
    }
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return out as T
  }

  /** A frame, shown, with the panel at this place of its content. Skips the test without a display. */
  fun show(panel: ComposePanel, x: Int, y: Int, width: Int, height: Int): JFrame {
    Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless())
    return onEdt {
      val frame = JFrame("compose")
      frame.contentPane.layout = null
      panel.setBounds(x, y, width, height)
      frame.contentPane.add(panel)
      frame.setSize(x + width + 100, y + height + 100)
      frame.isVisible = true
      frame
    }
  }

  /** A panel showing the content in a frame, its top left corner at this place of the frame's content. */
  fun panel(x: Int, y: Int, width: Int, height: Int, content: @Composable () -> Unit): Shown {
    val panel = onEdt { ComposePanel() }
    onEdt { panel.setContent(content) }
    val frame = show(panel, x, y, width, height)
    val shown = Shown(frame, panel)
    shown.awaitDrawn()
    return shown
  }

  /** A composable at this place, in dp, which on the desktop are the layer's pixels times its density. */
  fun Modifier.at(x: Int, y: Int, width: Int, height: Int): Modifier =
    this.offset(x.dp, y.dp).size(width.dp, height.dp)

  /** Fills the panel, for content placed with [at]. */
  @Composable
  fun Canvas(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
  }

  /** The layer Compose draws on, inside the component. */
  fun layer(c: Component): Component? {
    if (ToolkitSupport.isA(c.javaClass, ComposeSupport.LAYER)) {
      return c
    }
    if (c is Container) {
      for (child in c.components) {
        layer(child)?.let { return it }
      }
    }
    return null
  }

  class Shown(val frame: JFrame, val panel: ComposePanel) {
    val layer: Component get() = layer(panel)!!

    /** Waits until skiko has drawn a frame of the content and Compose has laid it out. */
    fun awaitDrawn() {
      val deadline = System.currentTimeMillis() + 10_000
      while (System.currentTimeMillis() < deadline) {
        val ready = onEdt { layer(panel)?.let { ComposeSupport.screenshot(it) != null && ComposeSupport.roots(it).isNotEmpty() } == true }
        if (ready) {
          Thread.sleep(200) // a frame or two more, so the drawing has settled
          return
        }
        Thread.sleep(50)
      }
      throw AssertionError("Compose never drew the panel")
    }

    fun close() {
      onEdt { frame.dispose() }
    }
  }
}
