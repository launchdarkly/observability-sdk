package com.launchdarkly.observability.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * What the toolkit instrumentations share, kept free of any toolkit's classes so that each can be
 * loaded in a runtime that lacks the other.
 */
public final class ToolkitSupport {
  static final int MAX_PATH_DEPTH = 24;
  static final int MAX_TEXT_LENGTH = 128;

  private ToolkitSupport() {}

  /** The class's simple name, or for an anonymous class its superclass's. */
  static String simpleName(Class<?> type) {
    Class<?> named = type;
    while (named.getSimpleName().isEmpty()) { // an anonymous class always has a superclass
      named = named.getSuperclass();
    }
    return named.getSimpleName();
  }

  /**
   * The names of the JVM's live threads. A toolkit is in use once its threads run, and asking the
   * toolkit itself would start it.
   */
  public static List<String> threadNames() {
    ThreadGroup root = Thread.currentThread().getThreadGroup();
    while (root.getParent() != null) {
      root = root.getParent();
    }
    Thread[] threads = new Thread[root.activeCount() + 16];
    int count = root.enumerate(threads, true);
    List<String> names = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      names.add(threads[i].getName());
    }
    return names;
  }

  /** Text cut to {@link #MAX_TEXT_LENGTH}, or null for none or blank. */
  static String visibleText(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
  }

  /**
   * Whether the application has started AWT, told from its threads, since asking AWT itself would
   * start it.
   */
  public static boolean awtInUse() {
    return anyAwtThread(threadNames());
  }

  /** Whether one of these thread names is a thread AWT starts: its event queue, or its toolkit's. */
  static boolean anyAwtThread(Iterable<String> threadNames) {
    for (String name : threadNames) {
      if (name.startsWith("AWT-EventQueue") || name.equals("AWT-AppKit") || name.equals("AWT-Windows")
          || name.equals("AWT-XAWT")) {
        return true;
      }
    }
    return false;
  }

  /** Whether the application has started JavaFX, told from its threads, since asking JavaFX would start it. */
  public static boolean fxInUse() {
    return threadNames().contains("JavaFX Application Thread");
  }

  /** SWT's display, whose event loop is on the thread that runs SWT. */
  static final String SWT_DISPLAY = "org.eclipse.swt.widgets.Display";

  /**
   * The thread running SWT's event loop, or null. SWT names no thread, and asking SWT for its
   * display would start it, so the loop is told from the threads' stacks: the application's UI
   * thread waits in {@code Display.sleep} or dispatches from {@code Display.readAndDispatch}.
   */
  public static Thread swtThread() {
    for (java.util.Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
      if (runsSwtLoop(thread.getValue())) {
        return thread.getKey();
      }
    }
    return null;
  }

  /** Whether a thread with this stack is in SWT's event loop. */
  static boolean runsSwtLoop(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (frame.getClassName().equals(SWT_DISPLAY)
          && (frame.getMethodName().equals("sleep") || frame.getMethodName().equals("readAndDispatch"))) {
        return true;
      }
    }
    return false;
  }

  /** Whether the class is, or extends, the class of this name, which need not be loaded. */
  public static boolean isA(Class<?> type, String name) {
    for (Class<?> t = type; t != null; t = t.getSuperclass()) {
      if (t.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }
}
