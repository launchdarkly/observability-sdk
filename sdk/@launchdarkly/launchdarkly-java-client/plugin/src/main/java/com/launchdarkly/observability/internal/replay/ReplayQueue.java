package com.launchdarkly.observability.internal.replay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The replay items waiting to be sent, oldest first, within a cost limit. An item that would take
 * the queue past the limit is dropped, so a backend that cannot be reached costs a bounded amount of
 * memory; the capture checks {@link #isFull} and skips frames instead of encoding them for nothing.
 * Thread-safe.
 */
final class ReplayQueue {
  /** The limit, roughly bytes of image data: the Android plugin's limit for replay. */
  static final long DEFAULT_COST_LIMIT = 2_500_000;

  private final long costLimit;
  private final ArrayDeque<ReplayItem> items = new ArrayDeque<>();
  private long cost;

  ReplayQueue() {
    this(DEFAULT_COST_LIMIT);
  }

  ReplayQueue(long costLimit) {
    this.costLimit = costLimit;
  }

  /** Adds the item, unless it would take the queue past its limit. An empty queue takes any one item. */
  synchronized boolean offer(ReplayItem item) {
    int itemCost = item.cost();
    if (cost != 0 && cost + itemCost > costLimit) {
      return false;
    }
    items.addLast(item);
    cost += itemCost;
    return true;
  }

  synchronized boolean isFull() {
    return cost >= costLimit;
  }

  synchronized int size() {
    return items.size();
  }

  /**
   * The oldest items, up to {@code max} of them and until their cost passes {@code budget}; the item
   * that passes it is included, so a single large frame still goes out.
   */
  synchronized List<ReplayItem> peek(long budget, int max) {
    List<ReplayItem> batch = new ArrayList<>(Math.min(max, items.size()));
    long sum = 0;
    for (ReplayItem item : items) {
      batch.add(item);
      sum += item.cost();
      if (batch.size() >= max || sum > budget) {
        break;
      }
    }
    return batch;
  }

  /** Removes the oldest {@code count} items, once they are sent. */
  synchronized void removeFirst(int count) {
    Iterator<ReplayItem> it = items.iterator();
    for (int i = 0; i < count && it.hasNext(); i++) {
      cost -= it.next().cost();
      it.remove();
    }
  }

  synchronized void clear() {
    items.clear();
    cost = 0;
  }
}
