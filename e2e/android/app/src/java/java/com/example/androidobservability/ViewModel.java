package com.example.androidobservability;

import android.app.Application;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;

import com.launchdarkly.observability.interfaces.Metric;
import com.launchdarkly.observability.sdk.LDObserve;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.android.LDClient;
import com.launchdarkly.sdk.android.LaunchDarklyException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Java counterpart of the Kotlin {@code ViewModel} used by the compose flavor.
 *
 * <p>Kotlin coroutines ({@code viewModelScope.launch(Dispatchers.IO)}) are replaced with a
 * background {@link ExecutorService}; the detached-thread span/context demos still use plain
 * {@link Thread}s, matching the Kotlin original.
 */
public class ViewModel extends AndroidViewModel {

    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    public ViewModel(@NonNull Application application) {
        super(application);
    }

    /**
     * {@code LDClient.get()} throws a checked {@link LaunchDarklyException} in Java when the client
     * is not yet initialized. This helper converts it to an unchecked exception so the demo call
     * sites stay readable.
     */
    private LDClient ldClient() {
        try {
            return LDClient.get();
        } catch (LaunchDarklyException e) {
            throw new IllegalStateException("LDClient is not initialized", e);
        }
    }

    public void triggerMetric() {
        LDObserve.Companion.recordMetric(new Metric("test-gauge", 50.0, Attributes.empty(), null));
    }

    public void triggerHistogramMetric() {
        LDObserve.Companion.recordHistogram(new Metric("test-histogram", 15.0, Attributes.empty(), null));
    }

    public void triggerCountMetric() {
        LDObserve.Companion.recordCount(new Metric("test-counter", 10.0, Attributes.empty(), null));
    }

    public void triggerIncrementalMetric() {
        LDObserve.Companion.recordIncr(new Metric("test-incremental-counter", 12.0, Attributes.empty(), null));
    }

    public void triggerUpDownCounterMetric() {
        LDObserve.Companion.recordUpDownCounter(new Metric("test-up-down-counter", 25.0, Attributes.empty(), null));
    }

    public void triggerError() {
        LDObserve.Companion.recordError(
                new Error("Manual error womp womp", new Error("The error that caused the other error.")),
                Attributes.of(AttributeKey.stringKey("FakeAttribute"), "FakeVal")
        );
    }

    public void triggerLog() {
        Attributes attributes = Attributes.builder()
                .put(AttributeKey.stringKey("test-string"), "maui")
                .put(AttributeKey.booleanKey("test-true"), true)
                .put(AttributeKey.booleanKey("test-false"), false)
                .put(AttributeKey.longKey("test-integer"), 42L)
                .put(AttributeKey.doubleKey("test-double"), 3.14)
                .put(AttributeKey.doubleArrayKey("test-array"), Arrays.asList(3.14))
                .put(AttributeKey.longArrayKey("test-nested.array"), Arrays.asList(1L))
                .build();
        LDObserve.Companion.recordLog("Test Log", Severity.INFO, attributes, null);
    }

    public void triggerCustomLog(String message) {
        triggerCustomLog(message, Severity.INFO, Attributes.empty());
    }

    public void triggerCustomLog(String message, Severity severity, Attributes attributes) {
        if (message != null && !message.isEmpty()) {
            LDObserve.Companion.recordLog(message, severity, attributes, null);
        }
    }

    public void triggerLogWithContext(String message) {
        final String text = (message == null || message.isEmpty()) ? "Log with span context" : message;

        Span parentSpan = LDObserve.Companion.startSpan("parentSpan", Attributes.empty());
        try (Scope ignored = parentSpan.makeCurrent()) {
            final Context capturedRootContext = Context.current();

            new Thread(() -> {
                try (Scope contextScope = capturedRootContext.makeCurrent()) {
                    Span childSpan = LDObserve.Companion.startSpan("childSpan", Attributes.empty());
                    try (Scope childScope = childSpan.makeCurrent()) {
                        // do work
                        childSpan.end();
                    }
                }
            }).start();
        }
        parentSpan.end();

        backgroundExecutor.execute(() -> {
            Span span = LDObserve.Companion.startSpan(
                    "log-context-demo",
                    Attributes.of(AttributeKey.stringKey("demo"), "log-with-context")
            );
            // Capture span context while still on the originating thread.
            final SpanContext capturedContext;
            try (Scope scope = span.makeCurrent()) {
                capturedContext = span.getSpanContext();
            }
            span.end();

            // Simulate a detached thread where OTel context is lost automatically.
            // Span.current() here returns INVALID, so we pass the captured context explicitly.
            new Thread(() -> {
                try (Scope scope = Span.wrap(capturedContext).makeCurrent()) {
                    Span childSpan = LDObserve.Companion.startSpan("child of log-context-demo", Attributes.empty());
                    childSpan.end();
                }
                LDObserve.Companion.recordLog(
                        text,
                        Severity.WARN,
                        Attributes.of(AttributeKey.stringKey("source"), "detached-thread-demo"),
                        capturedContext
                );
            }).start();
        });
    }

    public void triggerCustomSpan(String spanName) {
        if (spanName != null && !spanName.isEmpty()) {
            backgroundExecutor.execute(() -> {
                Span customSpan = LDObserve.Companion.startSpan(
                        spanName,
                        Attributes.of(AttributeKey.stringKey("custom_span"), "true")
                );
                customSpan.end();
            });
        }
    }

    public void triggerNestedSpans() {
        backgroundExecutor.execute(() -> {
            Span newSpan0 = LDObserve.Companion.startSpan("NestedSpan", Attributes.empty());
            try (Scope scope0 = newSpan0.makeCurrent()) {
                Span newSpan1 = LDObserve.Companion.startSpan("NestedSpan1", Attributes.empty());
                try (Scope scope1 = newSpan1.makeCurrent()) {
                    Span newSpan2 = LDObserve.Companion.startSpan("NestedSpan2", Attributes.empty());
                    try (Scope scope2 = newSpan2.makeCurrent()) {
                        LDObserve.Companion.recordCount(new Metric("test-counter", 10.0, Attributes.empty(), null));
                        LDObserve.Companion.recordLog("NestedLog", Severity.INFO, Attributes.empty(), null);
                        sendOkHttpRequest();
                        sendURLRequest();
                        newSpan2.end();
                    }
                    newSpan1.end();
                }
                newSpan0.end();
            }
        });
    }

    public void trackViaLdClient() {
        // Records a `track` span automatically via the Observability afterTrack hook.
        ldClient().trackData(
                "track-via-ld-client",
                LDValue.buildObject()
                        .put("test-string", "android")
                        .put("test-true", true)
                        .put("test-false", false)
                        .put("test-integer", 42)
                        .put("test-double", 3.14)
                        .build()
        );
    }

    public void trackViaLdObserve() {
        // Records a `track` span directly through the Observability API.
        LDObserve.Companion.track(
                "track-via-ld-observe",
                LDValue.buildObject()
                        .put("test-string", "android")
                        .put("test-true", true)
                        .put("test-false", false)
                        .put("test-integer", 42)
                        .put("test-double", 3.14)
                        .build(),
                null
        );
    }

    public void triggerCrash() {
        throw new RuntimeException("Failed to connect to bogus server.");
    }

    public void triggerHttpRequests() {
        backgroundExecutor.execute(() -> {
            sendOkHttpRequest();
            sendURLRequest();
        });
    }

    public void identifyLDContext(String contextKey) {
        LDContext context = LDContext.builder(ContextKind.DEFAULT, contextKey)
                .name("test-context-name")
                .build();

        ldClient().identify(context);
    }

    public void identifyUser() {
        LDContext userContext = LDContext.builder(ContextKind.DEFAULT, "single-userkey")
                .name("Bob Bobberson")
                .build();

        ldClient().identify(userContext);
    }

    public void identifyAnonymous() {
        LDContext anonContext = LDContext.builder(ContextKind.DEFAULT, "anonymous-userkey")
                .anonymous(true)
                .build();

        ldClient().identify(anonContext);
    }

    public void identifyMulti() {
        LDContext userContext = LDContext.builder(ContextKind.DEFAULT, "multi-username")
                .name("multi-username")
                .build();
        LDContext deviceContext = LDContext.builder(ContextKind.of("device"), "iphone")
                .name("iphone")
                .build();

        LDContext multiContext = LDContext.createMulti(userContext, deviceContext);
        ldClient().identify(multiContext);
    }

    public void evaluateBooleanFlag(String flagKey) {
        if (flagKey != null && !flagKey.isEmpty()) {
            boolean result = ldClient().boolVariation(flagKey, false);
            Toast.makeText(getApplication(), "Flag " + flagKey + ": " + result, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplication(), "Flag key cannot be empty", Toast.LENGTH_SHORT).show();
        }
    }

    public void startForegroundService() {
        Intent intent = new Intent(getApplication(), ObservabilityForegroundService.class);
        ContextCompat.startForegroundService(getApplication(), intent);
    }

    public void startBackgroundService() {
        Intent intent = new Intent(getApplication(), ObservabilityBackgroundService.class);
        getApplication().startService(intent);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        backgroundExecutor.shutdown();
    }

    private void sendOkHttpRequest() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://www.google.com")
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + (response.body() != null ? response.body().string() : null));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendURLRequest() {
        try {
            URL url = new URL("https://www.android.com/");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }
                System.out.println("URLRequest output: " + output);
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
