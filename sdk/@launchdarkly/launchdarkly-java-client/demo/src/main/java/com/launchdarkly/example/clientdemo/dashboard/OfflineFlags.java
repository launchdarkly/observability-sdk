package com.launchdarkly.example.clientdemo.dashboard;

import com.launchdarkly.sdk.client.integrations.TestData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the dashboard flags locally when there is no credential, through the
 * SDK's {@link TestData} source. An update goes through the SDK's flag store and
 * fires the same change listeners a streaming update from LaunchDarkly would.
 *
 * <p>{@code TestData} has no prerequisites, so this applies the one the real
 * project has: a component flag is only on while {@link DashboardFlags#DASHBOARD}
 * is on.
 */
final class OfflineFlags {

    private final TestData testData = TestData.dataSource();

    /** What each switch in the offline panel is set to, before the prerequisite. */
    private final Map<String, Boolean> switches = new LinkedHashMap<>();

    OfflineFlags() {
        for (String key : DashboardFlags.ALL) {
            switches.put(key, true);
        }
        apply();
    }

    TestData dataSource() {
        return testData;
    }

    synchronized boolean isSwitchedOn(String key) {
        return switches.getOrDefault(key, false);
    }

    /** Sets one switch and pushes the resulting flag values to the client. */
    synchronized void set(String key, boolean on) {
        switches.put(key, on);
        apply();
    }

    private void apply() {
        boolean dashboard = switches.get(DashboardFlags.DASHBOARD);
        testData.update(testData.flag(DashboardFlags.DASHBOARD).booleanFlag().variation(dashboard));
        for (String key : DashboardFlags.COMPONENTS) {
            boolean on = dashboard && switches.get(key);
            testData.update(testData.flag(key).booleanFlag().variation(on));
        }
    }
}
