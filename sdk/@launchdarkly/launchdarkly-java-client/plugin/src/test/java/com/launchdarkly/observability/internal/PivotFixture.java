package com.launchdarkly.observability.internal;

import org.apache.pivot.wtk.ApplicationContext;
import org.apache.pivot.wtk.Component;
import org.apache.pivot.wtk.Container;
import org.apache.pivot.wtk.Panel;
import org.apache.pivot.wtk.Window;

import java.awt.EventQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Apache Pivot displays for tests, built on the event dispatch thread, which Pivot requires. */
public final class PivotFixture {
  private PivotFixture() {}

  /** Runs on the event dispatch thread and returns what it made. */
  public static <T> T onEdt(Callable<T> task) throws Exception {
    AtomicReference<T> made = new AtomicReference<>();
    AtomicReference<Exception> failed = new AtomicReference<>();
    EventQueue.invokeAndWait(() -> {
      try {
        made.set(task.call());
      } catch (Exception e) {
        failed.set(e);
      }
    });
    if (failed.get() != null) {
      throw failed.get();
    }
    return made.get();
  }

  /** A host of this size whose display holds one window, with a panel that places children where told. */
  public static Panel content(ApplicationContext.DisplayHost host, int width, int height) {
    host.setSize(width, height);
    host.getDisplay().setSize(width, height);
    Window window = new Window();
    Panel panel = new Panel();
    window.setContent(panel);
    window.setLocation(0, 0);
    // Pivot's display sizes each window to its preferred size when it lays out.
    window.setPreferredSize(width, height);
    window.setSize(width, height);
    window.open(host.getDisplay());
    panel.setLocation(0, 0);
    panel.setSize(width, height);
    return panel;
  }

  /** Adds the child at this place, sized. */
  public static <T extends Component> T at(Container parent, T child, int x, int y, int width, int height) {
    parent.add(child);
    child.setLocation(x, y);
    child.setSize(width, height);
    return child;
  }
}
