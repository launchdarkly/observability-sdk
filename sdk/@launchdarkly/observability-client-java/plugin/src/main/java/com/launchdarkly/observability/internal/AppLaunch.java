package com.launchdarkly.observability.internal;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.prefs.Preferences;

/**
 * What the {@code app_launch} span says about a launch: whether it is the first run of this
 * application on this machine, the first run of a new version, or a relaunch, worked out as on
 * Android by comparing the current version with the one recorded at the previous launch.
 */
public final class AppLaunch {
  enum LaunchType {
    INSTALL("install"),
    UPDATE("update"),
    RELAUNCH("relaunch"),
    /** The application's version is not known, so there is nothing to compare. */
    UNKNOWN("unknown");

    final String wireValue;

    LaunchType(String wireValue) {
      this.wireValue = wireValue;
    }
  }

  /** Where the last launched version is kept between runs. */
  interface VersionStore {
    /** The version recorded at the previous launch, or null if none was. */
    String read();

    /** Records the current version for the next launch. */
    void write(String version);
  }

  final LaunchType launchType;
  final String version;
  final String build;
  final String previousVersion;
  final long timestamp;

  private AppLaunch(LaunchType launchType, String version, String build, String previousVersion, long timestamp) {
    this.launchType = launchType;
    this.version = version;
    this.build = build;
    this.previousVersion = previousVersion;
    this.timestamp = timestamp;
  }

  /** {@code install}, {@code update}, {@code relaunch} or {@code unknown}. */
  public String getLaunchType() {
    return launchType.wireValue;
  }

  /** The application's version, or null if it is not known. */
  public String getVersion() {
    return version;
  }

  /** The application's build, or null. */
  public String getBuild() {
    return build;
  }

  /** The version of the previous launch, for an update; otherwise null. */
  public String getPreviousVersion() {
    return previousVersion;
  }

  /** When the launch was recorded, in epoch milliseconds. */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Classifies this launch and records the version for the next one. A store that fails counts as
   * having nothing recorded: the launch is reported as an install rather than not at all.
   *
   * @param version the application version, or null if unknown
   * @param build the build identifier, or null
   */
  static AppLaunch resolve(String version, String build, VersionStore store, long timestamp) {
    if (version == null) {
      return new AppLaunch(LaunchType.UNKNOWN, null, build, null, timestamp);
    }
    String stored;
    try {
      stored = store.read();
    } catch (RuntimeException e) {
      stored = null;
    }
    try {
      store.write(version);
    } catch (RuntimeException e) {
      // The next launch is classified as an install again; nothing else depends on it.
    }
    if (stored == null) {
      return new AppLaunch(LaunchType.INSTALL, version, build, null, timestamp);
    }
    if (!stored.equals(version)) {
      return new AppLaunch(LaunchType.UPDATE, version, build, stored, timestamp);
    }
    return new AppLaunch(LaunchType.RELAUNCH, version, build, null, timestamp);
  }

  /** When the JVM started, in epoch milliseconds, or null if it cannot be told. */
  static Long jvmStartMillis() {
    try {
      return ManagementFactory.getRuntimeMXBean().getStartTime();
    } catch (RuntimeException | LinkageError e) {
      return null;
    }
  }

  /**
   * Keeps the last version in the user's Java preferences, under a key for the application, so two
   * applications on one machine do not overwrite each other's record.
   */
  public static final class PreferencesVersionStore implements VersionStore {
    private final Preferences node;
    private final String key;

    PreferencesVersionStore(String applicationKey) {
      this(Preferences.userRoot().node("com/launchdarkly/observability"), applicationKey);
    }

    PreferencesVersionStore(Preferences node, String applicationKey) {
      this.node = node;
      this.key = "lastAppVersion." + digest(applicationKey);
    }

    @Override
    public String read() {
      return node.get(key, null);
    }

    @Override
    public void write(String version) {
      node.put(key, version);
    }

    /** Preference keys are limited to 80 characters, so the application is identified by a digest. */
    public static String digest(String value) {
      try {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 16; i++) {
          hex.append(String.format("%02x", hash[i]));
        }
        return hex.toString();
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e); // every JVM has SHA-256
      }
    }
  }
}
