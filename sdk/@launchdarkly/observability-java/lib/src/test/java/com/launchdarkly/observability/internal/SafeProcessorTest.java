package com.launchdarkly.observability.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeProcessorTest {

    @Test
    void throwingSpanProcessorDoesNotStopTheSpanOrLaterProcessors() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(new SafeSpanProcessor(new ThrowingSpanProcessor()))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        assertDoesNotThrow(() -> provider.get("test").spanBuilder("survives").startSpan().end());
        assertEquals("survives", exporter.getFinishedSpanItems().get(0).getName());

        CompletableResultCode flush = assertDoesNotThrow(provider::forceFlush);
        assertFalse(flush.join(5, TimeUnit.SECONDS).isSuccess());
        CompletableResultCode shutdown = assertDoesNotThrow(provider::shutdown);
        assertFalse(shutdown.join(5, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void spanProcessorFlushAndShutdownAreForwarded() {
        CountingSpanProcessor counting = new CountingSpanProcessor();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(new SafeSpanProcessor(counting))
                .build();

        provider.get("test").spanBuilder("counted").startSpan().end();
        assertTrue(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess());
        assertTrue(provider.shutdown().join(5, TimeUnit.SECONDS).isSuccess());

        assertEquals(1, counting.starts.get());
        assertEquals(1, counting.ends.get());
        assertEquals(1, counting.flushes.get());
        assertEquals(1, counting.shutdowns.get());
    }

    @Test
    void throwingLogProcessorDoesNotStopTheRecordOrLaterProcessors() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider provider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new SafeLogRecordProcessor(new ThrowingLogProcessor()))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();

        assertDoesNotThrow(() -> provider.get("test").logRecordBuilder().setBody("survives").emit());
        assertEquals("survives", exporter.getFinishedLogRecordItems().get(0).getBodyValue().asString());

        CompletableResultCode flush = assertDoesNotThrow(provider::forceFlush);
        assertFalse(flush.join(5, TimeUnit.SECONDS).isSuccess());
        CompletableResultCode shutdown = assertDoesNotThrow(provider::shutdown);
        assertFalse(shutdown.join(5, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void logProcessorFlushAndShutdownAreForwarded() {
        AtomicInteger emits = new AtomicInteger();
        AtomicInteger flushes = new AtomicInteger();
        AtomicInteger shutdowns = new AtomicInteger();
        LogRecordProcessor counting = new LogRecordProcessor() {
            @Override
            public void onEmit(Context context, ReadWriteLogRecord logRecord) {
                emits.incrementAndGet();
            }

            @Override
            public CompletableResultCode forceFlush() {
                flushes.incrementAndGet();
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                shutdowns.incrementAndGet();
                return CompletableResultCode.ofSuccess();
            }
        };
        SdkLoggerProvider provider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new SafeLogRecordProcessor(counting))
                .build();

        provider.get("test").logRecordBuilder().setBody("counted").emit();
        assertTrue(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess());
        assertTrue(provider.shutdown().join(5, TimeUnit.SECONDS).isSuccess());

        assertEquals(1, emits.get());
        assertEquals(1, flushes.get());
        assertEquals(1, shutdowns.get());
    }

    static final class ThrowingSpanProcessor implements SpanProcessor {
        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            throw new IllegalStateException("onStart failed");
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            throw new IllegalStateException("onEnd failed");
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        @Override
        public CompletableResultCode forceFlush() {
            throw new IllegalStateException("forceFlush failed");
        }

        @Override
        public CompletableResultCode shutdown() {
            throw new IllegalStateException("shutdown failed");
        }
    }

    static final class ThrowingLogProcessor implements LogRecordProcessor {
        @Override
        public void onEmit(Context context, ReadWriteLogRecord logRecord) {
            throw new IllegalStateException("onEmit failed");
        }

        @Override
        public CompletableResultCode forceFlush() {
            throw new IllegalStateException("forceFlush failed");
        }

        @Override
        public CompletableResultCode shutdown() {
            throw new IllegalStateException("shutdown failed");
        }
    }

    private static final class CountingSpanProcessor implements SpanProcessor {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger ends = new AtomicInteger();
        final AtomicInteger flushes = new AtomicInteger();
        final AtomicInteger shutdowns = new AtomicInteger();

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            starts.incrementAndGet();
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            ends.incrementAndGet();
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        @Override
        public CompletableResultCode forceFlush() {
            flushes.incrementAndGet();
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdowns.incrementAndGet();
            return CompletableResultCode.ofSuccess();
        }
    }
}
