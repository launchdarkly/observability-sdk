package com.example.androidobservability;

import android.app.Application;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.launchdarkly.observability.api.ObservabilityOptions;
import com.launchdarkly.observability.context.LDObserveContext;
import com.launchdarkly.observability.replay.MaskViewRef;
import com.launchdarkly.observability.replay.PrivacyProfile;
import com.launchdarkly.observability.replay.ReplayOptions;
import com.launchdarkly.observability.sdk.LDObserve;
import com.launchdarkly.observability.sdk.LDReplay;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.android.FeatureFlagChangeListener;
import com.launchdarkly.sdk.android.LDClient;
import com.launchdarkly.sdk.android.LDConfig;
import com.launchdarkly.sdk.android.LaunchDarklyException;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

/**
 * Java counterpart of the Kotlin {@code BaseApplication} used by the compose flavor.
 *
 * <p>This class demonstrates how a pure-Java Android app configures the LaunchDarkly
 * Observability and Session Replay SDKs using the Java-friendly fluent builders. Both
 * initialization paths from the Kotlin example are mirrored here:
 * <ul>
 *     <li>{@link #initWithFlagClient()} - attaches Observability and Session Replay to an
 *     initialized LDClient via {@code LDObserve.init}.</li>
 *     <li>{@link #initIndependently()} - initializes via {@code LDObserve.init} without the
 *     feature-flag client.</li>
 * </ul>
 */
public class BaseApplication extends Application {

    /**
     * Which setup {@link #onCreate()} runs: standalone observability ({@link #initIndependently()})
     * or observability attached to an initialized LDClient ({@link #initWithFlagClient()}). Flip it
     * to exercise the app without the flagging SDK, which also hides the screens' LDClient-driven
     * controls.
     */
    public boolean isIndependent = false;

    public static final String LAUNCHDARKLY_MOBILE_KEY = BuildConfig.LAUNCHDARKLY_MOBILE_KEY;

    private ObservabilityOptions observabilityOptions = buildObservabilityOptions(
            BuildConfig.OTLP_ENDPOINT,
            BuildConfig.BACKEND_URL
    );

    @Nullable
    private String testUrl = null;

    /**
     * Builds the observability options used by both init paths. Centralized here (instead of
     * relying on Kotlin's {@code copy(...)}) so the OTLP/backend endpoints can be swapped for the
     * mock-server URL when running under test.
     */
    private ObservabilityOptions buildObservabilityOptions(String otlpEndpoint, String backendUrl) {
        return ObservabilityOptions.builder()
                .resourceAttributes(Attributes.of(
                        AttributeKey.stringKey("resourceAttributes"), "BaseApplication"))
                .debug(true)
                .otlpEndpoint(otlpEndpoint)
                .backendUrl(backendUrl)
                .tracesApi(ObservabilityOptions.TracesApi.enabled())
                .metricsApi(ObservabilityOptions.MetricsApi.enabled())
                .instrumentations(ObservabilityOptions.Instrumentations.builder()
                        .crashReporting(true)
                        .launchTime(true)
                        .build())
                .analytics(ObservabilityOptions.Analytics.builder()
                        .taps(true)
                        .screenViews(true)
                        .trackEvents(true)
                        .appLifecycle(true)
                        .build())
                .build();
    }

    private ReplayOptions buildReplayOptions() {
        return ReplayOptions.builder()
                .enabled(false)
                .privacyProfile(PrivacyProfile.builder()
                        .maskText(false)
                        .maskWebViews(true)
                        .addMaskView(MaskViewRef.ofClass(ImageView.class))
                        .addMaskXMLViewId("smoothieTitle")
                        .build())
                .frameRate(1.0)
                .build();
    }

    private ObservabilityOptions effectiveOptions() {
        if (testUrl != null) {
            return buildObservabilityOptions(testUrl, testUrl);
        }
        return observabilityOptions;
    }

    /** Example of creating OBS/SR with the LaunchDarkly feature-flag SDK. */
    public void initWithFlagClient() {
        LDConfig ldConfig = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
                .build();

        LDContext context = LDContext.builder(ContextKind.DEFAULT, "example-user-key")
                .anonymous(true)
                .build();

        LDClient ldClient = LDClient.init(this, ldConfig, context, 0);

        LDObserve.Companion.init(
                this,
                ldClient,
                observeContext(),
                effectiveOptions(),
                buildReplayOptions(),
                null,
                null
        );

        if (testUrl == null) {
            // intervenes in E2E tests by triggering spans
            flagEvaluation();
        }

        LDReplay.INSTANCE.start();
    }

    /** Example of creating OBS/SR without the feature-flag SDK. */
    public void initIndependently() {
        final ObservabilityOptions options = effectiveOptions();
        final LDObserveContext context = observeContext();

        new Thread(() -> {
            LDObserve.Companion.init(
                    this,
                    LAUNCHDARKLY_MOBILE_KEY,
                    context,
                    options,
                    buildReplayOptions(),
                    null,
                    null
            );

            LDReplay.INSTANCE.start();
        }).start();
    }

    private LDObserveContext observeContext() {
        return LDObserveContext.Companion
                .builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
                .anonymous(true)
                .build();
    }

    public void flagEvaluation() {
        final String flagKey = "feature1";
        try {
            LDClient client = LDClient.get();
            boolean value = client.boolVariation(flagKey, false);
            Log.i("flag", "sync " + flagKey + " value= " + value);
            FeatureFlagChangeListener listener = changedFlagKey -> {
                try {
                    boolean newValue = LDClient.get().boolVariation(flagKey, false);
                    Log.i("flag", "listened " + flagKey + " value= " + newValue);
                } catch (LaunchDarklyException e) {
                    Log.w("flag", "Failed to read flag in listener", e);
                }
            };
            client.registerFeatureFlagListener(flagKey, listener);
        } catch (LaunchDarklyException e) {
            Log.w("flag", "LDClient not initialized; skipping flag evaluation", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isIndependent) {
            initIndependently();
        } else {
            initWithFlagClient();
        }
    }
}
