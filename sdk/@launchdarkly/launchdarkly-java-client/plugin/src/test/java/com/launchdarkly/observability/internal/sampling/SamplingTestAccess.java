package com.launchdarkly.observability.internal.sampling;

import java.io.IOException;

/** Lets tests in other packages ungzip request bodies. */
public final class SamplingTestAccess {
  private SamplingTestAccess() {}

  public static String gunzip(byte[] bytes) throws IOException {
    return SamplingTest.gunzip(bytes);
  }
}
