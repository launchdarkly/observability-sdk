package com.launchdarkly.observability.internal;

import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SWT, the Eclipse toolkit, which draws with the platform's own widgets on its own UI thread, and
 * shares nothing with AWT. Never a dependency of the plugin: this class is loaded only by classes
 * that are themselves loaded by name when the runtime has SWT, and it is used on SWT's thread.
 * <p>
 * SWT has no component names. A control's name is its data under {@link #NAME}, a string the
 * application sets with {@code control.setData("name", "save")}.
 */
public final class SwtSupport {
  /** The data key of a control's name. */
  public static final String NAME = "name";

  private SwtSupport() {}

  /**
   * The display whose event loop is running, or null. Found from the threads, which starts nothing,
   * then asked of SWT, which the application has then started.
   */
  public static Display display() {
    Thread thread = ToolkitSupport.swtThread();
    return thread == null ? null : Display.findDisplay(thread); // a disposed display is no longer found
  }

  /** The name the application gave a widget, or null. */
  public static String name(Widget widget) {
    Object name = widget.getData(NAME);
    return name instanceof String && !((String) name).isEmpty() ? (String) name : null;
  }

  /**
   * The visible text of a button, label or link, which describes what was clicked, or null. The text
   * of other controls is user data, so it is never read for this.
   */
  public static String text(Control control) {
    String text = null;
    if (control instanceof Button) {
      text = ((Button) control).getText();
    } else if (control instanceof Label) {
      text = ((Label) control).getText();
    } else if (control instanceof CLabel) {
      text = ((CLabel) control).getText();
    } else if (control instanceof Link) {
      text = ((Link) control).getText().replaceAll("<[^>]*>", ""); // the link's markup is not text
    }
    return ToolkitSupport.visibleText(text);
  }

  /** The control's place in its shell, outermost first: {@code Shell/Composite/Button#save}. */
  public static String path(Control control) {
    List<String> segments = new ArrayList<>();
    for (Control c = control; c != null && segments.size() < ToolkitSupport.MAX_PATH_DEPTH; c = c.getParent()) {
      String name = name(c);
      segments.add(ToolkitSupport.simpleName(c.getClass()) + (name != null ? "#" + name : ""));
    }
    Collections.reverse(segments);
    return String.join("/", segments);
  }

  /**
   * The class a shell is the screen of: a JFace window's, which keeps itself as its shell's data, or
   * else the shell's own.
   */
  public static Class<?> screenClass(Shell shell) {
    Object data = shell.getData();
    return data != null && !(data instanceof String) ? data.getClass() : shell.getClass();
  }
}
