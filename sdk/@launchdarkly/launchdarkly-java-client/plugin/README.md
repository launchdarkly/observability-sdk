# LaunchDarkly Observability plugin for the client-side Java SDK

An observability plugin for the LaunchDarkly client-side Java SDK,
the SDK for desktop and other single-user Java apps. It does for that SDK what
[`observability-android`](https://github.com/launchdarkly/observability-sdk/tree/main/sdk/%40launchdarkly/observability-android)
does for the Android SDK. Its names, options and signals follow the Android plugin's.

**Status:** pre-release (0.1.0), not yet published. Requires Java 8 or later.

## Install

Neither the SDK nor the plugin is on Maven Central yet. Install both into your local Maven
repository, the SDK from a checkout of java-client-sdk and the plugin from this folder:

```sh
(cd path/to/java-client-sdk && ./gradlew :launchdarkly-java-client-sdk:publishToMavenLocal)
./gradlew publishToMavenLocal
```

Then depend on both:

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    implementation("com.launchdarkly:launchdarkly-java-client-sdk:0.1.0")
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability:0.1.0")
}
```

Your application supplies the SDK; the plugin brings only OpenTelemetry 1.51 and OkHttp 4.12,
the OkHttp version the SDK already uses.

A Compose for Desktop application can also depend on the plugin's Compose library, for
`Modifier.ldMask()`, `ldUnmask()` and `ldId()` (see [Session replay](#session-replay)). It depends
on nothing; the application brings Compose and Kotlin:

```kotlin
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability-compose:0.1.0")
```

## Use

Add the plugin to the SDK's configuration:

```java
LDConfig config = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
    .mobileKey(mobileKey)
    .plugins(Components.plugins().setPlugins(Collections.singletonList(
        new Observability(ObservabilityOptions.builder()
            .serviceName("my-desktop-app")
            .serviceVersion("1.4.2")
            .build()))))
    .build();
LDClient client = LDClient.init(config, context, 5);
```

Telemetry goes to the LaunchDarkly project of the SDK's credential, a mobile key or a
client-side ID. Record your own data with `LDObserve`:

```java
LDObserve.recordLog("Report exported", Severity.INFO, Attributes.empty());
LDObserve.recordError(exception, Attributes.empty());
LDObserve.recordHistogram(new Metric("render.ms", 42.0));
LDObserve.track("checkout", Collections.singletonMap("items", 3), 59.90);

Span span = LDObserve.startSpan("load-report", Attributes.empty());
try {
    // ...
} finally {
    span.end();
}
```

To use observability without an `LDClient`, call `LDObserve.init(credential, options)` instead.
Every `LDObserve` method is safe to call from any thread, and does nothing before observability
starts.

## What it records

| Signal | When | Shape |
|---|---|---|
| `evaluation` span | each flag evaluation | `feature_flag.*` attributes and a `feature_flag` event with the result. A flag evaluated again with the same result is reported once every 10 minutes, and again after each identify, through the SDK's `DedupingHook`. |
| `LD.identify` log | each completed identify | the context keys by kind, `key`, `canonicalKey` |
| `track` span | each `LDClient.track` and `LDObserve.track` | the event's data, the context keys, `key` and `value` |
| `highlight.error` span | `LDObserve.recordError`, and each uncaught exception on any thread | the exception as an event; crashes add `thread.name` and `thread.id` |
| `app_launch` span | when the plugin starts | `event.launch_type` (`install`, `update`, `relaunch` or `unknown`), `event.version`; with `launchTime`, the span starts with the JVM and carries an `app.start` event with `start.duration_ms` |
| `app_foreground` / `app_background` span | one of the app's windows becomes active, or none is | `event.lifecycle_state` |
| `screen_view` span | a different window becomes active, and `LDObserve.trackScreenView` | `event.name` (the window's class, for JavaFX its scene root's class, for SWT its JFace window's class, or with `recordText` its title), `event.screen_class`, `event.screen_id`, `event.previous_screen` |
| `click` span | each mouse click in a Swing, AWT, JavaFX, SWT, Apache Pivot or Compose for Desktop window, from press to click (in SWT, a release on the control pressed; in Pivot, on the Pivot component under the mouse; in Compose, on the clickable composable under it, such as a `Button`, named by its role) | `event.tag`, `event.classname`, `event.id` (the name the app gave the component; in SWT its data under `"name"`, `control.setData("name", "save")`; in Compose its `Modifier.ldId`, else its test tag, as on Android), `event.xpath`, `event.screen_id`, `event.screen_name`, `event.x`, `event.y`, and with `recordText` `event.text` (buttons and labels only) |
| logs, spans, metrics | `LDObserve` | as recorded |

Every signal carries `session.id`. A session starts with the plugin and is replaced after four
hours, or after 15 minutes in the background with nothing recorded (`sessionBackgroundTimeoutMillis`).
An application is in the background when none of its windows is active; one without windows is
never in the background. The resource carries `service.name`, `service.version`, the operating system, the Java
runtime and, from the SDK, `launchdarkly.application.*` and `launchdarkly.sdk.version`.

Signals go out over OTLP/HTTP with gzip. Sampling rules configured in LaunchDarkly are fetched at
start and applied at export.

Windows and clicks are watched in Swing and AWT, through an AWT event listener, in JavaFX 11 or
later, through listeners on each window, and in SWT 3.116 (Eclipse 2021-03) or later, through
filters on its display. The plugin never starts a toolkit itself, which on macOS would give a
console application a dock icon: it attaches to each once the application has started it, checking
once a second until then. SWT names no thread, so its event loop is told from the threads' stacks.
An application that shows windows of more than one is in the background only when none has an
active window. JavaFX and SWT are never dependencies of the plugin; on a runtime without the desktop
module, or without JavaFX or SWT, the plugin runs without that toolkit's signals.

### Privacy

By default the plugin records no text from the screen. A window title, a button's label or a menu
item can hold a person's name, an account or a file path, so a screen is named after its window's
class and a click records only the component's class, name and place in its window. To record
window titles and the text of clicked buttons, menu items and labels, opt in with
`ObservabilityOptions.Instrumentations.builder().recordText(true)`. The text of text fields and other
input components is never recorded. Flag values are recorded on evaluation spans, and `track` data on
track spans; string attributes longer than 16 KB are cut short.

Crash reporting installs a default uncaught-exception handler that records the exception and then
hands it to the handler that was there before. To tell an install from an update, the launch span
keeps the last application version in the user's Java preferences. Turn any of these off
with `ObservabilityOptions.Instrumentations` (what is watched) and `ObservabilityOptions.Analytics`
(which spans are emitted).

## Session replay

Add `SessionReplay` next to `Observability` to record what the application's windows show, and
the mouse over them, so a session can be watched in LaunchDarkly alongside its errors, logs and
traces:

```java
.plugins(Components.plugins().setPlugins(Arrays.asList(
    new Observability(ObservabilityOptions.builder().serviceName("my-desktop-app").build()),
    new SessionReplay(ReplayOptions.builder()
        .privacyProfile(PrivacyProfile.defaults())
        .build()))))
```

Without an `LDClient`, call `LDReplay.init(replayOptions)` after `LDObserve.init`. Like the rest of
the plugin, replay never starts a toolkit itself. It records these windows:

| Toolkit | How a frame is taken |
| --- | --- |
| Swing and AWT | the window painted offscreen, on the Swing thread |
| JavaFX 11 or later | a snapshot of the scene, on the JavaFX thread, skipped when nothing was laid out since the last |
| SWT 3.116 (Eclipse 2021-03) or later | on macOS a copy of what the shell last drew, since SWT's printing misplaces controls there; elsewhere the shell printed, which draws it whatever is over it on the screen |
| Apache Pivot 2.0 | its display host painted offscreen, as any Swing component |
| Compose for Desktop 1.6 or later | the last frame skiko drew, since Compose draws on the GPU, which painting offscreen cannot reach |

The [demo app](../demo) has a window
for each.

**What it records.** Once a second, at most, replay takes the active window, with its dialogs and
popups over it, at the window's logical size, and sends the parts that changed as JPEG tiles. It
records the mouse moving, pressing and releasing, and puts the plugin's screens, clicks, tracks,
identifies and foreground changes on the replay's timeline. It takes frames only while one of the
application's windows is active. A session is the plugin's session, so the replay sits with that
session's telemetry.

**Masking.** Each frame is masked in the application, before it is encoded: masked pixels are never
sent. The default `PrivacyProfile`, as on Android, masks text inputs and passwords.
`PrivacyProfile.strict()` masks all text as well: labels, buttons, lists, tables and trees. Passwords
are always masked, and content that cannot be taken with the window, such as a heavyweight AWT
`Canvas` (JCEF draws into one), a JavaFX `MediaView` or an SWT `GLCanvas`, is masked unless unmasked.
The profile's other switches are Android's: `maskImages`; `maskWebViews`, for JavaFX's `WebView`,
SWT's `Browser` and JxBrowser's Swing view, off by default so web pages are recorded like the rest
of the window; `maskBySemanticsKeywords`, which masks what has a word such as "email", "phone",
"card" or "password" in its text, hint, tooltip, accessible name or content description (the text
is read only to decide, and never sent); and `minimumAlpha`, the opacity below which a JavaFX node
is treated as not shown. Each toolkit's controls fall under the rules like this:

| Toolkit | Inputs (`maskTextInputs`) | Text (`maskText`) | Marked to mask or unmask by |
| --- | --- | --- | --- |
| Swing and AWT | text components (`JTextComponent`, AWT `TextField` and `TextArea`); `JPasswordField` always | labels, buttons, lists, tables, trees, combo boxes, spinners, tooltips, tab titles | `LDReplay.mask`, the client property `LDReplay.MASK_PROPERTY`, its name, its class |
| JavaFX | text fields and areas, the HTML editor; `PasswordField` always | labeled controls, `Text`, lists, tables, trees, combo and choice boxes, spinners, date pickers | `LDReplay.mask`, the node property `LDReplay.MASK_PROPERTY`, the style class `ld-mask` or `ld-unmask`, its id, its class |
| SWT | `Text`, editable `Combo` and `StyledText`, `Spinner`, `DateTime`; `Text` with `SWT.PASSWORD` always | labels, buttons, links, lists, tables, trees, tab and tool bar titles | `LDReplay.mask`, its data under `LDReplay.MASK_PROPERTY`, its name (its data under `"name"`), its class |
| Apache Pivot | `TextInput`, `TextArea`, `TextPane`, `Spinner`; a password `TextInput` always | labels, buttons, lists, tables, trees | `LDReplay.mask`, its user data under `LDReplay.MASK_PROPERTY`, its name, its class |
| Compose for Desktop | text fields; password fields always | `Text` (and `Image` is an image, for `maskImages`) | `Modifier.ldMask()` or `ldUnmask()`, its test tag as its name |

Compose's modifiers come from the plugin's Compose library, with the same names, keys and package
as Android's, so shared Compose code masks the same on both:

```java
LDReplay.mask(accountPanel);                               // Swing, AWT, JavaFX, SWT or Pivot
textField.putClientProperty(LDReplay.MASK_PROPERTY, true); // Swing
node.getStyleClass().add(LDReplay.MASK_STYLE_CLASS);       // JavaFX: "ld-mask", or "ld-unmask"
composite.setData(LDReplay.MASK_PROPERTY, true);           // SWT
PrivacyProfile.builder().addMaskName("accountPanel").addMaskClass(CardView.class)
    .addUnmaskClass(HelpPanel.class).build();
```

```kotlin
import com.launchdarkly.observability.api.ldId
import com.launchdarkly.observability.api.ldMask
import com.launchdarkly.observability.api.ldUnmask

Column(Modifier.ldMask()) { AccountDetails() }               // Compose for Desktop
Text(helpText, Modifier.ldUnmask())
Button(onClick = pay, modifier = Modifier.ldId("checkout.pay")) { Text("Pay") }
```

A mark to mask wins over a mark to unmask on an enclosing component, and either wins over the
profile's rules.

**Control.** `ReplayOptions` sets `enabled`, `sampleRate`, `frameRate`, `scale`, `imageQuality`,
`compression` and `debug`, with Android's defaults (on, every session, one frame a second, logical
size, quality 0.3, `CompressionMethod.overlayTiles()`, off). Overlay tiles send only what changed,
with a whole frame every 15; `CompressionMethod.screenImage()` sends every changed frame whole.
`LDReplay.stop()` and `LDReplay.start()` stop and restart recording, for instance around a sensitive
screen; `start` returns what happened, such as `SAMPLED_OUT`. Calls made before replay is installed
apply once it is.

**Cost.** On the toolkit's thread, painting a Swing window offscreen takes about 2 ms, a JavaFX
snapshot about 10 ms, an SWT shell's copy about 6 ms on macOS, and a Compose window adds about a
millisecond or two for skiko's copy of its last frame. Encoding runs on replay's own thread. If a
toolkit's thread is busy, the frame is skipped.

**Regions.** Where the backend refuses session replay, such as for a project in a region without
it, replay stops for the rest of the launch, and the next launch takes no screenshots until the
backend has accepted its session. The refusal is remembered in the user's Java preferences.

## Shutdown

The plugin registers a JVM shutdown hook that exports what is still queued, waiting up to five
seconds. If your application manages its own shutdown, turn the hook off with
`.shutdownHook(false)` and call `LDObserve.shutdown()` yourself, off the Swing thread, since it can
wait that long for an unreachable endpoint. `LDObserve.flush()` starts an export without waiting, so
it is safe on the Swing thread. Session replay stops with observability, sending what it has queued
for up to three more seconds.

Observability runs for one LaunchDarkly credential per process. The first start decides the
options: a later `LDObserve.init` or plugin for the same credential joins it, and one for another
credential records nothing and logs a warning.

## API stability

The public API is the `com.launchdarkly.observability` package: `Observability`,
`ObservabilityOptions`, `LDObserve`, `Metric`, and for replay `SessionReplay`, `ReplayOptions`,
`PrivacyProfile`, `LDReplay` and `SessionReplayStartResult`. The `internal` packages are public only so the
plugin's packages can share them; they can change in any release. `LDObserve` takes and returns
OpenTelemetry API types (`Attributes`, `Severity`, `Span`, `OpenTelemetry`), as the Android and
server-side plugins do.

## Build

```sh
./gradlew check                       # tests, a 99% line / 98% branch coverage gate, POM checks
./gradlew check -PtestJavaVersion=8   # the same tests on Java 8
```

The desktop tests create windows, so they need a display; without one they skip themselves, and
the coverage gate will then fail. The JavaFX tests run on Java 11 and later, using JavaFX's software
renderer, and are left out on Java 8. The Compose for Desktop tests, in the `compose` project
with the Compose library, run real Compose windows against the plugin, on skiko's software
renderer, and count toward the plugin's coverage; with `-PtestJavaVersion=8` they run on Gradle's
own JDK, as Compose needs Java 11. The SWT tests run in a JVM of their own, on its main thread,
which SWT needs on macOS (`./gradlew swtTest`); each JDK runs them on the newest SWT it can, 3.116
on Java 8 up to the latest on Java 21, and they count toward the coverage too. See [CONTRIBUTING.md](CONTRIBUTING.md) for CI and releases.

The sampling tests run the scenarios shared by the other LaunchDarkly SDKs, copied from
`go/internal/otel` in observability-sdk.

If this is ever moved upstream, its home would be `sdk/@launchdarkly/observability-java-client`
in [`observability-sdk`](https://github.com/launchdarkly/observability-sdk).

## License

Apache 2.0. See [LICENSE](LICENSE).
