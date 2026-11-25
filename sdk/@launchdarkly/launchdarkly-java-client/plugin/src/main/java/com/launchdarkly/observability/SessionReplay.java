package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Version;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.Plugin;
import com.launchdarkly.sdk.client.integrations.PluginMetadata;
import com.launchdarkly.sdk.client.integrations.RegistrationCompleteResult;

import java.util.Objects;

/**
 * The LaunchDarkly session replay plugin: records what the application's Swing, AWT and JavaFX
 * windows show, and the mouse over them, so a session can be watched back in LaunchDarkly alongside
 * its errors, logs and traces. It needs the {@link Observability} plugin, which provides the session,
 * and adds nothing on its own:
 *
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *         .mobileKey(mobileKey)
 *         .plugins(Components.plugins().setPlugins(Arrays.asList(
 *             new Observability(ObservabilityOptions.builder().serviceName("my-desktop-app").build()),
 *             new SessionReplay(ReplayOptions.builder().build()))))
 *         .build();
 * </code></pre>
 * <p>
 * Each frame is masked in the application before it is encoded, as the {@link PrivacyProfile} says:
 * by default, text inputs. Only the parts of a frame that changed are sent. Replay takes frames
 * only while one of the application's windows is active, and never starts a UI toolkit itself.
 */
public final class SessionReplay extends Plugin {
  static final String PLUGIN_NAME = "@launchdarkly/session-replay-java-client";

  private final ReplayOptions options;

  /** Creates the plugin with the default options. */
  public SessionReplay() {
    this(ReplayOptions.builder().build());
  }

  /**
   * Creates the plugin.
   *
   * @param options the options
   */
  public SessionReplay(ReplayOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  @Override
  public PluginMetadata getMetadata() {
    return new PluginMetadata() {
      @Override
      public String getName() {
        return PLUGIN_NAME;
      }

      @Override
      public String getVersion() {
        return Version.VERSION;
      }
    };
  }

  /** Nothing: replay installs once every plugin has registered, so the order of plugins does not matter. */
  @Override
  public void register(LDClient client, EnvironmentMetadata metadata) {}

  /** Installs replay for the observability service of the client's credential. */
  @Override
  public void onPluginsReady(RegistrationCompleteResult result, EnvironmentMetadata metadata) {
    LDReplay.install(metadata.getCredential(), options);
  }
}
