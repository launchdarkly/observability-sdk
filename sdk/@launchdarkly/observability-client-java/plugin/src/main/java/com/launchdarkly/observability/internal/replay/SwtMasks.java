package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.SwtSupport;
import com.launchdarkly.observability.internal.ToolkitSupport;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;

import java.awt.Rectangle;
import java.util.List;

/**
 * Finds what to mask in an SWT shell: a walk of its controls on SWT's thread, in the same task that
 * copies the shell. The same rules as for Swing, with SWT's kinds of control. A masked control's
 * whole area is covered, cut to what its ancestors show. A control's mark comes from
 * {@link MaskRules#mark}, its data under {@link MaskRules#PROPERTY}, its name ({@link SwtSupport#NAME})
 * or its class.
 * <p>
 * Text that a control draws itself, such as on a {@code Canvas}, is not seen by the walk; the
 * application masks such a control with a mark.
 */
final class SwtMasks {
  /** Masks grow by this, in frame pixels, to cover anti-aliased edges. */
  static final int MARGIN = SwingMasks.MARGIN;
  /** SWT's browser, which draws a web page. */
  static final String BROWSER = "org.eclipse.swt.browser.Browser";
  /** SWT's OpenGL canvas, which the platform draws and a copy may not see. */
  static final String GL_CANVAS = "org.eclipse.swt.opengl.GLCanvas";

  private final MaskRules rules;

  SwtMasks(MaskRules rules) {
    this.rules = rules;
  }

  /**
   * Adds the masks of a shell drawn with its client area's top left corner at this place of the frame.
   */
  void collect(Shell shell, int x, int y, List<Rectangle> out) {
    org.eclipse.swt.graphics.Rectangle area = shell.getClientArea();
    Rectangle clip = new Rectangle(0, 0, area.width, area.height);
    Boolean mark = rules.markOf(shell, shell.getData(MaskRules.PROPERTY), SwtSupport.name(shell));
    if (Boolean.TRUE.equals(mark)) {
      out.add(frameRect(clip, x, y));
      return;
    }
    for (Control child : shell.getChildren()) {
      walk(shell, child, clip, Boolean.FALSE.equals(mark), x, y, out);
    }
  }

  /**
   * @param clip what of the shell the control's ancestors show, in the shell's coordinates
   * @param unmaskedAbove whether an ancestor is marked to unmask
   */
  private void walk(Shell shell, Control c, Rectangle clip, boolean unmaskedAbove, int x, int y,
      List<Rectangle> out) {
    if (!c.getVisible()) {
      return;
    }
    Display display = c.getDisplay();
    Rectangle visible = clip.intersection(awt(display.map(c.getParent(), shell, c.getBounds())));
    if (visible.isEmpty()) {
      return;
    }
    Boolean mark = rules.markOf(c, c.getData(MaskRules.PROPERTY), SwtSupport.name(c));
    boolean unmaskedHere = unmaskedAbove || Boolean.FALSE.equals(mark);
    MaskRules.Kind kind = kind(c);
    if (kind == MaskRules.Kind.SECRET || Boolean.TRUE.equals(mark)
        || (!unmaskedHere && (rules.masks(kind) || (rules.maskSensitive && sensitive(c))))) {
      out.add(frameRect(visible, x, y));
      return; // all of it is covered
    }
    if (rules.maskText && !unmaskedHere) {
      items(shell, c, visible, x, y, out);
    }
    if (c instanceof Composite) {
      Rectangle inside = visible.intersection(awt(display.map(c, shell, ((Composite) c).getClientArea())));
      for (Control child : ((Composite) c).getChildren()) {
        walk(shell, child, inside, unmaskedHere, x, y, out);
      }
    }
  }

  /** Masks the titles a tab folder or tool bar draws itself, not with a control. */
  private void items(Shell shell, Control c, Rectangle visible, int x, int y, List<Rectangle> out) {
    if (c instanceof TabFolder) {
      for (TabItem item : ((TabFolder) c).getItems()) {
        item(shell, c, item.getBounds(), visible, x, y, out);
      }
    } else if (c instanceof CTabFolder) {
      for (CTabItem item : ((CTabFolder) c).getItems()) {
        item(shell, c, item.getBounds(), visible, x, y, out);
      }
    } else if (c instanceof ToolBar) {
      for (ToolItem item : ((ToolBar) c).getItems()) {
        if (hasText(item.getText())) {
          item(shell, c, item.getBounds(), visible, x, y, out);
        }
      }
    }
  }

  private static void item(Shell shell, Control c, org.eclipse.swt.graphics.Rectangle bounds, Rectangle visible,
      int x, int y, List<Rectangle> out) {
    Rectangle at = visible.intersection(awt(c.getDisplay().map(c, shell, bounds)));
    if (!at.isEmpty()) {
      out.add(frameRect(at, x, y));
    }
  }

  static Rectangle frameRect(Rectangle r, int x, int y) {
    return new Rectangle(r.x + x - MARGIN, r.y + y - MARGIN, r.width + 2 * MARGIN, r.height + 2 * MARGIN);
  }

  private static Rectangle awt(org.eclipse.swt.graphics.Rectangle r) {
    return new Rectangle(r.x, r.y, r.width, r.height);
  }

  /** How the rules mask a control of this kind. */
  static MaskRules.Kind kind(Control c) {
    if (c instanceof Text) {
      return (c.getStyle() & SWT.PASSWORD) != 0 ? MaskRules.Kind.SECRET : MaskRules.Kind.INPUT;
    }
    if (c instanceof StyledText) {
      return ((StyledText) c).getEditable() ? MaskRules.Kind.INPUT : MaskRules.Kind.TEXT;
    }
    if (c instanceof Combo || c instanceof CCombo) {
      return (c.getStyle() & SWT.READ_ONLY) != 0 ? MaskRules.Kind.TEXT : MaskRules.Kind.INPUT;
    }
    if (c instanceof Spinner || c instanceof DateTime) {
      return MaskRules.Kind.INPUT;
    }
    if (c instanceof org.eclipse.swt.widgets.List || c instanceof Table || c instanceof Tree || c instanceof Link) {
      return MaskRules.Kind.TEXT;
    }
    if (c instanceof Label) {
      Label label = (Label) c;
      return hasText(label.getText()) ? MaskRules.Kind.TEXT
          : label.getImage() != null ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
    }
    if (c instanceof CLabel) {
      CLabel label = (CLabel) c;
      return hasText(label.getText()) ? MaskRules.Kind.TEXT
          : label.getImage() != null ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
    }
    if (c instanceof Button) {
      Button button = (Button) c;
      return hasText(button.getText()) ? MaskRules.Kind.TEXT
          : button.getImage() != null ? MaskRules.Kind.IMAGE : MaskRules.Kind.NONE;
    }
    if (ToolkitSupport.isA(c.getClass(), BROWSER)) {
      return MaskRules.Kind.WEB;
    }
    return ToolkitSupport.isA(c.getClass(), GL_CANVAS) ? MaskRules.Kind.OPAQUE : MaskRules.Kind.NONE;
  }

  /** Whether the control's text, hint or tooltip has a sensitive keyword. */
  static boolean sensitive(Control c) {
    String text = null;
    String hint = null;
    if (c instanceof Text) {
      text = ((Text) c).getText(); // never a password's: that is SECRET, masked first
      hint = ((Text) c).getMessage();
    } else if (c instanceof Combo) {
      text = ((Combo) c).getText();
    } else {
      text = SwtSupport.text(c);
    }
    return MaskRules.sensitive(text, hint, c.getToolTipText());
  }

  private static boolean hasText(String text) {
    return text != null && !text.trim().isEmpty();
  }
}
