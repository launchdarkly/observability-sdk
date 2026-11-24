package com.launchdarkly.observability;

/** What {@link LDReplay#start()} did. The same outcomes as on Android and iOS. */
public enum SessionReplayStartResult {
  /** Session replay is not installed, or has not finished starting; the start applies once it has. */
  UNAVAILABLE,

  /** Session replay is recording because this call started it. */
  STARTED,

  /** Session replay was already recording. */
  ALREADY_STARTED,

  /** Session replay stayed stopped because sampling left this session out. */
  SAMPLED_OUT,

  /**
   * Session replay stayed stopped because the backend refused it in a way retrying cannot fix, such
   * as for a project in a region without session replay. It tries again at the next launch.
   */
  UNRECOVERABLE_ERROR;

  /** Whether session replay is recording once the call has been applied. */
  public boolean isRunning() {
    return this == STARTED || this == ALREADY_STARTED;
  }
}
