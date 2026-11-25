package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;
import com.launchdarkly.observability.internal.ObservabilityHook;
import com.launchdarkly.observability.internal.ObservabilityRuntime;
import com.launchdarkly.observability.internal.Version;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.integrations.DedupingHook;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.Hook;
import com.launchdarkly.sdk.client.integrations.Plugin;
import com.launchdarkly.sdk.client.integrations.PluginMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The LaunchDarkly Observability plugin for the client-side Java SDK. Add it to the SDK's
 * configuration, and it records flag evaluations, identifies, custom events, errors and crashes,
 * and exports them with anything recorded through {@link LDObserve} to LaunchDarkly over OTLP:
 *
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *         .mobileKey(mobileKey)
 *         .plugins(Components.plugins().setPlugins(Collections.singletonList(
 *             new Observability(ObservabilityOptions.builder()
 *                 .serviceName("my-desktop-app")
 *                 .serviceVersion("1.4.2")
 *                 .build()))))
 *         .build();
 *     LDClient client = LDClient.init(config, context, 5);
 * </code></pre>
 * <p>
 * Telemetry is filed under the LaunchDarkly project of the SDK's credential. A flag evaluated again
 * with the same result is reported once every ten minutes, and again after each identify, through
 * the SDK's {@link DedupingHook}; analytics events are unaffected.
 */
public final class Observability extends Plugin {
  private final ObservabilityOptions options;

  /** Creates the plugin with the default options. */
  public Observability() {
    this(ObservabilityOptions.builder().build());
  }

  /**
   * Creates the plugin.
   *
   * @param options the options
   */
  public Observability(ObservabilityOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  @Override
  public PluginMetadata getMetadata() {
    return new PluginMetadata() {
      @Override
      public String getName() {
        return Constants.PLUGIN_NAME;
      }

      @Override
      public String getVersion() {
        return Version.VERSION;
      }
    };
  }

  @Override
  public List<Hook> getHooks(EnvironmentMetadata metadata) {
    if (!options.isEnabled()) {
      return Collections.emptyList();
    }
    // One hook per client, bound to its credential. It looks the service up on every call, so it
    // follows LDObserve.shutdown() and a later start, and records nothing while observability runs
    // for another credential.
    String credential = metadata.getCredential();
    return Collections.<Hook>singletonList(new DedupingHook(new ObservabilityHook(
        () -> ObservabilityRuntime.currentFor(credential))));
  }

  /** Starts observability for the client's credential, unless it is already running. */
  @Override
  public void register(LDClient client, EnvironmentMetadata metadata) {
    if (options.isEnabled()) {
      ObservabilityRuntime.start(metadata.getCredential(), options, metadata);
    }
  }
}
