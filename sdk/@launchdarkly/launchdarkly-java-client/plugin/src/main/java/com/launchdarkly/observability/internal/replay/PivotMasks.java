package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.PivotSupport;

import org.apache.pivot.wtk.Button;
import org.apache.pivot.wtk.Component;
import org.apache.pivot.wtk.Container;
import org.apache.pivot.wtk.ImageView;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.ListButton;
import org.apache.pivot.wtk.ListView;
import org.apache.pivot.wtk.Spinner;
import org.apache.pivot.wtk.TableView;
import org.apache.pivot.wtk.TableViewHeader;
import org.apache.pivot.wtk.TextArea;
import org.apache.pivot.wtk.TextInput;
import org.apache.pivot.wtk.TextPane;
import org.apache.pivot.wtk.TreeView;
import org.apache.pivot.wtk.content.ButtonData;

import java.awt.Rectangle;
import java.util.List;

/**
 * Finds what to mask inside an Apache Pivot display: a walk of Pivot's components, which AWT sees
 * only as their host. The same rules as for Swing, with Pivot's kinds of component; a component's
 * mark comes from {@link MaskRules#mark}, its name, its class, or its user data under
 * {@link MaskRules#PROPERTY}.
 * <p>
 * Loaded only for a Pivot host, on the event dispatch thread, which is Pivot's.
 */
final class PivotMasks {
  private final MaskRules rules;

  PivotMasks(MaskRules rules) {
    this.rules = rules;
  }

  /**
   * Adds the masks inside a host, in the coordinates of the content the host is in.
   *
   * @param hostX the host's place in the content
   * @param clip what of the content the host's ancestors show
   * @param unmasked whether the host or an ancestor is marked to unmask
   */
  void collect(java.awt.Component host, int hostX, int hostY, Rectangle clip, boolean unmasked, List<Rectangle> out) {
    walk(PivotSupport.display(host), 0, 0, PivotSupport.scale(host), hostX, hostY, clip, unmasked, out);
  }

  /** @param x the component's place in the display, in Pivot pixels */
  private void walk(Component c, int x, int y, double scale, int hostX, int hostY, Rectangle clip,
      boolean unmaskedAbove, List<Rectangle> out) {
    int left = hostX + (int) Math.floor(x * scale);
    int top = hostY + (int) Math.floor(y * scale);
    int right = hostX + (int) Math.ceil((x + c.getWidth()) * scale);
    int bottom = hostY + (int) Math.ceil((y + c.getHeight()) * scale);
    Rectangle visible = clip.intersection(new Rectangle(left, top, right - left, bottom - top));
    if (visible.isEmpty()) {
      return;
    }
    Boolean mark = rules.markOf(c, c.getUserData().get(MaskRules.PROPERTY), c.getName());
    boolean unmaskedHere = unmaskedAbove || Boolean.FALSE.equals(mark);
    MaskRules.Kind kind = kind(c);
    if (kind == MaskRules.Kind.SECRET || Boolean.TRUE.equals(mark)
        || (!unmaskedHere && (rules.masks(kind) || (rules.maskSensitive && sensitive(c))))) {
      out.add(visible);
      return; // all of it is covered
    }
    if (c instanceof Container) {
      Container container = (Container) c;
      for (int i = 0; i < container.getLength(); i++) {
        Component child = container.get(i);
        if (child.isVisible()) {
          walk(child, x + child.getX(), y + child.getY(), scale, hostX, hostY, visible, unmaskedHere, out);
        }
      }
    }
  }

  /** How the rules mask a Pivot component of this kind. */
  static MaskRules.Kind kind(Component c) {
    if (c instanceof TextInput) {
      return ((TextInput) c).isPassword() ? MaskRules.Kind.SECRET : MaskRules.Kind.INPUT;
    }
    if (c instanceof TextArea || c instanceof TextPane || c instanceof Spinner) {
      return MaskRules.Kind.INPUT;
    }
    if (c instanceof ListButton || c instanceof ListView || c instanceof TableView || c instanceof TableViewHeader
        || c instanceof TreeView) {
      return MaskRules.Kind.TEXT;
    }
    if (c instanceof Button) {
      Object data = ((Button) c).getButtonData();
      if (data == null || data instanceof String) {
        return hasText((String) data) ? MaskRules.Kind.TEXT : MaskRules.Kind.NONE;
      }
      if (!(data instanceof ButtonData)) {
        return MaskRules.Kind.TEXT; // other data is drawn by the button's renderer, often as text
      }
      ButtonData buttonData = (ButtonData) data;
      if (hasText(buttonData.getText())) {
        return MaskRules.Kind.TEXT;
      }
      return buttonData.getIcon() != null ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
    }
    if (c instanceof Label) {
      return hasText(((Label) c).getText()) ? MaskRules.Kind.TEXT : MaskRules.Kind.NONE;
    }
    if (c instanceof ImageView) {
      return MaskRules.Kind.IMAGE;
    }
    return MaskRules.Kind.NONE;
  }

  /** Whether the component's text, prompt or tooltip has a sensitive keyword. */
  static boolean sensitive(Component c) {
    String text = null;
    String hint = null;
    if (c instanceof TextInput) {
      text = ((TextInput) c).getText(); // never a password's: that is SECRET, masked first
      hint = ((TextInput) c).getPrompt();
    } else if (c instanceof TextArea) {
      text = ((TextArea) c).getText();
    } else {
      text = PivotSupport.text(c);
    }
    return MaskRules.sensitive(text, hint, c.getTooltipText());
  }

  private static boolean hasText(String text) {
    return text != null && !text.trim().isEmpty();
  }
}
