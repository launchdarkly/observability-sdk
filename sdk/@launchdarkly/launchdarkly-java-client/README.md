# LaunchDarkly Observability for the client-side Java SDK

Observability and session replay for desktop and other single-user Java apps that use the
LaunchDarkly client-side Java SDK. It does for that SDK what
[`observability-android`](../observability-android) does for the Android SDK.

| Folder | What it is |
| --- | --- |
| [`plugin`](plugin) | The plugin, `com.launchdarkly:launchdarkly-java-client-sdk-observability`: flag evaluation, identify and track spans, errors and crashes, sessions, desktop signals, and session replay for Swing, AWT, JavaFX, SWT, Apache Pivot and Compose for Desktop. Its `compose` project is the Compose library, `com.launchdarkly:launchdarkly-java-client-sdk-observability-compose`. |
| [`demo`](demo) | A demo app: a console demo, a Swing dashboard, and a session replay demo per toolkit. It builds against `plugin` from source. |

Each folder is its own Gradle build. Gradle runs on JDK 17 or later; the plugin targets Java 8,
the demos Java 21.

```sh
make build   # the plugin's tests, coverage gate and POM checks, then the demos
make test    # the plugin's tests only
```

The SDK is not on Maven Central yet; both builds resolve it from Maven local. See
[`plugin/CONTRIBUTING.md`](plugin/CONTRIBUTING.md).

The plugin, session replay and the demo app were written by Brad Bunce.
