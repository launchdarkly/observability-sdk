# LaunchDarkly Observability SDK for Android

## Early Access Preview️

**NB: APIs are subject to change until a 1.x version is released.**

## Features

### Automatic Instrumentation

The Android observability plugin automatically instruments:
- **Activity Lifecycle**: App lifecycle events and transitions
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
    implementation("com.launchdarkly:launchdarkly-observability-android:0.5.0")
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
        
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey("your-mobile-key")
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        Observability(this@MyApplication)
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
import com.launchdarkly.observability.api.Options
import com.launchdarkly.sdk.android.LDAndroidLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

val observabilityPlugin = Observability(
    application = this@MyApplication,
    options = Options(
        serviceName = "my-android-app",
        serviceVersion = "1.0.0",
        debug = true,
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

// Record logs
LDObserve.recordLog(
    "User performed action",
    Severity.INFO,
    Attributes.of(
        AttributeKey.stringKey("user_id"), "12345",
        AttributeKey.stringKey("action"), "button_click"
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
    Attributes.of(
        AttributeKey.stringKey("endpoint"), "/api/users",
        AttributeKey.stringKey("method"), "GET"
    )
)
span.makeCurrent().use {
    // Your code here
}
span.end()
```

### Session Replay

#### Masking sensitive UI

Use `ldMask()` to mark views that should be masked in session replay. There are helpers for both XML-based Views and Jetpack Compose.

##### XML Views

Import the masking API and call `ldMask()` on any `View` (for example, after inflating the layout in an `Activity` or `Fragment`).

```kotlin
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.launchdarkly.observability.api.ldMask

class LoginActivity : AppCompatActivity() {
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
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.launchdarkly.observability.api.ldMask

class CheckoutFragment : Fragment(R.layout.fragment_checkout) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCheckoutBinding.bind(view)
        binding.creditCardNumber.ldMask()
        binding.cvv.ldMask()
    }
}
```

Optional: use `ldUnmask()` to explicitly clear masking on a view you previously masked.

##### Jetpack Compose

Add the masking `Modifier` to any composable you want masked in session replay.

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.launchdarkly.observability.api.ldMask

@Composable
fun CreditCardField() {
    var number by remember { mutableStateOf("") }
    TextField(
        value = number,
        onValueChange = { number = it },
        modifier = Modifier
            .fillMaxWidth()
            .ldMask() // mask this composable in session replay
    )
}
```

Optional: use `Modifier.ldUnmask()` to explicitly clear masking on a composable you previously masked.

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
