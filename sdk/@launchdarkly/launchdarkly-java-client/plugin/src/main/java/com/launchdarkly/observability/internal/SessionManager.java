package com.launchdarkly.observability.internal;

import java.security.SecureRandom;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * The session every signal is recorded in. Its rules are those of Android's session manager:
 * <ul>
 *   <li>a session lasts at most {@link Constants#SESSION_MAX_LIFETIME_MILLIS}, however active;</li>
 *   <li>while the application is in the foreground, a session never times out;</li>
 *   <li>in the background, a stretch of {@code backgroundTimeoutMillis} with nothing recorded ends
 *   it, and the next signal starts a new one.</li>
 * </ul>
 * A desktop application is in the background when none of its windows is active. One with no
 * windows, a console application, is always in the foreground.
 * <p>
 * Ids are 32 lowercase hex characters, the format of OpenTelemetry Android's session ids. Safe to
 * use from any thread.
 */
public final class SessionManager {
  private enum State {
    FOREGROUND,
    BACKGROUND,
    /** Back in the foreground; the next signal still checks the background timeout, then settles. */
    TRANSITIONING_TO_FOREGROUND
  }

  private final long maxLifetimeMillis;
  private final long backgroundTimeoutMillis;
  private final LongSupplier clock;
  private final Random random;

  private String sessionId;
  private long startedAtMillis;
  private long lastUsedMillis;
  private State state = State.FOREGROUND;

  /**
   * Creates a manager with the standard lifetime, the wall clock and a secure random source.
   *
   * @param backgroundTimeoutMillis how long a session survives in the background without a signal
   */
  public SessionManager(long backgroundTimeoutMillis) {
    this(Constants.SESSION_MAX_LIFETIME_MILLIS, backgroundTimeoutMillis, System::currentTimeMillis,
        new SecureRandom());
  }

  SessionManager(long maxLifetimeMillis, long backgroundTimeoutMillis, LongSupplier clock, Random random) {
    this.maxLifetimeMillis = maxLifetimeMillis;
    this.backgroundTimeoutMillis = backgroundTimeoutMillis;
    this.clock = clock;
    this.random = random;
  }

  /**
   * Returns the current session id, first starting a new session if there is none or the current
   * one has ended. A clock set backwards ends the session too, so a wrong clock cannot keep one
   * session open past its limits.
   */
  public synchronized String getSessionId() {
    long now = clock.getAsLong();
    long age = now - startedAtMillis;
    long idle = now - lastUsedMillis;
    boolean timedOut = state != State.FOREGROUND && (idle < 0 || idle >= backgroundTimeoutMillis);
    if (sessionId == null || age < 0 || age >= maxLifetimeMillis || timedOut) {
      sessionId = newId();
      startedAtMillis = now;
    }
    lastUsedMillis = now;
    if (state == State.TRANSITIONING_TO_FOREGROUND) {
      state = State.FOREGROUND;
    }
    return sessionId;
  }

  /** The application came to the foreground. */
  public synchronized void onForegrounded() {
    if (state == State.BACKGROUND) {
      state = State.TRANSITIONING_TO_FOREGROUND;
    }
  }

  /** The application went to the background, so the background timeout now applies. */
  public synchronized void onBackgrounded() {
    state = State.BACKGROUND;
  }

  private String newId() {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    StringBuilder id = new StringBuilder(32);
    for (byte b : bytes) {
      id.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
    }
    return id.toString();
  }
}
