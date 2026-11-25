package com.launchdarkly.observability.internal;

import org.apache.pivot.wtk.ApplicationContext;
import org.apache.pivot.wtk.Button;
import org.apache.pivot.wtk.Component;
import org.apache.pivot.wtk.Display;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.content.ButtonData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Apache Pivot, which draws its own components inside one AWT component, its display host. AWT sees
 * only the host, so the plugin asks Pivot what is inside it.
 * <p>
 * Pivot is never a dependency of the plugin. This class is loaded only for a component whose class
 * is the host's, by name ({@link #HOST}), so only when the application has Pivot; callers catch the
 * {@link LinkageError} of a Pivot too different from 2.0, the version it is built against. Called on
 * the event dispatch thread, which is Pivot's.
 */
public final class PivotSupport {
  /** The AWT component that hosts a Pivot display. */
  public static final String HOST = "org.apache.pivot.wtk.ApplicationContext$DisplayHost";

  private PivotSupport() {}

  /** The Pivot display in a host. */
  public static Display display(java.awt.Component host) {
    return ((ApplicationContext.DisplayHost) host).getDisplay();
  }

  /** Host pixels per Pivot pixel: Pivot can scale its whole display. */
  public static double scale(java.awt.Component host) {
    return ((ApplicationContext.DisplayHost) host).getScale();
  }

  /** The innermost Pivot component at this point of the host, in host pixels; the display when none is. */
  public static Component at(java.awt.Component host, int x, int y) {
    double scale = scale(host);
    Display display = display(host);
    Component found = display.getDescendantAt((int) Math.floor(x / scale), (int) Math.floor(y / scale));
    return found != null ? found : display;
  }

  /**
   * A click at this point of the host, in host pixels: the Pivot component there, placed below the
   * host's own path.
   */
  static Click click(java.awt.Component host, int x, int y, boolean recordText, String hostPath, long screenX,
      long screenY, long start, long end) {
    Component clicked = at(host, x, y);
    String text = recordText ? DesktopInstrumentation.clip(text(clicked)) : null;
    return new Click(ToolkitSupport.simpleName(clicked.getClass()), clicked.getClass().getName(), name(clicked), text,
        hostPath + "/" + path(clicked, DesktopInstrumentation.MAX_PATH_DEPTH), screenX, screenY, start, end);
  }

  /** The name the application gave a component, or null. */
  public static String name(Component component) {
    return component.getName();
  }

  /**
   * The visible text of a button or label, which describes what was clicked, or null. The text of
   * other components is user data, so it is never read for this.
   */
  public static String text(Component component) {
    if (component instanceof Label) {
      return ((Label) component).getText();
    }
    if (component instanceof Button) {
      return buttonText((Button) component);
    }
    return null;
  }

  /** A button's text: its data, when that is text, or the text of its {@link ButtonData}. */
  public static String buttonText(Button button) {
    Object data = button.getButtonData();
    if (data instanceof String) {
      return (String) data;
    }
    return data instanceof ButtonData ? ((ButtonData) data).getText() : null;
  }

  /**
   * The component's place in the display, outermost first: {@code Display/Frame/BoxPane/PushButton#save},
   * with a component's name after {@code #} when the application gave it one.
   *
   * @param maxDepth the most segments
   */
  public static String path(Component component, int maxDepth) {
    List<String> segments = new ArrayList<>();
    for (Component c = component; c != null && segments.size() < maxDepth; c = c.getParent()) {
      String segment = ToolkitSupport.simpleName(c.getClass());
      String name = name(c);
      if (name != null) {
        segment += "#" + name;
      }
      segments.add(segment);
    }
    Collections.reverse(segments);
    return String.join("/", segments);
  }
}
