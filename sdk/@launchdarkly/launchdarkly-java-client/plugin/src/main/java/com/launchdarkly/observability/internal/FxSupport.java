package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import java.util.List;

/**
 * Creates the JavaFX instrumentation when the runtime has JavaFX, which most runtimes do not: it
 * left the JDK in Java 11, and the SWT instrumentation when it has SWT. Each is loaded by name, so
 * that nothing that refers to a toolkit's classes is ever loaded without them.
 */
final class FxSupport {
  static final String PROBE_CLASS = "javafx.stage.Window";
  static final String INSTRUMENTATION_CLASS = "com.launchdarkly.observability.internal.FxInstrumentation";
  static final String SWT_PROBE_CLASS = "org.eclipse.swt.widgets.Display";
  static final String SWT_INSTRUMENTATION_CLASS = "com.launchdarkly.observability.internal.SwtInstrumentation";

  private FxSupport() {}

  /** Adds the JavaFX instrumentation to the toolkits, if the runtime has JavaFX, and SWT's, if it has SWT. */
  static void addTo(List<ToolkitInstrumentation> toolkits, ToolkitInstrumentation.Signals signals, boolean windows,
      boolean clicks, boolean recordText, LDLogger logger) {
    addTo(toolkits, PROBE_CLASS, INSTRUMENTATION_CLASS, signals, windows, clicks, recordText, logger);
    addTo(toolkits, "SWT", SWT_PROBE_CLASS, SWT_INSTRUMENTATION_CLASS, signals, windows, clicks, recordText, logger);
  }

  static void addTo(List<ToolkitInstrumentation> toolkits, String probeClass, String instrumentationClass,
      ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger) {
    addTo(toolkits, "JavaFX", probeClass, instrumentationClass, signals, windows, clicks, recordText, logger);
  }

  static void addTo(List<ToolkitInstrumentation> toolkits, String toolkit, String probeClass,
      String instrumentationClass, ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks,
      boolean recordText, LDLogger logger) {
    ToolkitInstrumentation created = create(toolkit, probeClass, instrumentationClass, signals, windows, clicks,
        recordText, logger);
    if (created != null) {
      toolkits.add(created);
    }
  }

  /** The JavaFX instrumentation, or null if the runtime has no JavaFX or it could not be created. */
  static ToolkitInstrumentation create(ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks,
      boolean recordText, LDLogger logger) {
    return create(PROBE_CLASS, INSTRUMENTATION_CLASS, signals, windows, clicks, recordText, logger);
  }

  static ToolkitInstrumentation create(String probeClass, String instrumentationClass,
      ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger) {
    return create("JavaFX", probeClass, instrumentationClass, signals, windows, clicks, recordText, logger);
  }

  static ToolkitInstrumentation create(String toolkit, String probeClass, String instrumentationClass,
      ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger) {
    ClassLoader loader = FxSupport.class.getClassLoader();
    try {
      Class.forName(probeClass, false, loader);
    } catch (ClassNotFoundException | LinkageError e) {
      // No such toolkit, or one built for a newer Java than this runtime: nothing to watch.
      return null;
    }
    try {
      return (ToolkitInstrumentation) Class.forName(instrumentationClass, true, loader)
          .getDeclaredMethod("create", ToolkitInstrumentation.Signals.class, boolean.class, boolean.class,
              boolean.class, LDLogger.class)
          .invoke(null, signals, windows, clicks, recordText, logger);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      logger.info("Not watching {} windows: {}", toolkit, e.toString());
      return null;
    }
  }
}
