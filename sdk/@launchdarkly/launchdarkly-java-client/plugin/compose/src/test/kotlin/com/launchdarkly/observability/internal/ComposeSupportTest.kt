package com.launchdarkly.observability.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import com.launchdarkly.observability.internal.ComposeFixture.Canvas
import com.launchdarkly.observability.internal.ComposeFixture.at
import com.launchdarkly.observability.internal.ComposeFixture.onEdt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleStateSet
import javax.swing.JPanel

/** How the plugin finds Compose's semantics trees from a layer's accessibility tree. */
class ComposeSupportTest {
  private var shown: ComposeFixture.Shown? = null

  @After
  fun tearDown() {
    shown?.close()
  }

  /** A real semantics tree: its root, and a text in it. */
  private fun tree(): Pair<SemanticsNode, SemanticsNode> {
    val on = ComposeFixture.panel(0, 0, 200, 100) {
      Canvas { Box(Modifier.at(0, 0, 100, 50)) { BasicText("Inside", Modifier.at(0, 0, 80, 20)) } }
    }.also { shown = it }
    return onEdt {
      val root = ComposeSupport.roots(on.layer).single()
      var text = root
      while (text.children.isNotEmpty()) text = text.children[0]
      root to text
    }
  }

  @Test
  fun eachTreeOnceFromWhereverItIsFound() {
    val (root, text) = tree()
    val roots = onEdt {
      ComposeSupport.roots(Layer(null, Holder(null), Holder(Holder(NodeAccessible(text))), Nothing(), NodeAccessible(root)))
    }
    assertEquals("one tree, found below and climbed to its root", 1, roots.size)
    assertEquals(root.id, roots[0].id)
  }

  @Test
  fun anAccessibleThatIsNotComposesHasNoNode() {
    assertNull(ComposeSupport.semanticsNode(JPanel()))
    assertSame(null, ComposeSupport.semanticsNode(Nothing()))
  }

  @Test(expected = IllegalStateException::class)
  fun aNodeThatCannotBeHadFailsTheWalk() {
    tree()
    onEdt { ComposeSupport.roots(Layer(Failing())) }
  }

  @Test
  fun labelsAndTextAsAndroidReadsThem() {
    val (_, text) = tree()
    onEdt {
      assertEquals("Inside", ComposeSupport.label(text.config))
      assertNull(ComposeSupport.editableText(text.config))
      assertNull(ComposeSupport.contentDescription(text.config))
    }
  }

  /** A layer whose accessibility tree is these. */
  private class Layer(vararg val children: Accessible?) : JPanel() {
    override fun getAccessibleContext(): AccessibleContext = Context(children.toList())
  }

  private class Holder(vararg val children: Accessible?) : Accessible {
    override fun getAccessibleContext(): AccessibleContext = Context(children.toList())
  }

  /** An accessible object without a context. */
  private class Nothing : Accessible {
    override fun getAccessibleContext(): AccessibleContext? = null
  }

  private class Context(val children: List<Accessible?>) : AccessibleContext() {
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL
    override fun getAccessibleStateSet(): AccessibleStateSet = AccessibleStateSet()
    override fun getAccessibleIndexInParent(): Int = 0
    override fun getAccessibleChildrenCount(): Int = children.size
    override fun getAccessibleChild(i: Int): Accessible? = children[i]
    override fun getLocale(): Locale = Locale.ROOT
  }
}

/** Like Compose's accessible objects, which the plugin asks for their node by name. */
class NodeAccessible(private val node: SemanticsNode) : Accessible {
  fun getSemanticsNode(): SemanticsNode = node
  override fun getAccessibleContext(): AccessibleContext? = null
}

class Failing : Accessible {
  fun getSemanticsNode(): SemanticsNode = throw UnsupportedOperationException("gone")
  override fun getAccessibleContext(): AccessibleContext? = null
}
