package com.launchdarkly.observability.internal;

/** A mouse click on a component or node, as the desktop instrumentation reports it. */
public final class Click {
  final String tag;
  final String classname;
  final String id;
  final String text;
  final String xpath;
  final long x;
  final long y;
  final long startMillis;
  final long endMillis;

  /**
   * @param tag the component's simple class name
   * @param classname the component's class name
   * @param id the name or id the application gave the component, or null
   * @param text the component's visible text, or null when text is not recorded
   * @param xpath the component's place in its window, outermost first
   * @param x the click's x position on the screen
   * @param y the click's y position on the screen
   * @param startMillis when the press began
   * @param endMillis when the click completed
   */
  public Click(String tag, String classname, String id, String text, String xpath, long x, long y, long startMillis,
      long endMillis) {
    this.tag = tag;
    this.classname = classname;
    this.id = id;
    this.text = text;
    this.xpath = xpath;
    this.x = x;
    this.y = y;
    this.startMillis = startMillis;
    this.endMillis = endMillis;
  }

  /** The component's simple class name. */
  public String getTag() {
    return tag;
  }

  /** The component's class name. */
  public String getClassname() {
    return classname;
  }

  /** The name or id the application gave the component, or null. */
  public String getId() {
    return id;
  }

  /** The component's place in its window, outermost first. */
  public String getXpath() {
    return xpath;
  }

  /** The component's visible text, or null when text is not recorded. */
  public String getText() {
    return text;
  }

  /** When the click completed, in epoch milliseconds. */
  public long getEndMillis() {
    return endMillis;
  }
}
