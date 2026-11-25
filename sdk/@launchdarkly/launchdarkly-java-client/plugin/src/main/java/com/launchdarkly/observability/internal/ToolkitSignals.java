package com.launchdarkly.observability.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Joins the signals of the application's UI toolkits into one foreground. An application can show
 * Swing and JavaFX windows side by side; moving from one toolkit's window to the other's leaves the
 * first toolkit with no active window, which it reports as the background. The application is in the
 * background only when no toolkit has an active window, so that report is dropped while another
 * toolkit has one. Screens and clicks pass straight through.
 */
final class ToolkitSignals implements ToolkitInstrumentation.Signals {
  private final ToolkitInstrumentation.Signals target;
  private final List<ToolkitInstrumentation> toolkits = new CopyOnWriteArrayList<>();
  private boolean foreground = true;

  ToolkitSignals(ToolkitInstrumentation.Signals target) {
    this.target = target;
  }

  void add(ToolkitInstrumentation toolkit) {
    toolkits.add(toolkit);
  }

  private boolean anyActiveWindow() {
    for (ToolkitInstrumentation toolkit : toolkits) {
      if (toolkit.hasActiveWindow()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public synchronized void foreground() {
    if (!foreground) {
      foreground = true;
      target.foreground();
    }
  }

  @Override
  public void background() {
    if (!anyActiveWindow() && leaveForeground()) {
      target.background();
    }
  }

  @Override
  public void startedInBackground() {
    if (!anyActiveWindow() && leaveForeground()) {
      target.startedInBackground();
    }
  }

  private synchronized boolean leaveForeground() {
    boolean was = foreground;
    foreground = false;
    return was;
  }

  @Override
  public void screenShown(String name, String screenClass, String screenId) {
    target.screenShown(name, screenClass, screenId);
  }

  @Override
  public void click(Click click) {
    target.click(click);
  }
}
