package com.launchdarkly.example.clientdemo;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.integrations.TestData;
import com.launchdarkly.sdk.client.subsystems.FlagValueSource;
import java.util.List;

/**
 * Drives the watched flags locally in offline mode, using the SDK's
 * {@link TestData} source.
 *
 * <p>This is not a shortcut around the real code path: a {@code TestData} update
 * goes through the SDK's flag store and fires the same flag change listeners a
 * streaming update from LaunchDarkly would. It just means the demo needs no
 * credentials.
 *
 * <p>Every flag is a boolean, like the dashboard flags it watches by default.
 * Each step counts up in binary across the flags, so the last flag flips every
 * step, the one before it every second step, and so on: a mix of changes rather
 * than all flags flipping at once. The first flag is always off for the context
 * key {@value #TARGETED_KEY}, so {@code i vip} shows targeting at work.
 */
final class FlagCycle {

    /** The context key the first flag targets. */
    static final String TARGETED_KEY = "vip";

    private final TestData testData = TestData.dataSource();
    private final DemoConfig config;
    private int step;

    private FlagCycle(DemoConfig config) {
        this.config = config;
    }

    /** Builds a data source with every watched flag on. */
    static FlagCycle seeded(DemoConfig config) {
        FlagCycle cycle = new FlagCycle(config);
        cycle.apply();
        return cycle;
    }

    TestData dataSource() {
        return testData;
    }

    /** Moves the flags to the next step. */
    void advance() {
        step++;
        apply();
    }

    /** The value the data source serves this context right now, or null if none. */
    LDValue expectedValue(String flagKey, LDContext context) {
        FlagValueSource.Value value = testData.evaluate(flagKey, context);
        return value == null ? null : value.getValue();
    }

    private void apply() {
        List<String> keys = config.flagKeys();
        for (int i = 0; i < keys.size(); i++) {
            // Bit (n - 1 - i) of the step: the last flag is the fastest-changing.
            int bit = keys.size() - 1 - i;
            boolean on = bit >= Integer.SIZE - 1 || ((step >> bit) & 1) == 0;
            TestData.FlagBuilder flag = testData.flag(keys.get(i)).booleanFlag().variation(on);
            if (i == 0) {
                flag.variationForKey(ContextKind.of(config.contextKind()), TARGETED_KEY, false);
            }
            testData.update(flag);
        }
    }
}
