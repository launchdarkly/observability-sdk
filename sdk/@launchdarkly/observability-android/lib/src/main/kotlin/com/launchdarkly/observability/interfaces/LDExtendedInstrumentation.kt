package com.launchdarkly.observability.interfaces

import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.sdk.logs.LogRecordProcessor

// This interface is for internal LaunchDarkly use only.
interface LDExtendedInstrumentation : AndroidInstrumentation {

    /**
     * @return the scope name that this instrumentation will use for its logs
     */
    fun getLoggerScopeName(): String

    /**
     * @param credential the credential that will be used by exporters for authenticating with
     * services
     *
     * @return the instrumentation specific [LogRecordProcessor] for handling this instrumentations
     * logs, or null if this instrumentation does not need to provide any specific handling.
     */
    fun getLogRecordProcessor(credential: String): LogRecordProcessor? = null
}