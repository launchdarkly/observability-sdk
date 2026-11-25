package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.ComposeSupport;

import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsNode;

import java.awt.Component;
import java.awt.Rectangle;
import java.util.List;

/**
 * Finds what to mask on a Compose for Desktop layer: a walk of Compose's semantics trees, which AWT
 * sees only as the layer. The same rules as for Swing, with Compose's kinds of node, which are
 * Android's: a password field, a text field, text and images. A node's mark is its
 * {@link ComposeSupport#MASK_KEY} property, set by {@code Modifier.ldMask()} or
 * {@code Modifier.ldUnmask()}; its test tag is its name for {@link MaskRules}' names.
 * <p>
 * Loaded only for a Compose layer, on the event dispatch thread, which is Compose's.
 */
final class ComposeMasks {
  private final MaskRules rules;

  ComposeMasks(MaskRules rules) {
    this.rules = rules;
  }

  /**
   * Adds the masks on a layer, in the coordinates of the content the layer is in.
   *
   * @param layerX the layer's place in the content
   * @param clip what of the content the layer's ancestors show
   * @param unmasked whether the layer or an ancestor is marked to unmask
   */
  void collect(Component layer, int layerX, int layerY, Rectangle clip, boolean unmasked, List<Rectangle> out) {
    double scale = ComposeSupport.scale(layer);
    for (SemanticsNode root : ComposeSupport.roots(layer)) {
      walk(root, scale, layerX, layerY, clip, unmasked, out);
    }
  }

  private void walk(SemanticsNode node, double scale, int layerX, int layerY, Rectangle clip, boolean unmaskedAbove,
      List<Rectangle> out) {
    if (!ComposeSupport.placed(node)) {
      return;
    }
    Rectangle bounds = ComposeSupport.bounds(node, scale);
    bounds.translate(layerX, layerY);
    Rectangle visible = clip.intersection(bounds);
    SemanticsConfiguration config = node.getConfig();
    Boolean mark = rules.markOf(node, ComposeSupport.property(config, ComposeSupport.MASK_KEY),
        ComposeSupport.testTag(config));
    boolean unmaskedHere = unmaskedAbove || Boolean.FALSE.equals(mark);
    MaskRules.Kind kind = kind(config);
    if (kind == MaskRules.Kind.SECRET || Boolean.TRUE.equals(mark)
        || (!unmaskedHere && (rules.masks(kind) || (rules.maskSensitive && sensitive(config))))) {
      if (!visible.isEmpty()) {
        out.add(visible);
      }
      return; // all of it is covered
    }
    // A node's bounds in the window are already cut to its ancestors', so the layer's clip is enough.
    for (SemanticsNode child : node.getChildren()) {
      walk(child, scale, layerX, layerY, clip, unmaskedHere, out);
    }
  }

  /** How the rules mask a node of this kind, as Android's plugin decides it. */
  static MaskRules.Kind kind(SemanticsConfiguration config) {
    if (ComposeSupport.isPassword(config)) {
      return MaskRules.Kind.SECRET;
    }
    if (ComposeSupport.isInput(config)) {
      return MaskRules.Kind.INPUT;
    }
    if (ComposeSupport.hasText(config)) {
      return MaskRules.Kind.TEXT;
    }
    return "Image".equals(ComposeSupport.role(config)) ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
  }

  /** Whether the node's text, a text field's text or its content description has a sensitive keyword. */
  static boolean sensitive(SemanticsConfiguration config) {
    return MaskRules.sensitive(ComposeSupport.text(config), ComposeSupport.editableText(config),
        ComposeSupport.contentDescription(config));
  }
}
