package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.TestRuntime;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.client.Components;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDConfig;
import com.launchdarkly.sdk.client.integrations.Plugin;
import com.launchdarkly.sdk.client.integrations.TestData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The public session replay API: {@link LDReplay}, {@link SessionReplay} and their options. */
public class SessionReplayTest {
  private final List<LDClient> clients = new ArrayList<>();

  @Before
  public void setUp() {
    LDReplay.resetForTest();
    TestRuntime.useMemory();
  }

  @After
  public void tearDown() throws Exception {
    for (LDClient client : clients) {
      client.close();
    }
    LDReplay.resetForTest();
    TestRuntime.reset();
  }

  private void observe() {
    LDObserve.init(TestRuntime.CREDENTIAL, TestRuntime.quietOptions().build());
  }

  private LDClient client(Plugin... plugins) throws Exception {
    LDConfig config = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Disabled)
        .mobileKey(TestRuntime.CREDENTIAL)
        .dataSource(TestData.dataSource())
        .events(Components.noEvents())
        .disablePersistence()
        .diagnosticOptOut(true)
        .plugins(Components.plugins().setPlugins(Arrays.asList(plugins)))
        .build();
    LDClient client = LDClient.create(config, LDContext.create("user-1"));
    clients.add(client);
    client.start(java.time.Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);
    return client;
  }

  // -- LDReplay --

  @Test
  public void beforeInstallTheStateIsRemembered() {
    assertFalse(LDReplay.isEnabled());
    LDReplay.setEnabled(true);
    assertTrue(LDReplay.isEnabled());
    LDReplay.stop();
    assertFalse(LDReplay.isEnabled());
    assertEquals(SessionReplayStartResult.UNAVAILABLE, LDReplay.start());
    assertTrue(LDReplay.isEnabled());
    LDReplay.flush(); // nothing to send
  }

  @Test
  public void initWithoutObservabilityInstallsNothing() {
    LDReplay.init(ReplayOptions.builder().build());
    assertEquals(SessionReplayStartResult.UNAVAILABLE, LDReplay.start());
  }

  @Test
  public void initInstallsReplayOnRunningObservability() {
    observe();
    LDReplay.init(ReplayOptions.builder().build());
    assertTrue(LDReplay.isEnabled());
    assertEquals(SessionReplayStartResult.ALREADY_STARTED, LDReplay.start());
    LDReplay.stop();
    assertFalse(LDReplay.isEnabled());
    assertEquals(SessionReplayStartResult.STARTED, LDReplay.start(true));
    LDReplay.setEnabled(false);
    assertFalse(LDReplay.isEnabled());
    LDReplay.flush();

    LDReplay.init(ReplayOptions.builder().build());
    assertThat(TestRuntime.logs(), containsString("already installed"));
  }

  @Test
  public void replayInstallsWithTheLaunchRecorded() {
    LDObserve.init(TestRuntime.CREDENTIAL, TestRuntime.quietOptions()
        .analytics(ObservabilityOptions.Analytics.builder().appLaunch(true).build()).build());
    LDReplay.init(ReplayOptions.builder().build());
    assertTrue(LDReplay.isEnabled());
  }

  @Test
  public void replayStopsWithObservability() {
    observe();
    LDReplay.init(ReplayOptions.builder().build());
    LDObserve.shutdown();
    assertEquals("uninstalled, so the start is remembered", SessionReplayStartResult.UNAVAILABLE, LDReplay.start());
  }

  @Test
  public void aRememberedStopWinsOverTheOptions() {
    LDReplay.setEnabled(false);
    observe();
    LDReplay.init(ReplayOptions.builder().build());
    assertFalse(LDReplay.isEnabled());
  }

  @Test
  public void aRememberedStartWinsOverTheOptionsAndCanIgnoreSampling() {
    LDReplay.start(true);
    observe();
    LDReplay.init(ReplayOptions.builder().enabled(false).sampleRate(0).build());
    assertTrue(LDReplay.isEnabled());
    assertEquals("recording, though sampled out", SessionReplayStartResult.ALREADY_STARTED, LDReplay.start());
  }

  @Test
  public void aRememberedEnableWinsOverTheOptions() {
    LDReplay.setEnabled(true);
    observe();
    LDReplay.init(ReplayOptions.builder().enabled(false).build());
    assertTrue(LDReplay.isEnabled());
  }

  @Test
  public void aRememberedStartHasSamplingDecide() {
    LDReplay.start();
    observe();
    LDReplay.init(ReplayOptions.builder().enabled(false).sampleRate(0).build());
    assertTrue("enabled, though sampled out", LDReplay.isEnabled());
    assertEquals(SessionReplayStartResult.SAMPLED_OUT, LDReplay.start());
  }

  @Test
  public void replayThatCannotStartRecordsNothing() {
    LDReplay.factory = (service, options) -> {
      throw new NoClassDefFoundError("okhttp3/OkHttpClient");
    };
    observe();
    LDReplay.init(ReplayOptions.builder().build());
    assertThat(TestRuntime.logs(), containsString("could not start"));
    assertEquals(SessionReplayStartResult.UNAVAILABLE, LDReplay.start());
  }

  @Test
  public void theMarksHaveFixedNames() {
    assertEquals("launchdarkly.replay.mask", LDReplay.MASK_PROPERTY);
    assertEquals("ld-mask", LDReplay.MASK_STYLE_CLASS);
    assertEquals("ld-unmask", LDReplay.UNMASK_STYLE_CLASS);
  }

  @Test(expected = NullPointerException.class)
  public void aMarkNeedsAComponent() {
    LDReplay.mask(null);
  }

  // -- the plugin --

  @Test
  public void thePluginInstallsReplayWhateverItsPlaceAmongThePlugins() throws Exception {
    client(new SessionReplay(), new Observability(TestRuntime.quietOptions().build()));
    assertTrue(LDReplay.isEnabled());
    assertEquals(SessionReplayStartResult.ALREADY_STARTED, LDReplay.start());
  }

  @Test
  public void thePluginNeedsObservability() throws Exception {
    client(new SessionReplay(ReplayOptions.builder().build()));
    assertEquals(SessionReplayStartResult.UNAVAILABLE, LDReplay.start());
  }

  @Test
  public void theMetadataNamesThePlugin() {
    SessionReplay plugin = new SessionReplay();
    assertEquals("@launchdarkly/session-replay-java-client", plugin.getMetadata().getName());
    assertEquals(com.launchdarkly.observability.internal.Version.VERSION, plugin.getMetadata().getVersion());
  }

  @Test
  public void startResultsSayWhetherReplayRuns() {
    assertTrue(SessionReplayStartResult.STARTED.isRunning());
    assertTrue(SessionReplayStartResult.ALREADY_STARTED.isRunning());
    assertFalse(SessionReplayStartResult.SAMPLED_OUT.isRunning());
    assertFalse(SessionReplayStartResult.UNAVAILABLE.isRunning());
    assertFalse(SessionReplayStartResult.UNRECOVERABLE_ERROR.isRunning());
  }

  // -- options --

  @Test
  public void theDefaultsAreAndroids() {
    ReplayOptions options = ReplayOptions.builder().build();
    assertTrue(options.isEnabled());
    assertEquals(1, options.getSampleRate(), 0);
    assertEquals(1, options.getFrameRate(), 0);
    assertEquals(1, options.getScale(), 0);
    assertEquals(0.3, options.getImageQuality(), 0);
    PrivacyProfile profile = options.getPrivacyProfile();
    assertTrue(profile.isMaskTextInputs());
    assertFalse(profile.isMaskText());
    assertFalse(profile.isMaskImages());
    assertTrue(profile.getMaskNames().isEmpty());
    assertTrue(profile.getUnmaskNames().isEmpty());
    assertTrue(profile.getMaskClasses().isEmpty());
    assertTrue(profile.getUnmaskClasses().isEmpty());
    assertFalse(profile.isMaskWebViews());
    assertFalse(profile.isMaskBySemanticsKeywords());
    assertEquals(0.02, profile.getMinimumAlpha(), 0);
    assertEquals(ReplayOptions.CompressionMethod.overlayTiles(), options.getCompression());
    assertFalse(options.isDebug());
    assertThat(options.toString(), containsString("maskTextInputs=true"));
    assertThat(options.toString(), containsString("compression=overlayTiles(15, true)"));
  }

  @Test
  public void compressionIsAndroids() {
    ReplayOptions.CompressionMethod tiles = ReplayOptions.CompressionMethod.overlayTiles();
    assertFalse(tiles.isScreenImage());
    assertEquals(15, tiles.getLayers());
    assertTrue(tiles.isBacktracking());
    assertEquals(tiles, ReplayOptions.CompressionMethod.overlayTiles(15, true));
    assertEquals(tiles.hashCode(), ReplayOptions.CompressionMethod.overlayTiles(15, true).hashCode());
    assertFalse(tiles.equals(ReplayOptions.CompressionMethod.overlayTiles(15, false)));
    assertFalse(tiles.equals(ReplayOptions.CompressionMethod.overlayTiles(10, true)));
    assertFalse(tiles.equals("overlayTiles"));

    ReplayOptions.CompressionMethod screen = ReplayOptions.CompressionMethod.screenImage();
    assertTrue(screen.isScreenImage());
    assertEquals(0, screen.getLayers());
    assertFalse(screen.isBacktracking());
    assertFalse(screen.equals(tiles));
    assertNotEquals(screen.hashCode(), tiles.hashCode());
    assertEquals("screenImage", screen.toString());

    ReplayOptions options = ReplayOptions.builder().compression(screen).debug(true).build();
    assertEquals(screen, options.getCompression());
    assertTrue(options.isDebug());
    assertThat(options.toString(), containsString("debug=true"));
  }

  @Test(expected = NullPointerException.class)
  public void aCompressionIsRequired() {
    ReplayOptions.builder().compression(null);
  }

  @Test
  public void aProfileTakesAndroidsOtherSwitches() {
    PrivacyProfile profile = PrivacyProfile.builder().maskWebViews(true).maskBySemanticsKeywords(true)
        .minimumAlpha(0.25).addUnmaskClass(javax.swing.JLabel.class).addUnmaskClass("app.Help").build();
    assertTrue(profile.isMaskWebViews());
    assertTrue(profile.isMaskBySemanticsKeywords());
    assertEquals(0.25, profile.getMinimumAlpha(), 0);
    assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("javax.swing.JLabel", "app.Help")),
        profile.getUnmaskClasses());
    assertThat(profile.toString(), containsString("maskWebViews=true"));
    assertThat(profile.toString(), containsString("unmaskClasses=[javax.swing.JLabel, app.Help]"));
    assertEquals(0, PrivacyProfile.builder().minimumAlpha(-1).build().getMinimumAlpha(), 0);
    assertEquals(1, PrivacyProfile.builder().minimumAlpha(2).build().getMinimumAlpha(), 0);
    assertEquals(0.02, PrivacyProfile.builder().minimumAlpha(Double.NaN).build().getMinimumAlpha(), 0);
  }

  @Test(expected = NullPointerException.class)
  public void anUnmaskClassIsRequired() {
    PrivacyProfile.builder().addUnmaskClass((Class<?>) null);
  }

  @Test(expected = NullPointerException.class)
  public void anUnmaskClassNameIsRequired() {
    PrivacyProfile.builder().addUnmaskClass((String) null);
  }

  @Test
  public void optionsAreKeptInRange() {
    ReplayOptions low = ReplayOptions.builder().sampleRate(-1).frameRate(0).scale(0).imageQuality(-1).build();
    assertEquals(0, low.getSampleRate(), 0);
    assertEquals(0.1, low.getFrameRate(), 0);
    assertEquals(0.1, low.getScale(), 0);
    assertEquals(0, low.getImageQuality(), 0);
    ReplayOptions high = ReplayOptions.builder().sampleRate(2).frameRate(100).scale(9).imageQuality(2).build();
    assertEquals(1, high.getSampleRate(), 0);
    assertEquals(10, high.getFrameRate(), 0);
    assertEquals(4, high.getScale(), 0);
    assertEquals(1, high.getImageQuality(), 0);
    ReplayOptions nan = ReplayOptions.builder().sampleRate(Double.NaN).frameRate(Double.NaN).scale(Double.NaN)
        .imageQuality(Double.NaN).build();
    assertEquals(1, nan.getSampleRate(), 0);
    assertEquals(1, nan.getFrameRate(), 0);
    assertEquals(1, nan.getScale(), 0);
    assertEquals(0.3, nan.getImageQuality(), 0);
  }

  @Test
  public void theStrictProfileMasksText() {
    PrivacyProfile strict = PrivacyProfile.strict();
    assertTrue(strict.isMaskText());
    assertTrue(strict.isMaskTextInputs());
    ReplayOptions options = ReplayOptions.builder().privacyProfile(strict).build();
    assertTrue(options.getPrivacyProfile().isMaskText());
  }

  @Test
  public void aProfileNamesWhatToMask() {
    PrivacyProfile profile = PrivacyProfile.builder()
        .addMaskName("account")
        .addUnmaskName("help")
        .addMaskClass(javax.swing.JTree.class)
        .addMaskClass("javafx.scene.control.TreeView")
        .build();
    assertEquals(java.util.Collections.singleton("account"), profile.getMaskNames());
    assertEquals(java.util.Collections.singleton("help"), profile.getUnmaskNames());
    assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("javax.swing.JTree", "javafx.scene.control.TreeView")),
        profile.getMaskClasses());
    assertThat(profile.toString(), containsString("maskNames=[account]"));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void aProfileCannotBeChanged() {
    PrivacyProfile.defaults().getMaskNames().add("x");
  }

  @Test(expected = NullPointerException.class)
  public void aProfileIsRequired() {
    ReplayOptions.builder().privacyProfile(null);
  }

  @Test(expected = NullPointerException.class)
  public void aNameIsRequired() {
    PrivacyProfile.builder().addMaskName(null);
  }

  @Test(expected = NullPointerException.class)
  public void anUnmaskNameIsRequired() {
    PrivacyProfile.builder().addUnmaskName(null);
  }

  @Test(expected = NullPointerException.class)
  public void aClassIsRequired() {
    PrivacyProfile.builder().addMaskClass((Class<?>) null);
  }

  @Test(expected = NullPointerException.class)
  public void aClassNameIsRequired() {
    PrivacyProfile.builder().addMaskClass((String) null);
  }

  @Test(expected = NullPointerException.class)
  public void replayOptionsAreRequired() {
    new SessionReplay(null);
  }

  @Test(expected = NullPointerException.class)
  public void initNeedsOptions() {
    LDReplay.init(null);
  }
}
