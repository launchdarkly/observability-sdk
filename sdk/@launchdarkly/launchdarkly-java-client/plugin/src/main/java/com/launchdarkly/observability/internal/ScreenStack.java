package com.launchdarkly.observability.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * The screens shown so far, for {@code event.previous_screen}. Ported from the Android plugin's
 * {@code ScreenStack}: a screen already on top does not count as a navigation, and showing a screen
 * lower in the stack pops back to it. Screens are matched by id when they have one, else by name.
 * Safe to use from any thread.
 */
final class ScreenStack {
  /** What recording a screen did. */
  static final class Change {
    /** Recording the current screen again: not a navigation. */
    static final Change UNCHANGED = new Change(false, null);

    final boolean changed;
    /** The screen shown before, or null for the first. */
    final String previous;

    private Change(boolean changed, String previous) {
      this.changed = changed;
      this.previous = previous;
    }
  }

  private static final class Entry {
    final String key;
    final String name;
    final String id;

    Entry(String key, String name, String id) {
      this.key = key;
      this.name = name;
      this.id = id;
    }
  }

  private final List<Entry> stack = new ArrayList<>();

  /** The current screen's id, or null. */
  synchronized String currentScreenId() {
    return stack.isEmpty() ? null : stack.get(stack.size() - 1).id;
  }

  /** The current screen's name, or null. */
  synchronized String currentScreenName() {
    return stack.isEmpty() ? null : stack.get(stack.size() - 1).name;
  }

  synchronized Change record(String name, String id) {
    String key = id != null ? id : name;
    Entry top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
    if (top != null && top.key.equals(key)) {
      return Change.UNCHANGED;
    }
    String previous = top == null ? null : top.name;
    int existing = -1;
    for (int i = stack.size() - 1; i >= 0; i--) {
      if (stack.get(i).key.equals(key)) {
        existing = i;
        break;
      }
    }
    if (existing >= 0) {
      while (stack.size() - 1 > existing) {
        stack.remove(stack.size() - 1);
      }
    } else {
      stack.add(new Entry(key, name, id));
    }
    return new Change(true, previous);
  }

  synchronized void reset() {
    stack.clear();
  }
}
