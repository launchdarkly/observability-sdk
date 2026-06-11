# LaunchDarkly Observability SDK for Android

## Early Access Preview️

**NB: APIs are subject to change until a 1.x version is released.**

## Features

### Automatic Instrumentation

The Android observability plugin automatically instruments:
- **Activity Lifecycle**: App lifecycle events and transitions
- **App Lifecycle**: Emits `app_foreground` / `app_background` spans on foreground/background transitions, plus matching Session Replay `Foreground` / `Background` breadcrumbs (gate the span via `analytics.appLifecycle`)
- **Screen Views**: Emits a `screen_view` span for each Android `Activity` that is shown
- **Taps**: Optionally emits a `click` span for each tap (enable via `analytics.taps`)
- **HTTP Requests**: OkHttp and HttpURLConnection requests (requires setup of ByteBuddy compile time plugin and additional dependencies)
- **Crash Reporting**: Automatic crash reporting and stack traces
- **Feature Flag Evaluations**: Evaluation events added to your spans.
- **Session Management**: User session tracking and background timeout handling

## Example Application

A complete example application is available in the [e2e/android](../e2e/android) directory.

## Install

Add the dependency to your app's Gradle file:

```kotlin
dependencies {
    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.+")
    implementation("com.launchdarkly:launchdarkly-observability-android:0.19.1")
}
```

## Usage

### Basic Setup

Add the observability plugin to your LaunchDarkly Android Client SDK configuration:

```kotlin
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.sdk.android.LDConfig
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDClient

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val mobileKey = "your-mobile-key"
        
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        Observability(this@MyApplication, mobileKey)
                    )
                )
            )
            .build()
            
        val context = LDContext.builder(ContextKind.DEFAULT, "user-key")
            .build()
            
        LDClient.init(this@MyApplication, ldConfig, context)
    }
}
```

<details>
<summary>Java</summary>

The SDK is written in Kotlin but is fully usable from Java. `Observability` and `SessionReplay`
accept the same options objects; use the `*.builder()` factories described below to configure them.

```java
import com.launchdarkly.observability.plugin.Observability;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.android.Components;
import com.launchdarkly.sdk.android.LDClient;
import com.launchdarkly.sdk.android.LDConfig;
import com.launchdarkly.sdk.android.integrations.Plugin;

import java.util.Collections;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        String mobileKey = "your-mobile-key";

        LDConfig ldConfig = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .mobileKey(mobileKey)
                .plugins(
                        Components.plugins().setPlugins(
                                Collections.<Plugin>singletonList(
                                        new Observability(this, mobileKey)
                                )
                        )
                )
                .build();

        LDContext context = LDContext.builder(ContextKind.DEFAULT, "user-key")
                .build();

        LDClient.init(this, ldConfig, context);
    }
}
```

</details>

### Configure additional instrumentations

To enable HTTP request instrumentation and user interaction instrumentation, add the following plugin and dependencies to your top level application's Gradle file.

<CodeBlocks>
<CodeBlock title='Gradle Groovy'>

```java
plugins {
    id 'net.bytebuddy.byte-buddy-gradle-plugin' version '1.+'
}

dependencies {
    // Android HTTP Url instrumentation
    implementation 'io.opentelemetry.android.instrumentation:httpurlconnection-library:0.11.0-alpha'
    byteBuddy 'io.opentelemetry.android.instrumentation:httpurlconnection-agent:0.11.0-alpha'

    // OkHTTP instrumentation
    implementation 'io.opentelemetry.android.instrumentation:okhttp3-library:0.11.0-alpha'
    byteBuddy 'io.opentelemetry.android.instrumentation:okhttp3-agent:0.11.0-alpha'
}
```

### Advanced Configuration

You can customize the observability plugin with various options:

```kotlin
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.sdk.android.LDAndroidLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration.Companion.minutes

val mobileKey = "your-mobile-key"

val observabilityPlugin = Observability(
    application = this@MyApplication,
    mobileKey = mobileKey,
    options = ObservabilityOptions(
        serviceName = "my-android-app",
        serviceVersion = "1.0.0",
        debug = true,
        sessionBackgroundTimeout = 30.minutes,
        logAdapter = LDAndroidLogging.adapter(),
        resourceAttributes = Attributes.of(
            AttributeKey.stringKey("environment"), "production",
            AttributeKey.stringKey("team"), "mobile"
        ),
        customHeaders = mapOf(
            "X-Custom-Header" to "custom-value"
        )
    )
)
```

<details>
<summary>Java</summary>

From Java, build `ObservabilityOptions` with `ObservabilityOptions.builder()` instead of the Kotlin
constructor (Java cannot omit Kotlin default parameters). Each setter defaults to the same value as
the constructor, so set only what you need. Options typed as a Kotlin `Duration` — such as
`sessionBackgroundTimeout` — have a Java-friendly millis overload (`sessionBackgroundTimeoutMillis(long)`).

```java
import com.launchdarkly.observability.api.ObservabilityOptions;
import com.launchdarkly.observability.plugin.Observability;
import com.launchdarkly.sdk.android.LDAndroidLogging;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import java.util.Collections;

String mobileKey = "your-mobile-key";

Observability observabilityPlugin = new Observability(
        this,
        mobileKey,
        ObservabilityOptions.builder()
                .serviceName("my-android-app")
                .serviceVersion("1.0.0")
                .debug(true)
                // sessionBackgroundTimeout is a Kotlin Duration; use the millis overload from Java.
                .sessionBackgroundTimeoutMillis(java.util.concurrent.TimeUnit.MINUTES.toMillis(30))
                .logAdapter(LDAndroidLogging.adapter())
                .resourceAttributes(Attributes.of(
                        AttributeKey.stringKey("environment"), "production",
                        AttributeKey.stringKey("team"), "mobile"
                ))
                .customHeaders(Collections.singletonMap("X-Custom-Header", "custom-value"))
                .build()
);
```

</details>

Additional `ObservabilityOptions` settings:

- `sessionBackgroundTimeout`: How long the app can stay backgrounded before the current session ends (defaults to 15 minutes). In Kotlin this is a `kotlin.time.Duration` (e.g. `30.minutes`). From Java, where `Duration` is awkward to construct, the builder exposes `sessionBackgroundTimeoutMillis(long)` instead.
- `logsApiLevel`: Minimum log severity to export (defaults to `INFO`). Set to `ObservabilityOptions.LogLevel.NONE` to disable log exporting.
- `tracesApi`: Controls trace recording (defaults to enabled). Use `ObservabilityOptions.TracesApi.disabled()` to disable all tracing, or set `includeErrors`/`includeSpans`.
- `metricsApi`: Controls metric export (defaults to enabled). Use `ObservabilityOptions.MetricsApi.disabled()` to disable metrics.
- `instrumentations`: Enables/disables specific automatic instrumentations:
  - `crashReporting` (default `true`): report uncaught exceptions as errors.
  - `launchTime` (default `false`): record application startup time as metrics.
  - `userTaps` (default `true`): run the tap-detection machinery. Disabling `userTaps` stops tap detection entirely, so no `click` spans are emitted regardless of `analytics.taps`.
  - `screens` (default `true`): automatically detect screen changes from Android `Activity` lifecycle callbacks. This drives the `screen_view` span (gated separately by `analytics.screenViews`) and Session Replay `Navigate` events.
- `analytics`: Enables/disables analytics telemetry, emitted as OpenTelemetry spans:
  - `taps` (default `true`): publish a `click` span for each detected tap. Tap detection is governed by `instrumentations.userTaps`; this flag only controls publishing the span.
  - `pageViews` (default `true`): emit spans for Android Activity lifecycle events (screen/page views).
  - `trackEvents` (default `true`): emit a `track` span when a custom event is tracked, either automatically via the LaunchDarkly `afterTrack` hook (`LDClient.track(...)`) or manually via `LDObserve.track(...)`.
  - `screenViews` (default `true`): emit a `screen_view` span when a screen is shown. This only gates the span; screen *detection* (and Session Replay `Navigate` events) is controlled by `instrumentations.screens`.
  - `appLifecycle` (default `true`): emit app-lifecycle spans as the app moves between states: `app_foreground` (with `event.lifecycle_state = foreground`) when it enters the foreground, and `app_background` (with `event.lifecycle_state = background`) when it enters the background. This flag only gates the span; the matching Session Replay `Foreground` / `Background` breadcrumbs are emitted regardless.

Example:

```kotlin
val options = ObservabilityOptions(
    logsApiLevel = ObservabilityOptions.LogLevel.WARN,
    tracesApi = ObservabilityOptions.TracesApi(includeErrors = true, includeSpans = false),
    metricsApi = ObservabilityOptions.MetricsApi.disabled(),
    instrumentations = ObservabilityOptions.Instrumentations(
        crashReporting = false,
        launchTime = true,
        screens = true
    ),
    analytics = ObservabilityOptions.Analytics(
        taps = true,
        pageViews = true,
        trackEvents = true,
        screenViews = true,
        appLifecycle = true
    )
)
```

<details>
<summary>Java</summary>

The nested option types (`TracesApi`, `Analytics`, `Instrumentations`) each expose a `builder()` as
well; `MetricsApi` keeps its `enabled()` / `disabled()` factories.

```java
import com.launchdarkly.observability.api.ObservabilityOptions;

ObservabilityOptions options = ObservabilityOptions.builder()
        .logsApiLevel(ObservabilityOptions.LogLevel.WARN)
        .tracesApi(ObservabilityOptions.TracesApi.builder()
                .includeErrors(true)
                .includeSpans(false)
                .build())
        .metricsApi(ObservabilityOptions.MetricsApi.disabled())
        .instrumentations(ObservabilityOptions.Instrumentations.builder()
                .crashReporting(false)
                .launchTime(true)
                .screens(true)
                .build())
        .analytics(ObservabilityOptions.Analytics.builder()
                .taps(true)
                .pageViews(true)
                .trackEvents(true)
                .screenViews(true)
                .build())
        .build();
```

</details>

### Tracking Screen Views

The SDK emits a `screen_view` span (following the analytics taxonomy `event.*` namespace) whenever a screen is shown. Each `screen_view` also resolves `event.previous_screen` from a shared navigation stack and broadcasts a Session Replay `Navigate` event.

#### Automatic capture (Activities)

When `instrumentations.screens` is enabled (the default), every Android `Activity` is captured on resume. By default the screen name is derived by cleaning the class name (`ProfileActivity` → `Profile`), and `event.screen_class` / `event.screen_id` are populated from the activity class.

To customize the reported name or category, implement `LDScreenNameProvider` on your `Activity`:

```kotlin
import com.launchdarkly.observability.client.screen.LDScreenNameProvider

class CheckoutActivity : ComponentActivity(), LDScreenNameProvider {
    override val ldScreenName: String = "Checkout"
    override val ldScreenCategory: String = "Commerce"
}
```

#### Manual capture (Fragments / Compose)

Screens that aren't backed by a distinct `Activity` (Fragments, Jetpack Compose destinations) won't be captured automatically. Report them manually with `LDObserve.trackScreenView`:

```kotlin
import com.launchdarkly.observability.sdk.LDObserve

LDObserve.trackScreenView(
    name = "Profile",
    screenClass = "ProfileFragment",
    screenId = "com.example.app.ProfileFragment",
    category = "Account"
)
```

A single call per appearance is sufficient regardless of capture mode — `event.previous_screen` is resolved through the shared stack, which handles re-appearance and back-navigation.

#### Decoupling detection from the span

- `instrumentations.screens` controls automatic screen *detection*. It drives both the automatic `screen_view` span and the Session Replay `Navigate` event.
- `analytics.screenViews` only gates the `screen_view` span. Detection and the `Navigate` broadcast are independent of this flag, and manual `trackScreenView(...)` still works when `screens` is disabled.

### Recording Observability Data

After initialization of the LaunchDarkly Android Client SDK, use `LDObserve` to record metrics, logs, errors, and traces:

```kotlin
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.interfaces.Metric
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity

// Record metrics
LDObserve.recordMetric(Metric("user_actions", 1.0))
LDObserve.recordCount(Metric("api_calls", 1.0))
LDObserve.recordIncr(Metric("page_views", 1.0))
LDObserve.recordHistogram(Metric("response_time", 150.0))
LDObserve.recordUpDownCounter(Metric("active_connections", 1.0))

// Record logs — pass attributes as a plain map via `properties`.
LDObserve.recordLog(
    "User performed action",
    Severity.INFO,
    properties = mapOf(
        "user_id" to "12345",
        "action" to "button_click"
    )
)

// Record errors
LDObserve.recordError(
    Exception("Something went wrong"),
    Attributes.of(
        AttributeKey.stringKey("component"), "payment",
        AttributeKey.stringKey("error_code"), "PAYMENT_FAILED"
    )
)

// Create spans for tracing
val span = LDObserve.startSpan(
    "api_request",
    properties = mapOf(
        "endpoint" to "/api/users",
        "method" to "GET"
    )
)
span.makeCurrent().use {
    // Your code here
}
span.end()

// Record a custom track event as a `track` span.
// (Calling LDClient.track(...) records the same span automatically via the afterTrack hook.)
LDObserve.track(
    "checkout_completed",
    properties = mapOf("currency" to "USD"),
    metricValue = 42.0
)

// Record a `screen_view` span for screens not backed by a distinct Activity
// (e.g. Fragments or Compose destinations). Activities are captured automatically.
LDObserve.trackScreenView(
    name = "Profile",
    screenClass = "ProfileFragment",
    category = "Account",
    properties = mapOf("tab" to "overview")
)
```

`recordLog`, `startSpan`, `track`, and `trackScreenView` accept attributes/data as a
plain Kotlin map via the `properties` parameter — pass `String`, `Boolean`, `Int`,
`Long`, `Double`, lists, and nested maps directly, with no need to build OpenTelemetry
`Attributes`. The `Attributes` overloads remain available when you need precise
OpenTelemetry typing (as shown for `recordError` above). For `trackScreenView`, custom
properties are applied at lower precedence than the reserved `event.*` taxonomy fields,
so they can never clobber them.

### Session Replay

#### Enable Session Replay

Add the Session Replay plugin **after** Observability when configuring the LaunchDarkly SDK:

```kotlin
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.plugin.SessionReplay

val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
    .mobileKey("your-mobile-key")
    .plugins(
        Components.plugins().setPlugins(
            listOf(
                Observability(this@MyApplication, "your-mobile-key"),
                SessionReplay() // depends on Observability being present first
            )
        )
    )
    .build()
```

Notes:
- SessionReplay depends on Observability. If Observability is missing or listed after SessionReplay, the plugin logs an error and stays inactive.
- Observability runs fine without SessionReplay; adding SessionReplay extends the Observability pipeline to include session recording.

#### Delay Start

If you need to begin recording after login or later in the app lifecycle, disable automatic capture and start it later:

```kotlin
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.sdk.LDReplay

val sessionReplay = SessionReplay(
    ReplayOptions(enabled = false)
)

// After login:
LDReplay.start()
```

Call `LDReplay.stop()` to pause recording.

#### Masking sensitive UI

Use `ldMask()` to mark views that should be masked in session replay. There are helpers for both XML-based Views and Jetpack Compose.

##### How the SDK Determines What to Mask

When deciding whether a specific view should be masked in a Session Replay, the SDK evaluates rules in a strict order of precedence. It checks these conditions from top to bottom and stops at the first one that applies:

1. **Explicit Masking (Highest Priority)**: Is the view, or *any* of its parent views, explicitly masked (e.g., using `.ldMask()` or matching `maskXMLViewIds`)?
   * **Yes**: The view is **masked**. This overrides all other rules.
2. **Explicit Unmasking**: Is the view, or *any* of its parent views, explicitly unmasked (e.g., using `.ldUnmask()` or matching `unmaskXMLViewIds`)?
   * **Yes**: The view is **unmasked**.
3. **Global Configuration**: Does your global privacy configuration (like `maskTextInputs`, `maskImages`, etc.) apply to this view?
   * **Yes**: The view follows the global configuration.

*Note: If multiple rules conflict at the same level, masking wins over unmasking.*

##### Configure masking via `PrivacyProfile`

If you want to configure masking globally (instead of calling `ldMask()` on each element), pass a `PrivacyProfile` to `ReplayOptions`:

```kotlin
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.view
import com.launchdarkly.observability.replay.plugin.SessionReplay

val sessionReplay = SessionReplay(
    ReplayOptions(
        privacyProfile = PrivacyProfile(
            // Toggle built-in masking:
            // - maskTextInputs: masks text input fields (e.g. EditText / Compose text fields)
            maskTextInputs = true,
            // - maskText: masks non-input text targets
            maskText = false,
            // New settings:
            maskViews = listOf(
                // Masks targets by *exact* Android View class (does not match subclasses).
                view(android.widget.ImageView::class),
                // You can also provide the class name as a string (FQCN).
                view("android.widget.EditText"),
            ),
            maskWebViews = true,
            maskXMLViewIds = listOf(
                // Masks by resource entry name (from resources.getResourceEntryName(view.id)).
                // Accepts "@+id/foo", "@id/foo", or "foo".
                "@+id/password",
                "credit_card_number",
            ),
            unmaskXMLViewIds = listOf(
                // Unmasks views matching these ids. Same id format as maskXMLViewIds. Takes
                // precedence over global rules like `maskText`/`maskTextInputs`, but an explicit
                // mask on the same view or any of its ancestors still wins.
                "@+id/greeting",
            ),
        )
    )
)
```

Notes:
- `maskViews` matches on `target.view.javaClass` equality (exact class only).
- `maskWebViews` uses a default list of WebView class names for masking (and still allows subclasses), including AndroidView-hosted views inside Compose.
- `maskXMLViewIds` and `unmaskXMLViewIds` apply to Views with a non-`View.NO_ID` id that resolves to a resource entry name. When the React Native library is on the runtime classpath, they also match the value of the `react_test_id` tag — i.e. the JS `testID` prop on RN-rendered views.

##### XML Views

Import the masking API and call `ldMask()` on any `View` (for example, after inflating the layout in an `Activity` or `Fragment`).

```kotlin
import com.launchdarkly.observability.api.ldMask

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val password = findViewById<EditText>(R.id.password)
        password.ldMask() // mask this field in session replay
    }
}
```

With View Binding or Data Binding:

```kotlin
import com.launchdarkly.observability.api.ldMask

override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View {
    _binding = SettingsPageBinding.inflate(inflater, container, false)
    binding.nestedScrollView.systemBarsPadding()
    viewModel.toggleBackgroundAccess(requireContext().isIgnoreBatteryEnabled())
    val toolbar = binding.toolbar
    toolbar.ldMask()
}
```

Use `ldUnmask()` to explicitly opt a view out of masking. This overrides global masking rules (e.g. `maskText`) for the view and its descendants — but an explicit `ldMask()` on the view itself or any ancestor still wins.

##### Jetpack Compose

Add the masking `Modifier` to any composable you want masked in session replay.

```kotlin
import com.launchdarkly.observability.api.ldMask

@Composable
fun CreditCardField() {
    ...
   	var zipCode by remember { mutableStateOf("") }

    OutlinedTextField(
        value = zipCode,
        onValueChange = { zipCode = it },
        label = { Text("ZIP Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .ldMask()
    )
}
```

Use `Modifier.ldUnmask()` to explicitly opt a composable out of masking. This overrides global masking rules (e.g. `maskText`) for the composable and its descendants — but an explicit `ldMask()` on the composable itself or any ancestor still wins.

Notes:
- Masking marks elements so their contents are obscured in recorded sessions.
- You can apply masking to any `View` or composable where sensitive data may appear.

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](../../CONTRIBUTING.md) for instructions on how to contribute to this SDK.

## About LaunchDarkly

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Read [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [launchdarkly.com/blog](https://launchdarkly.com/blog/  "LaunchDarkly Blog Documentation") for the latest product updates
