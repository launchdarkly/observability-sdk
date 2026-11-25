package com.launchdarkly.observability.internal.replay;

/**
 * The rrweb event vocabulary the replay player reads. The numbers are rrweb's own; the custom
 * event tags are LaunchDarkly's, shared with the web, Android and iOS SDKs.
 */
final class RRWeb {
  private RRWeb() {}

  // Event types.
  static final int FULL_SNAPSHOT = 2;
  static final int INCREMENTAL_SNAPSHOT = 3;
  static final int META = 4;
  static final int CUSTOM = 5;

  // Node types.
  static final int DOCUMENT_NODE = 0;
  static final int ELEMENT_NODE = 2;

  // Incremental snapshot sources.
  static final int SOURCE_MUTATION = 0;
  static final int SOURCE_MOUSE_MOVE = 1;
  static final int SOURCE_MOUSE_INTERACTION = 2;

  // Mouse interaction types.
  static final int MOUSE_UP = 0;
  static final int MOUSE_DOWN = 1;

  // Custom event tags.
  static final String TAG_CLICK = "Click";
  static final String TAG_VIEWPORT = "Viewport";
  static final String TAG_RELOAD = "Reload";
  static final String TAG_IDENTIFY = "Identify";
  static final String TAG_TRACK = "Track";
  static final String TAG_NAVIGATE = "Navigate";
  static final String TAG_FOREGROUND = "Foreground";
  static final String TAG_BACKGROUND = "Background";
  static final String TAG_LAUNCH = "Launch";

  /**
   * The margin around the recorded screen in the replay document, which keeps the player's
   * scrollbars off the image. Positions are shifted by it.
   */
  static final int DOCUMENT_PADDING = 11;
}
