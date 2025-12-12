package com.launchdarkly.observability.plugin
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation

/**
 * Plugins can implement this to contribute OpenTelemetry instrumentations that should
 * be installed by the Observability plugin.
 */
interface InstrumentationContributor {
    fun provideInstrumentations(): List<LDExtendedInstrumentation>
}
