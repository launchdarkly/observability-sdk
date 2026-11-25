package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.AppLaunch;

import java.util.prefs.Preferences;

/**
 * Remembers, between launches, that the backend refused session replay for a credential, as
 * Android does: the next launch then takes no screenshots until the backend has accepted its
 * session, so a project the backend will not record from costs no capturing.
 */
interface RefusalStore {
  /** The reason the last launch was refused, or null if it was not. */
  String load();

  void store(String reason);

  void clear();

  /** A store that remembers nothing. */
  RefusalStore NONE = new RefusalStore() {
    @Override
    public String load() {
      return null;
    }

    @Override
    public void store(String reason) {}

    @Override
    public void clear() {}
  };

  /** Keeps the refusal in the user's Java preferences, under a key for the credential. */
  final class InPreferences implements RefusalStore {
    static final String NODE = "/com/launchdarkly/observability/replay";
    private static final int MAX_REASON_LENGTH = 1024;

    private final Preferences node;
    private final String key;

    InPreferences(String credential) {
      this(Preferences.userRoot().node(NODE), credential);
    }

    InPreferences(Preferences node, String credential) {
      this.node = node;
      // A digest, so the credential itself is not written to disk.
      this.key = "refused." + AppLaunch.PreferencesVersionStore.digest(credential);
    }

    @Override
    public String load() {
      try {
        return node.get(key, null);
      } catch (RuntimeException e) {
        return null; // a store that cannot be read remembers nothing
      }
    }

    @Override
    public void store(String reason) {
      String value = reason == null ? "" : reason;
      try {
        node.put(key, value.length() > MAX_REASON_LENGTH ? value.substring(0, MAX_REASON_LENGTH) : value);
        node.flush();
      } catch (Exception e) {
        // The next launch takes screenshots until refused again; nothing else depends on it.
      }
    }

    @Override
    public void clear() {
      try {
        node.remove(key);
        node.flush();
      } catch (Exception e) {
        // As above.
      }
    }
  }
}
