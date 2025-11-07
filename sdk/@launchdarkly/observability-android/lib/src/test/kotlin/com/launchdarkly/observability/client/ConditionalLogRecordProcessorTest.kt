package com.launchdarkly.observability.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConditionalLogRecordProcessorTest {

    private lateinit var delegate: LogRecordProcessor
    private val context = Context.root()

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxUnitFun = true)
    }

    @Nested
    @DisplayName("onEmit filtering")
    inner class OnEmitFiltering {

        @Test
        fun `should forward both normal and crash logs when allowed`() {
            val processor = ConditionalLogRecordProcessor(
                delegate = delegate,
                allowNormalLogs = true,
                allowCrashes = true
            )
            val normalLog = createLogRecord("com.launchdarkly.observability")
            val crashLog = createLogRecord("io.opentelemetry.crash")

            processor.onEmit(context, normalLog)
            processor.onEmit(context, crashLog)

            verify(exactly = 1) { delegate.onEmit(context, normalLog) }
            verify(exactly = 1) { delegate.onEmit(context, crashLog) }
        }

        @Test
        fun `should drop crash logs when crashes are not allowed`() {
            val processor = ConditionalLogRecordProcessor(
                delegate = delegate,
                allowNormalLogs = true,
                allowCrashes = false
            )
            val normalLog = createLogRecord("com.launchdarkly.observability")
            val crashLog = createLogRecord("io.opentelemetry.crash")

            processor.onEmit(context, normalLog)
            processor.onEmit(context, crashLog)

            verify(exactly = 1) { delegate.onEmit(context, normalLog) }
            verify(exactly = 0) { delegate.onEmit(context, crashLog) }
        }

        @Test
        fun `should drop normal logs when only crashes are allowed`() {
            val processor = ConditionalLogRecordProcessor(
                delegate = delegate,
                allowNormalLogs = false,
                allowCrashes = true
            )
            val normalLog = createLogRecord("com.launchdarkly.observability")
            val crashLog = createLogRecord("io.opentelemetry.crash")

            processor.onEmit(context, normalLog)
            processor.onEmit(context, crashLog)

            verify(exactly = 0) { delegate.onEmit(context, normalLog) }
            verify(exactly = 1) { delegate.onEmit(context, crashLog) }
        }

        @Test
        fun `should drop all logs when neither category is allowed`() {
            val processor = ConditionalLogRecordProcessor(
                delegate = delegate,
                allowNormalLogs = false,
                allowCrashes = false
            )
            val normalLog = createLogRecord("com.launchdarkly.observability")
            val crashLog = createLogRecord("io.opentelemetry.crash")

            processor.onEmit(context, normalLog)
            processor.onEmit(context, crashLog)

            verify(exactly = 0) { delegate.onEmit(context, any()) }
        }
    }

    @Test
    fun `forceFlush should delegate to underlying processor`() {
        val expectedResult = CompletableResultCode.ofSuccess()
        every { delegate.forceFlush() } returns expectedResult
        val processor = ConditionalLogRecordProcessor(delegate, allowNormalLogs = true, true)

        val result = processor.forceFlush()

        assertSame(expectedResult, result)
        verify(exactly = 1) { delegate.forceFlush() }
    }

    @Test
    fun `shutdown should delegate to underlying processor`() {
        val expectedResult = CompletableResultCode.ofSuccess()
        every { delegate.shutdown() } returns expectedResult
        val processor = ConditionalLogRecordProcessor(delegate, allowNormalLogs = true, allowCrashes = true)

        val result = processor.shutdown()

        assertSame(expectedResult, result)
        verify(exactly = 1) { delegate.shutdown() }
    }

    @Test
    fun `close should delegate to underlying processor`() {
        val processor = ConditionalLogRecordProcessor(delegate, allowNormalLogs = true, allowCrashes = true)

        processor.close()

        verify(exactly = 1) { delegate.close() }
    }

    private fun createLogRecord(scopeName: String): ReadWriteLogRecord {
        return mockk {
            every { instrumentationScopeInfo } returns mockk {
                every { name } returns scopeName
            }
        }
    }
}
