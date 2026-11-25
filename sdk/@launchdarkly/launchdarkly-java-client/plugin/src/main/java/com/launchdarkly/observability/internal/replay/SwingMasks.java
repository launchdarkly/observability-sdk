package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.ComposeSupport;
import com.launchdarkly.observability.internal.PivotSupport;
import com.launchdarkly.observability.internal.ToolkitSupport;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Container;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.TextComponent;
import java.util.ArrayList;
import java.util.List;

import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;

/**
 * Finds what to mask in a Swing or AWT window: a walk of its components on the event dispatch
 * thread, in the same task that paints them. A masked component's whole area is covered, cut to
 * what its ancestors show, so the part scrolled out of sight costs nothing.
 * <p>
 * Text that a component paints itself, such as a custom renderer's {@code drawString} or a titled
 * border's title, is not seen by the walk; the application masks such a component with
 * {@link MaskRules#mark} or its client property.
 */
final class SwingMasks implements SwingCapture.Masks {
  /** Masks grow by this, in frame pixels, to cover anti-aliased edges. */
  static final int MARGIN = 1;
  private static final String JFX_PANEL = "javafx.embed.swing.JFXPanel";
  /**
   * Apache Pivot's AWT host. It paints offscreen like any component, and what it paints are Pivot's
   * own components, which {@link PivotMasks} walks. When it cannot, the host is covered whole.
   */
  static final String PIVOT_HOST = PivotSupport.HOST;
  /**
   * The layer Compose for Desktop draws on, which {@link SwingCapture} fills with skiko's last frame.
   * What is on it is Compose's, which {@link ComposeMasks} walks. When it cannot, the canvas inside
   * the layer is covered whole, as any canvas is.
   */
  static final String COMPOSE_LAYER = ComposeSupport.LAYER;
  /** JxBrowser's lightweight Swing view, which paints offscreen like any component. */
  static final String JXBROWSER_VIEW = "com.teamdev.jxbrowser.view.swing.BrowserView";
  /** The placeholder text FlatLaf and other look and feels read from a text field, its hint. */
  static final String PLACEHOLDER = "JTextField.placeholderText";

  private final MaskRules rules;
  private PivotMasks pivotMasks; // made on first use, on the event dispatch thread
  private ComposeMasks composeMasks; // likewise

  SwingMasks(MaskRules rules) {
    this.rules = rules;
  }

  @Override
  public void collect(Component content, int offsetX, int offsetY, double scale, List<Rectangle> out) {
    Rectangle clip = new Rectangle(0, 0, content.getWidth(), content.getHeight());
    walk(content, 0, 0, clip, false, offsetX, offsetY, scale, out);
  }

  /**
   * @param x the component's position in the content
   * @param clip what of the content the component's ancestors show
   * @param unmaskedAbove whether an ancestor is marked to unmask. No ancestor is marked to mask: a
   *     masked component is covered whole, and its children are not walked.
   */
  private void walk(Component c, int x, int y, Rectangle clip, boolean unmaskedAbove, int offsetX, int offsetY,
      double scale, List<Rectangle> out) {
    Rectangle visible = clip.intersection(new Rectangle(x, y, c.getWidth(), c.getHeight()));
    if (visible.isEmpty()) {
      return;
    }
    Boolean mark = rules.markOf(c, c instanceof JComponent ? ((JComponent) c).getClientProperty(MaskRules.PROPERTY)
        : null, c.getName());
    boolean unmaskedHere = unmaskedAbove || Boolean.FALSE.equals(mark);
    if (!Boolean.TRUE.equals(mark) && isA(c.getClass(), PIVOT_HOST)
        && pivot(c, x, y, visible, unmaskedHere, offsetX, offsetY, scale, out)) {
      return;
    }
    if (!Boolean.TRUE.equals(mark) && isA(c.getClass(), COMPOSE_LAYER)
        && compose(c, x, y, visible, unmaskedHere, offsetX, offsetY, scale, out)) {
      return;
    }
    MaskRules.Kind kind = kind(c);
    if (kind == MaskRules.Kind.SECRET || Boolean.TRUE.equals(mark)
        || (!unmaskedHere && (rules.masks(kind) || (rules.maskSensitive && sensitive(c))))) {
      out.add(frameRect(visible, offsetX, offsetY, scale));
      return; // all of it is covered
    }
    if (c instanceof JTabbedPane && rules.maskText && !unmaskedHere) {
      // Tab titles are painted by the tabbed pane itself, not by a component.
      JTabbedPane tabs = (JTabbedPane) c;
      for (int i = 0; i < tabs.getTabCount(); i++) {
        Rectangle tab = tabs.getBoundsAt(i);
        if (tab != null) {
          Rectangle at = visible.intersection(new Rectangle(x + tab.x, y + tab.y, tab.width, tab.height));
          if (!at.isEmpty()) {
            out.add(frameRect(at, offsetX, offsetY, scale));
          }
        }
      }
    }
    if (c instanceof Container) {
      for (Component child : ((Container) c).getComponents()) {
        if (child.isVisible()) {
          walk(child, x + child.getX(), y + child.getY(), visible, unmaskedHere, offsetX, offsetY, scale, out);
        }
      }
    }
  }

  /** Masks what is inside a Pivot host, or says it could not, so the host is covered whole. */
  private boolean pivot(Component host, int x, int y, Rectangle visible, boolean unmasked, int offsetX, int offsetY,
      double scale, List<Rectangle> out) {
    List<Rectangle> inside = new ArrayList<>();
    try {
      if (pivotMasks == null) {
        pivotMasks = new PivotMasks(rules);
      }
      pivotMasks.collect(host, x, y, visible, unmasked, inside);
    } catch (LinkageError | RuntimeException e) {
      return false;
    }
    for (Rectangle r : inside) {
      out.add(frameRect(r, offsetX, offsetY, scale));
    }
    return true;
  }

  /** Masks what is on a Compose layer, or says it could not, so its canvas is covered whole. */
  private boolean compose(Component layer, int x, int y, Rectangle visible, boolean unmasked, int offsetX,
      int offsetY, double scale, List<Rectangle> out) {
    List<Rectangle> inside = new ArrayList<>();
    try {
      if (composeMasks == null) {
        composeMasks = new ComposeMasks(rules);
      }
      composeMasks.collect(layer, x, y, visible, unmasked, inside);
    } catch (LinkageError | RuntimeException e) {
      return false;
    }
    for (Rectangle r : inside) {
      out.add(frameRect(r, offsetX, offsetY, scale));
    }
    return true;
  }

  static Rectangle frameRect(Rectangle r, int offsetX, int offsetY, double scale) {
    int left = (int) Math.floor((r.x + offsetX) * scale) - MARGIN;
    int top = (int) Math.floor((r.y + offsetY) * scale) - MARGIN;
    int right = (int) Math.ceil((r.x + r.width + offsetX) * scale) + MARGIN;
    int bottom = (int) Math.ceil((r.y + r.height + offsetY) * scale) + MARGIN;
    return new Rectangle(left, top, right - left, bottom - top);
  }

  /** How the rules mask a component of this kind. */
  static MaskRules.Kind kind(Component c) {
    if (c instanceof JPasswordField) {
      return MaskRules.Kind.SECRET;
    }
    if (c instanceof JTextComponent || c instanceof TextComponent) {
      return MaskRules.Kind.INPUT;
    }
    if (c instanceof JTable || c instanceof JTableHeader || c instanceof JList || c instanceof JTree
        || c instanceof JComboBox || c instanceof JSpinner || c instanceof JToolTip || c instanceof Label
        || c instanceof Button || c instanceof Checkbox || c instanceof Choice || c instanceof java.awt.List) {
      return MaskRules.Kind.TEXT;
    }
    if (c instanceof AbstractButton) {
      return hasText(((AbstractButton) c).getText()) ? MaskRules.Kind.TEXT : MaskRules.Kind.NONE;
    }
    if (c instanceof JLabel) {
      JLabel label = (JLabel) c;
      if (hasText(label.getText())) {
        return MaskRules.Kind.TEXT;
      }
      return label.getIcon() != null ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
    }
    if (isA(c.getClass(), JXBROWSER_VIEW)) {
      return MaskRules.Kind.WEB;
    }
    if (c instanceof Canvas || isA(c.getClass(), JFX_PANEL) || isA(c.getClass(), PIVOT_HOST)) {
      return MaskRules.Kind.OPAQUE; // a heavyweight browser such as JCEF's is a Canvas too
    }
    return MaskRules.Kind.NONE;
  }

  /**
   * Whether the component's text, placeholder, tooltip or accessible name or description has a
   * sensitive keyword, as Android checks a view's text, hint and content description.
   */
  static boolean sensitive(Component c) {
    String text = null;
    if (c instanceof JTextComponent) {
      text = ((JTextComponent) c).getText(); // never a password field's: that is SECRET, masked first
    } else if (c instanceof TextComponent) {
      text = ((TextComponent) c).getText();
    } else if (c instanceof JLabel) {
      text = ((JLabel) c).getText();
    } else if (c instanceof AbstractButton) {
      text = ((AbstractButton) c).getText();
    } else if (c instanceof Label) {
      text = ((Label) c).getText();
    } else if (c instanceof Button) {
      text = ((Button) c).getLabel();
    }
    String hint = null;
    String tooltip = null;
    if (c instanceof JComponent) {
      JComponent j = (JComponent) c;
      Object placeholder = j.getClientProperty(PLACEHOLDER);
      hint = placeholder instanceof String ? (String) placeholder : null;
      tooltip = j.getToolTipText();
    }
    AccessibleContext accessible = c.getAccessibleContext();
    String name = accessible != null ? accessible.getAccessibleName() : null;
    String description = accessible != null ? accessible.getAccessibleDescription() : null;
    return MaskRules.sensitive(text, hint, tooltip, name, description);
  }

  private static boolean hasText(String text) {
    return text != null && !text.trim().isEmpty();
  }

  /** Whether the class is, or extends, the class of this name, which may not be loaded. */
  static boolean isA(Class<?> type, String name) {
    return ToolkitSupport.isA(type, name);
  }
}
