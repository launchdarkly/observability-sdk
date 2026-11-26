package com.launchdarkly.observability.internal.replay;

/** Where a capture source reports the mouse. Called on the toolkit's UI thread; must return quickly. */
interface PointerSink {
  /**
   * @param action pressed, released, or moved (with or without a button down)
   * @param screenX the position on the screen, in the toolkit's logical pixels
   * @param screenY the position on the screen, in the toolkit's logical pixels
   * @param timestamp when, in epoch milliseconds
   */
  void pointer(ReplayItem.Pointer.Action action, double screenX, double screenY, long timestamp);
}
