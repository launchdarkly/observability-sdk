package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.SdkMetadata;
import com.launchdarkly.sdk.client.subsystems.ApplicationInfo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UnitsTest {
  @After
  public void tearDown() {
    GlobalAttributes.clearForTest();
  }

  // -- sessions --

  @Test
  public void aSessionLastsUntilItsMaximumLifetime() {
    AtomicLong clock = new AtomicLong(1000);
    SessionManager sessions = new SessionManager(100, 1_000_000, clock::get, new Random(7)); // DevSkim: ignore DS148264
    String first = sessions.getSessionId();
    assertTrue(first.matches("[0-9a-f]{32}"));
    clock.set(1099);
    assertEquals(first, sessions.getSessionId());
    clock.set(1100);
    String second = sessions.getSessionId();
    assertNotEquals(first, second);
    clock.set(1050);
    assertNotEquals("a clock set backwards ends the session", second, sessions.getSessionId());
  }

  @Test
  public void theDefaultSessionManagerMakesIds() {
    assertTrue(new SessionManager(1000).getSessionId().matches("[0-9a-f]{32}"));
  }

  // -- global attributes --

  @Test
  public void globalAttributesReplaceByNameWhateverTheType() {
    GlobalAttributes.set(Attributes.builder().put("tier", "gold").put("keep", true).build());
    GlobalAttributes.set(Attributes.builder().put("tier", 3L).build());
    assertNull(GlobalAttributes.get().get(AttributeKey.stringKey("tier")));
    assertEquals(Long.valueOf(3), GlobalAttributes.get().get(AttributeKey.longKey("tier")));
    assertEquals(Boolean.TRUE, GlobalAttributes.get().get(AttributeKey.booleanKey("keep")));
  }

  @Test
  public void sessionKeysAreReserved() {
    Set<String> dropped = GlobalAttributes.set(Attributes.builder().put("session.id", "x")
        .put("highlight.session_id", "y").build());
    assertEquals(2, dropped.size());
    assertTrue(GlobalAttributes.get().isEmpty());
  }

  @Test
  public void emptyAndNullAreIgnored() {
    assertThat(GlobalAttributes.set(null), empty());
    assertThat(GlobalAttributes.set(Attributes.empty()), empty());
    GlobalAttributes.remove(null);
    GlobalAttributes.remove(Collections.<String>emptyList());
    assertTrue(GlobalAttributes.get().isEmpty());
  }

  @Test
  public void mergeKeepsTheCallersValues() {
    Attributes own = Attributes.builder().put("a", "own").build();
    assertSame(own, GlobalAttributes.merge(own));
    assertTrue(GlobalAttributes.merge(null).isEmpty());
    GlobalAttributes.set(Attributes.builder().put("a", "global").put("b", "global").build());
    Attributes merged = GlobalAttributes.merge(own);
    assertEquals("own", merged.get(AttributeKey.stringKey("a")));
    assertEquals("global", merged.get(AttributeKey.stringKey("b")));
    assertEquals(GlobalAttributes.get(), GlobalAttributes.merge(Attributes.empty()));
    GlobalAttributes.remove(Arrays.asList("a", "missing"));
    assertEquals(1, GlobalAttributes.get().size());
  }

  // -- attribute conversion --

  @Test
  public void plainValuesBecomeTypedAttributes() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("id", "u-1");
    Map<String, Object> item = new HashMap<>();
    item.put("price", 2.5);
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("flag", true);
    source.put("byte", (byte) 1);
    source.put("short", (short) 2);
    source.put("int", 3);
    source.put("long", 4L);
    source.put("float", 1.5f);
    source.put("double", 2.5);
    source.put("text", "hi");
    source.put("user", nested);
    source.put("strings", Arrays.asList("a", "b"));
    source.put("booleans", Arrays.asList(true, false));
    source.put("longs", Arrays.asList(1, 2L));
    source.put("doubles", Arrays.asList(1, 2.5));
    source.put("items", Arrays.asList(item, "x"));
    source.put("empty", Collections.emptyList());
    source.put("skipped", new Object());
    source.put("nothing", null);
    source.put(null, "no key");
    source.put("attrs", Attributes.builder().put("s", "v").put("b", true).put("l", 1L).put("d", 1.0)
        .put(AttributeKey.stringArrayKey("sa"), Arrays.asList("x"))
        .put(AttributeKey.booleanArrayKey("ba"), Arrays.asList(true))
        .put(AttributeKey.longArrayKey("la"), Arrays.asList(1L))
        .put(AttributeKey.doubleArrayKey("da"), Arrays.asList(1.0)).build());

    Attributes a = AttributeConversion.fromMap(source);
    assertEquals(Boolean.TRUE, a.get(AttributeKey.booleanKey("flag")));
    assertEquals(Long.valueOf(1), a.get(AttributeKey.longKey("byte")));
    assertEquals(Long.valueOf(2), a.get(AttributeKey.longKey("short")));
    assertEquals(Long.valueOf(3), a.get(AttributeKey.longKey("int")));
    assertEquals(Long.valueOf(4), a.get(AttributeKey.longKey("long")));
    assertEquals(Double.valueOf(1.5), a.get(AttributeKey.doubleKey("float")));
    assertEquals(Double.valueOf(2.5), a.get(AttributeKey.doubleKey("double")));
    assertEquals("hi", a.get(AttributeKey.stringKey("text")));
    assertEquals("u-1", a.get(AttributeKey.stringKey("user.id")));
    assertEquals(Arrays.asList("a", "b"), a.get(AttributeKey.stringArrayKey("strings")));
    assertEquals(Arrays.asList(true, false), a.get(AttributeKey.booleanArrayKey("booleans")));
    assertEquals(Arrays.asList(1L, 2L), a.get(AttributeKey.longArrayKey("longs")));
    assertEquals(Arrays.asList(1.0, 2.5), a.get(AttributeKey.doubleArrayKey("doubles")));
    assertEquals(Double.valueOf(2.5), a.get(AttributeKey.doubleKey("items.0.price")));
    assertEquals("x", a.get(AttributeKey.stringKey("items.1")));
    assertEquals("v", a.get(AttributeKey.stringKey("attrs.s")));
    assertEquals(Boolean.TRUE, a.get(AttributeKey.booleanKey("attrs.b")));
    assertEquals(Long.valueOf(1), a.get(AttributeKey.longKey("attrs.l")));
    assertEquals(Double.valueOf(1.0), a.get(AttributeKey.doubleKey("attrs.d")));
    assertEquals(Arrays.asList("x"), a.get(AttributeKey.stringArrayKey("attrs.sa")));
    assertEquals(Arrays.asList(true), a.get(AttributeKey.booleanArrayKey("attrs.ba")));
    assertEquals(Arrays.asList(1L), a.get(AttributeKey.longArrayKey("attrs.la")));
    assertEquals(Arrays.asList(1.0), a.get(AttributeKey.doubleArrayKey("attrs.da")));
    assertEquals("nothing else", 23, a.size());
    assertTrue(AttributeConversion.fromMap(null).isEmpty());
    assertTrue(AttributeConversion.fromMap(Collections.<String, Object>emptyMap()).isEmpty());
  }

  @Test
  public void aMapThatContainsItselfStopsAtTheDepthLimit() {
    Map<String, Object> loop = new HashMap<>();
    loop.put("name", "x");
    loop.put("self", loop);
    List<Object> list = new ArrayList<>();
    list.add(list);
    loop.put("list", list);
    Attributes a = AttributeConversion.fromMap(loop);
    assertEquals("x", a.get(AttributeKey.stringKey("name")));
    assertEquals("x", a.get(AttributeKey.stringKey("self.self.name")));
    StringBuilder deepest = new StringBuilder();
    for (int i = 0; i < AttributeConversion.MAX_DEPTH; i++) {
      deepest.append("self.");
    }
    assertEquals("x", a.get(AttributeKey.stringKey(deepest + "name")));
    assertNull(a.get(AttributeKey.stringKey(deepest + "self.name")));
  }

  @Test
  public void trackDataKeepsScalarMembers() {
    Attributes a = AttributeConversion.fromLDValue(LDValue.buildObject().put("b", true).put("n", 2).put("s", "x")
        .put("null", LDValue.ofNull()).put("array", LDValue.buildArray().add(1).build()).build());
    assertEquals(3, a.size());
    assertEquals(Double.valueOf(2), a.get(AttributeKey.doubleKey("n")));
    assertTrue(AttributeConversion.fromLDValue(null).isEmpty());
    assertTrue(AttributeConversion.fromLDValue(LDValue.of("scalar")).isEmpty());
  }

  // -- crash reporting --

  @Test
  public void withNoEarlierHandlerACrashIsPrintedAsTheJvmWould() {
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    try {
      Thread.setDefaultUncaughtExceptionHandler(null);
      List<Throwable> recorded = new ArrayList<>();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CrashReporter reporter = CrashReporter.install((t, e) -> recorded.add(e),
          new PrintStream(out, true));
      RuntimeException crash = new RuntimeException("boom");
      reporter.uncaughtException(Thread.currentThread(), crash);
      assertThat(recorded, contains(crash));
      String printed = new String(out.toByteArray(), StandardCharsets.UTF_8);
      assertThat(printed, containsString("Exception in thread \"" + Thread.currentThread().getName() + "\""));
      assertThat(printed, containsString("boom"));
      reporter.uninstall();
      assertNull(Thread.getDefaultUncaughtExceptionHandler());
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  @Test
  public void aFailingRecorderDoesNotStopTheHandlerBehind() {
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    try {
      List<Throwable> passedOn = new ArrayList<>();
      Thread.setDefaultUncaughtExceptionHandler((t, e) -> passedOn.add(e));
      CrashReporter reporter = CrashReporter.install((t, e) -> {
        throw new IllegalStateException("recorder broke");
      });
      RuntimeException crash = new RuntimeException();
      reporter.uncaughtException(Thread.currentThread(), crash);
      assertThat(passedOn, contains(crash));
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  @Test
  public void anUninstalledReporterBehindAnotherHandlerStaysButStopsRecording() {
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    try {
      Thread.setDefaultUncaughtExceptionHandler(null);
      List<Throwable> recorded = new ArrayList<>();
      CrashReporter reporter = CrashReporter.install((t, e) -> recorded.add(e),
          new PrintStream(new ByteArrayOutputStream(), true));
      Thread.UncaughtExceptionHandler later = (t, e) -> { };
      Thread.setDefaultUncaughtExceptionHandler(later);
      reporter.uninstall();
      assertSame("a handler installed later is left alone", later, Thread.getDefaultUncaughtExceptionHandler());
      reporter.uncaughtException(Thread.currentThread(), new RuntimeException());
      assertThat(recorded, empty());
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  // -- launch history --

  @Test
  public void aFailingStoreCountsAsNothingRecorded() {
    TestTelemetry.MemoryVersionStore store = new TestTelemetry.MemoryVersionStore();
    store.readFailure = new IllegalStateException("no preferences");
    store.writeFailure = new IllegalStateException("read-only");
    AppLaunch launch = AppLaunch.resolve("1.0", null, store, 0);
    assertEquals(AppLaunch.LaunchType.INSTALL, launch.launchType);
  }

  @Test
  public void thePreferencesStoreKeepsOneVersionPerApplication() throws Exception {
    Preferences node = Preferences.userRoot().node("com/launchdarkly/observability/test-" + System.nanoTime());
    try {
      AppLaunch.PreferencesVersionStore first = new AppLaunch.PreferencesVersionStore(node, "app-one");
      AppLaunch.PreferencesVersionStore second = new AppLaunch.PreferencesVersionStore(node, "app-two");
      assertNull(first.read());
      first.write("1.0");
      assertEquals("1.0", first.read());
      assertNull(second.read());
      assertEquals(32, AppLaunch.PreferencesVersionStore.digest("app-one").length());
      assertEquals(AppLaunch.LaunchType.RELAUNCH, AppLaunch.resolve("1.0", null, first, 0).launchType);
    } finally {
      node.removeNode();
    }
  }

  @Test
  public void theDefaultPreferencesStoreUsesTheUserRoot() {
    AppLaunch.PreferencesVersionStore store = new AppLaunch.PreferencesVersionStore("app-" + System.nanoTime());
    assertNull(store.read());
  }

  @Test
  public void theJvmStartIsKnown() {
    Long start = AppLaunch.jvmStartMillis();
    assertTrue(start != null && start <= System.currentTimeMillis());
  }

  // -- resource --

  @Test
  public void theOperatingSystemAndRuntimeAreDescribed() {
    Properties system = new Properties();
    system.setProperty("os.name", "Mac OS X");
    system.setProperty("os.version", "15.1");
    system.setProperty("os.arch", "aarch64");
    system.setProperty("java.runtime.name", "OpenJDK Runtime Environment");
    system.setProperty("java.runtime.version", "21.0.5+11");
    system.setProperty("java.vm.vendor", "Eclipse Adoptium");
    system.setProperty("java.vm.name", "OpenJDK 64-Bit Server VM");
    system.setProperty("java.vm.version", "21.0.5+11");
    Attributes a = ObservabilityResource.attributes("key", ObservabilityOptions.builder().build(), null, system);
    assertEquals("darwin", a.get(AttributeKey.stringKey("os.type")));
    assertEquals("15.1", a.get(AttributeKey.stringKey("os.version")));
    assertEquals("Mac OS X 15.1", a.get(AttributeKey.stringKey("os.description")));
    assertEquals("arm64", a.get(AttributeKey.stringKey("host.arch")));
    assertEquals("OpenJDK Runtime Environment", a.get(AttributeKey.stringKey("process.runtime.name")));
    assertEquals("Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.5+11",
        a.get(AttributeKey.stringKey("process.runtime.description")));
    assertEquals(Constants.DEFAULT_SERVICE_NAME, a.get(AttributeKey.stringKey("service.name")));
    assertEquals(Version.VERSION, a.get(AttributeKey.stringKey("service.version")));
    assertEquals(Version.VERSION, a.get(AttributeKey.stringKey("telemetry.distro.version")));
  }

  @Test
  public void missingSystemPropertiesAreLeftOut() {
    Attributes a = ObservabilityResource.attributes("key", ObservabilityOptions.builder().build(),
        new EnvironmentMetadata(new ApplicationInfo(null, ""), new SdkMetadata("JavaClient", null, null, null), "key"),
        new Properties());
    for (String key : Arrays.asList("os.type", "os.version", "os.description", "host.arch", "process.runtime.name",
        "process.runtime.description", "launchdarkly.application.id", "launchdarkly.application.version",
        "launchdarkly.sdk.version")) {
      assertNull(key, a.get(AttributeKey.stringKey(key)));
    }
    Properties osOnly = new Properties();
    osOnly.setProperty("os.name", "Linux");
    osOnly.setProperty("java.vm.vendor", "Vendor");
    assertEquals("Linux", ObservabilityResource.attributes("key", ObservabilityOptions.builder().build(), null,
        osOnly).get(AttributeKey.stringKey("os.description")));
    assertEquals(null, ObservabilityResource.attributes("key", ObservabilityOptions.builder().build(),
        new EnvironmentMetadata(null, new SdkMetadata(null, "1", null, null), "key"), osOnly)
        .get(AttributeKey.stringKey("launchdarkly.sdk.version")));
  }

  @Test
  public void userResourceAttributesCannotReplaceTheServiceIdentity() {
    Attributes a = ObservabilityResource.attributes("key", ObservabilityOptions.builder()
        .serviceName("svc")
        .resourceAttributes(Attributes.builder().put("service.name", "spoof").put("team", "desktop")
            .put("highlight.project_id", "override").build())
        .build(), null, new Properties());
    assertEquals("svc", a.get(AttributeKey.stringKey("service.name")));
    assertEquals("desktop", a.get(AttributeKey.stringKey("team")));
    assertEquals("override", a.get(AttributeKey.stringKey("highlight.project_id")));
  }

  @Test
  public void osTypesAndArchitecturesFollowTheSemanticConventions() {
    String[][] os = {
        {"Windows 11", "windows"}, {"Mac OS X", "darwin"}, {"Darwin", "darwin"}, {"Linux", "linux"},
        {"FreeBSD", "freebsd"}, {"SunOS", "solaris"}, {"Solaris", "solaris"}, {"AIX", "aix"}, {"Plan 9", null},
    };
    for (String[] pair : os) {
      assertEquals(pair[0], pair[1], ObservabilityResource.osType(pair[0]));
    }
    assertNull(ObservabilityResource.osType(null));
    String[][] arch = {
        {"amd64", "amd64"}, {"x86_64", "amd64"}, {"aarch64", "arm64"}, {"arm64", "arm64"}, {"x86", "x86"},
        {"i386", "x86"}, {"i686", "x86"}, {"arm", "arm32"}, {"ppc64", "ppc64"}, {"ppc64le", "ppc64"},
        {"s390x", "s390x"}, {"riscv64", null},
    };
    for (String[] pair : arch) {
      assertEquals(pair[0], pair[1], ObservabilityResource.hostArch(pair[0]));
    }
    assertNull(ObservabilityResource.hostArch(null));
  }

  @Test
  public void contextKeysCoverEveryKind() {
    Attributes single = ObservabilityHook.contextKeys(com.launchdarkly.sdk.LDContext.create("u"));
    assertEquals("u", single.get(AttributeKey.stringKey("user")));
    assertFalse(single.isEmpty());
  }
}
