package com.launchdarkly.observability.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ldId
import com.launchdarkly.observability.internal.ComposeFixture.Canvas
import com.launchdarkly.observability.internal.ComposeFixture.at
import com.launchdarkly.observability.internal.ComposeFixture.onEdt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import javax.swing.JButton

/** What a click in Compose for Desktop is reported as. */
class ComposeClickTest {
  private val shown = mutableListOf<ComposeFixture.Shown>()
  private val instrumentations = mutableListOf<DesktopInstrumentation>()
  private val menu = mutableStateOf(false)

  @After
  fun tearDown() {
    instrumentations.forEach { it.stop() }
    shown.forEach { it.close() }
  }

  private fun form(): ComposeFixture.Shown = ComposeFixture.panel(0, 0, 400, 300) {
    Canvas {
      Button({}, Modifier.at(0, 0, 120, 40).ldId("checkout.pay").testTag("pay")) { Text("Pay now") }
      Button({}, Modifier.at(0, 50, 120, 40).testTag("cancel")) { Text("Cancel") }
      BasicText("Total", Modifier.at(200, 0, 80, 20).testTag("total"))
      BasicTextField("hunter2", {}, Modifier.at(200, 50, 100, 30), visualTransformation = PasswordVisualTransformation())
      Box(Modifier.at(200, 120, 150, 60).ldId("outer")) {
        Button({}, Modifier.at(0, 0, 100, 40).ldId("")) { Text("Inner") }
      }
      Box(Modifier.at(0, 200, 120, 40)) {
        DropdownMenu(menu.value, { menu.value = false }) { DropdownMenuItem({}) { Text("Sign out") } }
      }
    }
  }.also { shown.add(it) }

  private fun instrumentation(recordText: Boolean, signals: Recorded): DesktopInstrumentation =
    DesktopInstrumentation(signals, false, true, recordText, LDLogger.none(), Executors.newSingleThreadScheduledExecutor(),
      { true }, { null }, DesktopInstrumentation.TOOLKIT).also { instrumentations.add(it) }

  /** A click at this point of the layer, which AWT reports on skiko's canvas in it. */
  private fun click(instrumentation: DesktopInstrumentation, on: ComposeFixture.Shown, x: Int, y: Int) {
    onEdt {
      val canvas = (on.layer as org.jetbrains.skiko.SkiaLayer).canvas
      instrumentation.eventDispatched(MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 1, 0, x, y, x, y, 1, false,
        MouseEvent.BUTTON1))
    }
  }

  @Test
  fun aClickNamesTheButtonNotTheTextInIt() {
    val on = form()
    val recorded = Recorded()
    val instrumentation = instrumentation(true, recorded)
    click(instrumentation, on, 60, 20)
    val pay = recorded.clicks[0]
    assertEquals("Button", pay.tag)
    assertEquals("Button", pay.classname)
    assertEquals("ldId first, as on Android", "checkout.pay", pay.id)
    assertEquals("Pay now", pay.text)
    assertTrue(pay.xpath, pay.xpath.contains("/ComposePanel/SkiaLayer/") && pay.xpath.endsWith("/Button#pay"))

    click(instrumentation, on, 60, 70)
    assertEquals("then the test tag", "cancel", recorded.clicks[1].id)

    click(instrumentation, on, 240, 10)
    val total = recorded.clicks[2]
    assertEquals("nothing clickable: the deepest node", "Composable", total.tag)
    assertEquals("total", total.id)
    assertEquals("Total", total.text)

    click(instrumentation, on, 250, 65)
    assertNull("never a password's text", recorded.clicks[3].text)

    click(instrumentation, on, 390, 290)
    assertEquals("no node there: the root", "Composable", recorded.clicks[4].tag)

    click(instrumentation, on, 240, 140)
    assertEquals("an empty ldId is none", "outer", recorded.clicks[5].id)

    click(instrumentation, on, -5, -5)
    assertTrue("off the layer: the canvas", recorded.clicks[6].classname.startsWith("org.jetbrains.skiko.SkiaLayer"))
  }

  @Test
  fun aClickInAPopupNamesWhatIsInIt() {
    val on = form()
    onEdt { menu.value = true }
    on.awaitDrawn()
    Thread.sleep(500)
    val item = onEdt {
      val roots = ComposeSupport.roots(on.layer)
      assertEquals("the menu's, and the content's", 2, roots.size)
      fun find(node: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? =
        if (ComposeSupport.text(node.config) == "Sign out") node else node.children.firstNotNullOfOrNull { find(it) }
      ComposeSupport.bounds(roots.firstNotNullOf { find(it) }, ComposeSupport.scale(on.layer))
    }
    val recorded = Recorded()
    click(instrumentation(true, recorded), on, item.centerX.toInt(), item.centerY.toInt())
    assertEquals("Sign out", recorded.clicks[0].text)
  }

  @Test
  fun withoutRecordTextThereIsNoText() {
    val on = form()
    val recorded = Recorded()
    click(instrumentation(false, recorded), on, 60, 20)
    assertEquals("Button", recorded.clicks[0].tag)
    assertNull(recorded.clicks[0].text)
  }

  @Test
  fun aClickTheLayerCannotNameReportsTheCanvas() {
    val recorded = Recorded()
    val instrumentation = instrumentation(true, recorded)
    onEdt {
      val layer = org.jetbrains.skiko.SkiaLayer() // no Compose on it, and never shown
      instrumentation.eventDispatched(MouseEvent(layer.canvas, MouseEvent.MOUSE_CLICKED, 1, 0, 5, 5, 5, 5, 1, false,
        MouseEvent.BUTTON1))
      instrumentation.eventDispatched(MouseEvent(layer, MouseEvent.MOUSE_CLICKED, 2, 0, 5, 5, 5, 5, 1, false,
        MouseEvent.BUTTON1))
      instrumentation.eventDispatched(MouseEvent(JButton("Plain"), MouseEvent.MOUSE_CLICKED, 3, 0, 5, 5, 5, 5, 1,
        false, MouseEvent.BUTTON1))
    }
    assertTrue(recorded.clicks[0].classname, recorded.clicks[0].classname.startsWith("org.jetbrains.skiko.SkiaLayer"))
    assertEquals("org.jetbrains.skiko.SkiaLayer", recorded.clicks[1].classname)
    assertEquals("JButton", recorded.clicks[2].tag)
  }

  private class Recorded : ToolkitInstrumentation.Signals {
    val clicks = mutableListOf<Click>()
    override fun foreground() {}
    override fun background() {}
    override fun startedInBackground() {}
    override fun screenShown(name: String?, screenClass: String?, screenId: String?) {}
    override fun click(click: Click) {
      clicks.add(click)
    }
  }

}
