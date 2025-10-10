package com.launchdarkly.observability.interfaces

import io.opentelemetry.sdk.logs.LogRecordProcessor

interface InstrumentationLoggerProvider {

    fun getLoggerScopeName(): String

    fun getLogRecordProcessor(): LogRecordProcessor

}