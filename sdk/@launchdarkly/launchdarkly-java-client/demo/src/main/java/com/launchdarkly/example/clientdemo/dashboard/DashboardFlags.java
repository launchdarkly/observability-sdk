package com.launchdarkly.example.clientdemo.dashboard;

import java.util.List;

/**
 * The dashboard's flags. All are booleans.
 *
 * <p>{@link #DASHBOARD} is the keystone: in LaunchDarkly it is a prerequisite of
 * the other three, so turning it off turns every component off. The app never
 * needs to check it itself, because prerequisites are evaluated by LaunchDarkly
 * and the client only receives the result.
 */
public final class DashboardFlags {

    public static final String DASHBOARD = "dashboard";
    public static final String PROGRESS_METERS = "dashboard-progress-meters";
    public static final String LINE_CHART = "dashboard-line-chart";
    public static final String BAR_CHART = "dashboard-bar-chart";

    /** The flags that each drive one component, in on-screen order. */
    public static final List<String> COMPONENTS = List.of(PROGRESS_METERS, LINE_CHART, BAR_CHART);

    /** Every flag the dashboard watches, keystone first. */
    public static final List<String> ALL = List.of(DASHBOARD, PROGRESS_METERS, LINE_CHART, BAR_CHART);

    private DashboardFlags() {
        throw new AssertionError("No instances");
    }
}
