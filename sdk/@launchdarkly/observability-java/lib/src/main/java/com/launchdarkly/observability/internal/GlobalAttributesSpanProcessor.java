package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Adds the {@link GlobalAttributes} current at span start to every span the
 * plugin's tracer provider starts, for each key the span was not started with.
 * Registered first, so user processors see them.
 */
public final class GlobalAttributesSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Attributes globals = GlobalAttributes.get();
        if (globals.isEmpty()) {
            return;
        }
        Attributes missing = GlobalAttributes.missingFrom(globals, span.getAttributes());
        if (!missing.isEmpty()) {
            span.setAllAttributes(missing);
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }
}
