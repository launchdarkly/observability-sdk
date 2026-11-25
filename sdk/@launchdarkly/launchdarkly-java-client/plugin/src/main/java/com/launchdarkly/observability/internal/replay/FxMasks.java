package com.launchdarkly.observability.internal.replay;

import java.awt.Rectangle;
import java.util.List;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;

/**
 * Finds what to mask in a JavaFX scene: a walk of its nodes on the JavaFX application thread, in
 * the same task that snapshots them. A masked node's bounds in the scene are covered, cut by the
 * clips of its ancestors, which is how a scroll pane hides what is scrolled away. A rotated or
 * scaled node is covered by the box around it.
 * <p>
 * Loaded only when JavaFX is present.
 */
final class FxMasks {
  static final String MASK_STYLE = "ld-mask";
  static final String UNMASK_STYLE = "ld-unmask";
  // Classes in JavaFX modules the plugin does not compile against, told by name.
  static final String WEB_VIEW = "javafx.scene.web.WebView"; // a snapshot shows its page
  private static final String[] OPAQUE = {"javafx.scene.media.MediaView", "javafx.embed.swing.SwingNode"};
  private static final String HTML_EDITOR = "javafx.scene.web.HTMLEditor";

  private final MaskRules rules;

  FxMasks(MaskRules rules) {
    this.rules = rules;
  }

  /** Adds the masks of the scene under {@code root}, drawn at the offset, in frame pixels. */
  void collect(Parent root, double sceneWidth, double sceneHeight, int offsetX, int offsetY, double scale,
      List<Rectangle> out) {
    walk(root, new BoundingBox(0, 0, sceneWidth, sceneHeight), false, offsetX, offsetY, scale, out);
  }

  private void walk(Node node, Bounds clip, boolean unmaskedAbove, int offsetX, int offsetY, double scale,
      List<Rectangle> out) {
    // A node this faint shows nothing worth masking, as on Android; a fully transparent one never does.
    if (!node.isVisible() || node.getOpacity() <= 0 || node.getOpacity() < rules.minimumAlpha) {
      return;
    }
    if (node.getClip() != null) {
      clip = intersect(clip, node.localToScene(node.getClip().getBoundsInLocal()));
    }
    Bounds bounds = intersect(clip, node.localToScene(node.getBoundsInLocal()));
    if (bounds.isEmpty()) {
      return;
    }
    Boolean mark = rules.markOf(node, node.getProperties().get(MaskRules.PROPERTY), node.getId());
    if (mark == null && node.getStyleClass().contains(MASK_STYLE)) {
      mark = Boolean.TRUE;
    } else if (mark == null && node.getStyleClass().contains(UNMASK_STYLE)) {
      mark = Boolean.FALSE;
    }
    boolean unmaskedHere = unmaskedAbove || Boolean.FALSE.equals(mark);
    MaskRules.Kind kind = kind(node);
    if (kind == MaskRules.Kind.SECRET || Boolean.TRUE.equals(mark)
        || (!unmaskedHere && (rules.masks(kind) || (rules.maskSensitive && sensitive(node))))) {
      out.add(frameRect(bounds, offsetX, offsetY, scale));
      return;
    }
    if (node instanceof Parent) {
      for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
        walk(child, clip, unmaskedHere, offsetX, offsetY, scale, out);
      }
    }
  }

  static Bounds intersect(Bounds a, Bounds b) {
    double minX = Math.max(a.getMinX(), b.getMinX());
    double minY = Math.max(a.getMinY(), b.getMinY());
    double maxX = Math.min(a.getMaxX(), b.getMaxX());
    double maxY = Math.min(a.getMaxY(), b.getMaxY());
    return new BoundingBox(minX, minY, Math.max(-1, maxX - minX), Math.max(-1, maxY - minY));
  }

  private static Rectangle frameRect(Bounds b, int offsetX, int offsetY, double scale) {
    int left = (int) Math.floor((b.getMinX() + offsetX) * scale) - SwingMasks.MARGIN;
    int top = (int) Math.floor((b.getMinY() + offsetY) * scale) - SwingMasks.MARGIN;
    int right = (int) Math.ceil((b.getMaxX() + offsetX) * scale) + SwingMasks.MARGIN;
    int bottom = (int) Math.ceil((b.getMaxY() + offsetY) * scale) + SwingMasks.MARGIN;
    return new Rectangle(left, top, right - left, bottom - top);
  }

  /** How the rules mask a node of this kind. */
  static MaskRules.Kind kind(Node node) {
    if (node instanceof PasswordField) {
      return MaskRules.Kind.SECRET;
    }
    if (node instanceof TextInputControl || SwingMasks.isA(node.getClass(), HTML_EDITOR)) {
      return MaskRules.Kind.INPUT;
    }
    if (node instanceof ListView || node instanceof TableView || node instanceof TreeView
        || node instanceof TreeTableView || node instanceof ComboBoxBase || node instanceof ChoiceBox
        || node instanceof Spinner) {
      return MaskRules.Kind.TEXT;
    }
    if (node instanceof Labeled) {
      String text = ((Labeled) node).getText();
      return text != null && !text.trim().isEmpty() ? MaskRules.Kind.TEXT : MaskRules.Kind.NONE;
    }
    if (node instanceof Text) { // a Text's text is never null: setting null sets ""
      return ((Text) node).getText().trim().isEmpty() ? MaskRules.Kind.NONE : MaskRules.Kind.TEXT;
    }
    if (node instanceof ImageView) {
      return MaskRules.Kind.IMAGE;
    }
    if (SwingMasks.isA(node.getClass(), WEB_VIEW)) {
      return MaskRules.Kind.WEB;
    }
    for (String opaque : OPAQUE) {
      if (SwingMasks.isA(node.getClass(), opaque)) {
        return MaskRules.Kind.OPAQUE;
      }
    }
    return MaskRules.Kind.NONE;
  }

  /**
   * Whether the node's text, prompt text, tooltip or accessible text has a sensitive keyword, as
   * Android checks a view's text, hint and content description.
   */
  static boolean sensitive(Node node) {
    String text = null;
    String hint = null;
    if (node instanceof TextInputControl) {
      text = ((TextInputControl) node).getText(); // never a password field's: that is SECRET, masked first
      hint = ((TextInputControl) node).getPromptText();
    } else if (node instanceof Labeled) {
      text = ((Labeled) node).getText();
    } else if (node instanceof Text) {
      text = ((Text) node).getText();
    }
    Tooltip tooltip = node instanceof Control ? ((Control) node).getTooltip() : null;
    return MaskRules.sensitive(text, hint, tooltip != null ? tooltip.getText() : null, node.getAccessibleText());
  }
}
