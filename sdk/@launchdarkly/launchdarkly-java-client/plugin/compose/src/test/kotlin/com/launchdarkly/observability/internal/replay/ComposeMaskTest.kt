package com.launchdarkly.observability.internal.replay

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.launchdarkly.observability.api.ldMask
import com.launchdarkly.observability.api.ldUnmask
import com.launchdarkly.observability.internal.ComposeFixture
import com.launchdarkly.observability.internal.ComposeFixture.Canvas
import com.launchdarkly.observability.internal.ComposeFixture.at
import com.launchdarkly.observability.internal.ComposeFixture.onEdt
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import javax.swing.JPanel

/** What replay masks on a Compose for Desktop layer. */
class ComposeMaskTest {
  private val shown = mutableListOf<ComposeFixture.Shown>()

  @After
  fun tearDown() {
    shown.forEach { it.close() }
  }

  private fun panel(content: @Composable () -> Unit): ComposeFixture.Shown =
    ComposeFixture.panel(10, 20, 400, 300) { Canvas(content) }.also { shown.add(it) }

  private fun masks(rules: MaskRules, on: ComposeFixture.Shown): List<Rectangle> = onEdt {
    val out = mutableListOf<Rectangle>()
    SwingMasks(rules).collect(on.frame.rootPane, 0, 0, 1.0, out)
    out
  }

  @Composable
  private fun form() {
    BasicText("Name", Modifier.at(0, 0, 80, 20))
    BasicTextField("jane@example.com", {}, Modifier.at(100, 0, 100, 30))
    BasicTextField("hunter2", {}, Modifier.at(100, 40, 100, 30), visualTransformation = PasswordVisualTransformation())
    Image(ColorPainter(Color.Red), "logo", Modifier.at(0, 40, 30, 30))
  }

  @Test
  fun theDefaultMasksInputsAndPasswordsWhereTheyAre() {
    val on = panel { form() }
    assertThat(masks(MaskRules.defaults(), on), contains(covered(110, 20, 100, 30), covered(110, 60, 100, 30)))
    assertThat("strict masks the text too", masks(MaskRules.strict(), on),
      contains(covered(10, 20, 80, 20), covered(110, 20, 100, 30), covered(110, 60, 100, 30)))
    assertThat("and images when asked", masks(rules(false, false, true), on),
      contains(covered(110, 60, 100, 30), covered(10, 60, 30, 30)))
    assertThat("a password whatever the rules", masks(rules(false, false, false), on),
      contains(covered(110, 60, 100, 30)))
  }

  @Test
  fun theSameMasksWalkAgain() {
    val on = panel { form() }
    val masks = SwingMasks(MaskRules.defaults())
    repeat(2) {
      val out = onEdt { mutableListOf<Rectangle>().also { masks.collect(on.frame.rootPane, 0, 0, 1.0, it) } }
      assertThat(out, contains(covered(110, 20, 100, 30), covered(110, 60, 100, 30)))
    }
  }

  @Test
  fun ldMaskCoversAComposableAndAllInIt() {
    val on = panel {
      Box(Modifier.at(0, 0, 200, 100).ldMask()) {
        BasicText("Shown elsewhere", Modifier.at(10, 10, 100, 20))
      }
      BasicText("Visible", Modifier.at(0, 150, 80, 20))
    }
    assertThat(masks(MaskRules.defaults(), on), contains(covered(10, 20, 200, 100)))
  }

  @Test
  fun ldUnmaskShowsWhatTheRulesMaskButNeverAPassword() {
    val on = panel {
      Box(Modifier.at(0, 0, 300, 100).ldUnmask()) {
        BasicText("Name", Modifier.at(0, 0, 80, 20))
        BasicTextField("jane@example.com", {}, Modifier.at(100, 0, 100, 30))
        BasicTextField("hunter2", {}, Modifier.at(100, 40, 100, 30),
          visualTransformation = PasswordVisualTransformation())
        BasicText("Masked again", Modifier.at(0, 70, 80, 20).ldMask())
      }
    }
    assertThat(masks(MaskRules.strict(), on), contains(covered(110, 60, 100, 30), covered(10, 90, 80, 20)))
  }

  @Test
  fun aTestTagIsANameForTheRules() {
    val on = panel {
      BasicText("Account", Modifier.at(0, 0, 80, 20).testTag("account"))
      BasicTextField("jane@example.com", {}, Modifier.at(100, 0, 100, 30).testTag("email"))
    }
    val rules = MaskRules(true, false, false, setOf("account"), setOf("email"), emptySet(), emptySet())
    assertThat(masks(rules, on), contains(covered(10, 20, 80, 20)))
  }

  @Test
  fun sensitiveKeywordsMaskTextFieldsAndDescriptions() {
    val on = panel {
      BasicText("Your password is", Modifier.at(0, 0, 120, 20))
      BasicText("Hello", Modifier.at(0, 30, 80, 20))
      BasicTextField("my SSN", {}, Modifier.at(0, 60, 100, 30))
      Image(ColorPainter(Color.Blue), "credit card", Modifier.at(0, 100, 30, 30))
    }
    val rules = MaskRules(false, false, false, false, true, 0.02, emptySet(), emptySet(), emptySet(), emptySet())
    assertThat(masks(rules, on), contains(covered(10, 20, 120, 20), covered(10, 80, 100, 30), covered(10, 120, 30, 30)))
  }

  @Test
  fun whatIsOutOfSightIsNotMasked() {
    val on = panel {
      BasicTextField("hunter2", {}, Modifier.at(500, 400, 100, 30), visualTransformation = PasswordVisualTransformation())
    }
    assertThat(masks(MaskRules.strict(), on), empty())
  }

  @Test
  fun aComposableNotPlacedIsNotMasked() {
    val on = panel {
      Layout({ BasicTextField("hunter2", {}, Modifier.at(0, 0, 100, 30), visualTransformation = PasswordVisualTransformation()) }) { measurables, constraints ->
        measurables.forEach { it.measure(constraints) }
        layout(200, 200) {} // measured, never placed
      }
    }
    assertThat(masks(MaskRules.strict(), on), empty())
  }

  @Test
  fun aPopupIsMaskedToo() {
    val expanded = mutableStateOf(false)
    val on = panel {
      Box(Modifier.at(0, 0, 100, 30)) {
        DropdownMenu(expanded.value, { expanded.value = false }) {
          DropdownMenuItem({}) { BasicText("Account 4242") }
        }
      }
    }
    assertThat(masks(MaskRules.strict(), on), empty())
    onEdt { expanded.value = true }
    on.awaitDrawn()
    Thread.sleep(500)
    val masks = masks(MaskRules.strict(), on)
    assertThat(masks, hasSize(1))
    assertTrue("inside the panel", Rectangle(9, 19, 402, 302).contains(masks[0]))
  }

  @Test
  fun theLayerMarkedOrItsAncestorsComeFirst() {
    val on = panel { form() }
    onEdt { on.panel.putClientProperty(MaskRules.PROPERTY, false) }
    assertThat("unmasked, all but the password", masks(MaskRules.strict(), on), contains(covered(110, 60, 100, 30)))
    onEdt { on.panel.putClientProperty(MaskRules.PROPERTY, true) }
    assertThat("masked, the whole panel", masks(MaskRules.defaults(), on), contains(covered(10, 20, 400, 300)))
  }

  @Test
  fun aLayerThatCannotBeWalkedIsCoveredWhole() {
    // The walk fails at the layer, so its canvas is masked as any canvas is.
    val failing = onEdt {
      val holder = JPanel(null)
      holder.setSize(600, 600)
      val wrapped = BrokenLayer()
      wrapped.setBounds(50, 60, 200, 100)
      wrapped.canvas.setBounds(0, 0, 200, 100)
      holder.add(wrapped)
      val out = mutableListOf<Rectangle>()
      SwingMasks(MaskRules.defaults()).collect(holder, 0, 0, 1.0, out)
      out
    }
    assertThat(failing, contains(covered(50, 60, 200, 100)))
  }

  @Test
  fun aNodeWithADescriptionOnlyIsNotText() {
    val on = panel { Box(Modifier.at(0, 0, 50, 50).semantics { contentDescription = "decoration" }) }
    assertThat(masks(MaskRules.strict(), on), empty())
    assertEquals(MaskRules.Kind.NONE, onEdt {
      com.launchdarkly.observability.internal.ComposeSupport.roots(on.layer)[0].children[0].config.let { ComposeMasks.kind(it) }
    })
  }

  /** A layer whose accessible context, which leads to Compose's semantics, cannot be had. */
  private class BrokenLayer : org.jetbrains.skiko.SkiaLayer() {
    override fun getAccessibleContext(): javax.accessibility.AccessibleContext = throw IllegalStateException("broken")
  }

  companion object {
    fun covered(x: Int, y: Int, w: Int, h: Int): Rectangle = Rectangle(x - 1, y - 1, w + 2, h + 2)

    fun rules(inputs: Boolean, text: Boolean, images: Boolean): MaskRules =
      MaskRules(inputs, text, images, emptySet(), emptySet(), emptySet(), emptySet())
  }
}
