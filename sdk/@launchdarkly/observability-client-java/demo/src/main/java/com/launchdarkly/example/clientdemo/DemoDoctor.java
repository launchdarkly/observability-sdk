package com.launchdarkly.example.clientdemo;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.LDClient;

/**
 * Prints diagnostics for the setup mistakes that look like SDK bugs.
 *
 * <p>Each flag is read as a raw {@code LDValue} with its evaluation detail, so the
 * report shows the value's JSON type and why the SDK returned it. That separates a
 * flag the client can't see from a flag that is serving an unexpected value.
 */
final class DemoDoctor {

    private DemoDoctor() {
        throw new AssertionError("No instances");
    }

    /** Prints the report. Returns true if every check passed. */
    static boolean report(DemoConfig config, LDClient client) {
        System.out.println("=".repeat(72));
        System.out.println(" LaunchDarkly client-side Java SDK - diagnostics");
        System.out.println("=".repeat(72));
        System.out.printf(" SDK version         %s%n", client.getVersion());
        System.out.printf(" mode                %s%n",
                config.isLive() ? "live" : "offline (TestData)");
        System.out.printf(" credential          %s%n", config.credentialDescription());
        System.out.printf(" client initialized  %s%n", client.isInitialized());
        System.out.printf(" context             %s%n", Flags.describeContext(client.getContext()));
        Flags.printConnection(client);
        System.out.println("-".repeat(72));

        boolean healthy = client.isInitialized();
        if (!healthy) {
            System.out.println(" ! The client has no flag values yet. Check the credential, and");
            System.out.println("   that this machine can reach LaunchDarkly.");
        }
        for (String key : config.flagKeys()) {
            healthy &= reportFlag(config, client, key);
        }

        System.out.println("-".repeat(72));
        System.out.println(healthy
                ? " All checks passed."
                : " Some checks failed - see the notes above.");
        return healthy;
    }

    private static boolean reportFlag(DemoConfig config, LDClient client, String key) {
        EvaluationDetail<LDValue> detail = client.jsonValueVariationDetail(key, LDValue.ofNull());
        LDValue value = detail.getValue();
        EvaluationReason reason = detail.getReason();

        System.out.printf("%n %s%n", key);
        System.out.printf("   value             %s (%s)%n", value.toJsonString(), value.getType());
        System.out.printf("   variation         %s%n",
                detail.getVariationIndex() < 0 ? "none" : detail.getVariationIndex());
        System.out.printf("   reason            %s%n", reason);

        if (reason != null && reason.getKind() == EvaluationReason.Kind.ERROR) {
            if (reason.getErrorKind() == EvaluationReason.ErrorKind.FLAG_NOT_FOUND) {
                System.out.println("   status            NOT FOUND - the key is wrong, or the flag is in");
                System.out.println("                     another project or environment.");
                if (config.clientSideId().isPresent()) {
                    System.out.println("                     With a client-side ID, the flag also needs");
                    System.out.println("                     'SDKs using Client-side ID' turned on in its");
                    System.out.println("                     settings.");
                }
            } else {
                System.out.printf("   status            ERROR - %s%n", reason.getErrorKind());
            }
            return false;
        }
        System.out.println("   status            OK");
        return true;
    }
}
