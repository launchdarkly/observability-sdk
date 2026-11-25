package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Sends the queue on a background thread: every {@link #INTERVAL_MILLIS}, and at once on a flush.
 * One batch is in flight at a time, so pushes reach the backend in order. A failed batch stays at
 * the front of the queue, and the next attempt waits, doubling from {@link #BASE_BACKOFF_MILLIS} to
 * {@link #MAX_BACKOFF_MILLIS} with 20% jitter. The numbers are the Android plugin's.
 */
final class BatchWorker {
  static final long INTERVAL_MILLIS = 1500;
  static final long MAX_BATCH_COST = 30_000;
  static final int MAX_BATCH_ITEMS = 100;
  static final long BASE_BACKOFF_MILLIS = 2000;
  static final long MAX_BACKOFF_MILLIS = 60_000;
  static final double BACKOFF_JITTER = 0.2;

  /** Sends a batch, or throws to have it retried. */
  interface Exporter {
    void export(List<ReplayItem> items) throws ReplayApi.Failure;
  }

  private final ReplayQueue queue;
  private final Exporter exporter;
  private final LDLogger logger;
  private final ScheduledExecutorService executor;
  private final LongSupplier clock;
  private final Random random;

  // Touched only on the worker's thread.
  private int failures;
  private long backoffUntil;

  BatchWorker(ReplayQueue queue, Exporter exporter, LDLogger logger) {
    this(queue, exporter, logger, Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "LaunchDarkly-session-replay-export");
      thread.setDaemon(true);
      return thread;
    }), System::currentTimeMillis, new Random());
  }

  BatchWorker(ReplayQueue queue, Exporter exporter, LDLogger logger, ScheduledExecutorService executor,
      LongSupplier clock, Random random) {
    this.queue = queue;
    this.exporter = exporter;
    this.logger = logger;
    this.executor = executor;
    this.clock = clock;
    this.random = random;
  }

  void start() {
    executor.scheduleWithFixedDelay(this::send, INTERVAL_MILLIS, INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
  }

  /** Sends what is queued now, without waiting for it. */
  void flush() {
    try {
      executor.execute(this::send);
    } catch (RejectedExecutionException e) {
      // Stopped.
    }
  }

  /** Runs the task on the worker's thread, after what it is doing now, so it never overlaps a send. */
  void run(Runnable task) {
    try {
      executor.execute(() -> {
        try {
          task.run();
        } catch (RuntimeException e) {
          logger.debug("Session replay task failed: {}", e.toString());
        }
      });
    } catch (RejectedExecutionException e) {
      // Stopped.
    }
  }

  /**
   * Sends what is queued, waiting up to {@code timeoutMillis}, then stops. What is still queued
   * after that is dropped.
   *
   * @return whether everything queued was sent
   */
  boolean stop(long timeoutMillis) {
    flush();
    executor.shutdown();
    try {
      if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    return queue.size() == 0;
  }

  /** Sends batches until the queue is empty or an export fails. On the worker's thread. */
  void send() {
    if (clock.getAsLong() < backoffUntil) {
      return;
    }
    while (true) {
      List<ReplayItem> batch = queue.peek(MAX_BATCH_COST, MAX_BATCH_ITEMS);
      if (batch.isEmpty()) {
        return;
      }
      try {
        exporter.export(batch);
      } catch (ReplayApi.Failure | RuntimeException e) {
        failures++;
        long backoff = (long) Math.min(BASE_BACKOFF_MILLIS * Math.pow(2, failures - 1), MAX_BACKOFF_MILLIS);
        long jitter = (long) (backoff * BACKOFF_JITTER * (random.nextDouble() * 2 - 1));
        backoffUntil = clock.getAsLong() + backoff + jitter;
        logger.warn("Session replay could not send {} items, retrying in {} ms: {}", batch.size(),
            backoff + jitter, e.toString());
        return;
      }
      failures = 0;
      queue.removeFirst(batch.size());
    }
  }
}
