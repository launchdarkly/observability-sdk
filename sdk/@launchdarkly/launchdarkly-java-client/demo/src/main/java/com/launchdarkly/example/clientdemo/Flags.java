package com.launchdarkly.example.clientdemo;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.ConnectionInformation;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDFailure;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Formatting shared by the demo and the doctor report. */
final class Flags {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private Flags() {
        throw new AssertionError("No instances");
    }

    /** One line per watched flag: value, variation and reason. */
    static void printWatched(LDClient client, List<String> keys) {
        int width = keys.stream().mapToInt(String::length).max().orElse(0);
        for (String key : keys) {
            System.out.printf("   %-" + width + "s  %s%n", key, describe(client, key));
        }
    }

    /** Every flag the client has for the current context, sorted by key. */
    static void printAll(LDClient client) {
        Map<String, LDValue> all = new TreeMap<>(client.allFlags());
        if (all.isEmpty()) {
            System.out.println("   (no flags)");
            return;
        }
        int width = all.keySet().stream().mapToInt(String::length).max().orElse(0);
        all.forEach((key, value) ->
                System.out.printf("   %-" + width + "s  %s%n", key, value.toJsonString()));
    }

    /** The value, variation index and reason for one flag. */
    static String describe(LDClient client, String key) {
        EvaluationDetail<LDValue> detail = client.jsonValueVariationDetail(key, LDValue.ofNull());
        String variation = detail.getVariationIndex() < 0
                ? "-" : String.valueOf(detail.getVariationIndex());
        return String.format("%-12s variation %-2s %s",
                detail.getValue().toJsonString(), variation, detail.getReason());
    }

    /** A context as kind:key, one per individual context. */
    static String describeContext(LDContext context) {
        if (context == null) {
            return "(none)";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < context.getIndividualContextCount(); i++) {
            LDContext c = context.getIndividualContext(i);
            parts.add(c.getKind() + ":" + c.getKey());
        }
        return String.join(", ", parts);
    }

    /** The connection mode and the last success and failure. */
    static void printConnection(LDClient client) {
        ConnectionInformation info = client.getConnectionInformation();
        System.out.printf(" connection          %s%n", info.getConnectionMode());
        System.out.printf(" last success        %s%n", time(info.getLastSuccessfulConnection()));
        System.out.printf(" last failure        %s%n", time(info.getLastFailedConnection()));
        LDFailure failure = info.getLastFailure();
        if (failure != null) {
            System.out.printf(" failure             %s: %s%n",
                    failure.getFailureType(), failure.getMessage());
        }
    }

    private static String time(Long millis) {
        return millis == null ? "never" : TIME.format(Instant.ofEpochMilli(millis));
    }
}
