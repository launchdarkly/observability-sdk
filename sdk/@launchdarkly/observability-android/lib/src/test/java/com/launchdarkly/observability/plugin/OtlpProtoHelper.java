package com.launchdarkly.observability.plugin;

import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * Java-side helper that wraps OTel's internal {@link TraceRequestMarshaler} so the Kotlin
 * E2E test can import it cleanly (Kotlin reserves `internal` as a visibility modifier and
 * cannot import a Java package whose path contains an `internal` segment).
 */
final class OtlpProtoHelper {
    private OtlpProtoHelper() {}

    static byte[] marshalSpansToProto(Collection<SpanData> spans) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            TraceRequestMarshaler.create(spans).writeBinaryTo(os);
        } catch (IOException e) {
            throw new RuntimeException("Failed to marshal spans to OTLP protobuf", e);
        }
        return os.toByteArray();
    }
}
