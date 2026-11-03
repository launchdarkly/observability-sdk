package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * Adds the {@link GlobalAttributes} to every log record emitted through the
 * plugin's logger provider, for each key the record does not already have.
 * Registered first, so user processors see them.
 */
public final class GlobalAttributesLogProcessor implements LogRecordProcessor {

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        Attributes globals = GlobalAttributes.get();
        if (globals.isEmpty()) {
            return;
        }
        // ReadWriteLogRecord#getAttributes is newer than the 1.38 floor.
        Attributes missing = GlobalAttributes.missingFrom(
                globals, logRecord.toLogRecordData().getAttributes());
        if (!missing.isEmpty()) {
            logRecord.setAllAttributes(missing);
        }
    }
}
